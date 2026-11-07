package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

class AttachmentStorageManager(private val context: Context) {

    val attachmentsDir: File
        get() = File(context.filesDir, "vault_attachments").apply {
            if (!exists()) mkdirs()
        }

    val generationsRootDir: File
        get() = File(context.filesDir, "vault_attachment_generations").apply {
            if (!exists()) mkdirs()
        }

    val activeGenerationFile: File
        get() = File(context.filesDir, "vault_active_generation.json")

    val stagingRootDir: File
        get() = File(context.cacheDir, "vault_backup_staging").apply {
            if (!exists()) mkdirs()
        }

    val rollbackRootDir: File
        get() = File(context.filesDir, "vault_attachments_rollback").apply {
            if (!exists()) mkdirs()
        }

    fun getGenerationDir(generationId: String): File {
        val cleanId = generationId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(generationsRootDir, "gen_$cleanId")
    }

    fun readActiveGenerationId(): String {
        val file = activeGenerationFile
        if (!file.exists() || !file.isFile) return "gen_initial"
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            json.optString("generationId", "gen_initial").ifBlank { "gen_initial" }
        } catch (_: Exception) {
            "gen_initial"
        }
    }

    fun writeActiveGeneration(generationId: String, fileChecksums: Map<String, String>) {
        val root = JSONObject().apply {
            put("generationId", generationId)
            put("updatedAt", System.currentTimeMillis())
            val filesObj = JSONObject()
            fileChecksums.forEach { (name, sha) ->
                filesObj.put(name, sha)
            }
            put("files", filesObj)
        }
        atomicWriteText(activeGenerationFile, root.toString(2))
    }

    /**
     * Atomically writes text content to [targetFile] via a sibling `.tmp` file,
     * flushes the file descriptor with `fd.sync()`, and atomically renames to [targetFile].
     */
    fun atomicWriteText(targetFile: File, content: String) {
        val parent = targetFile.parentFile ?: throw IOException("Target file has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Unable to create parent directory for ${targetFile.name}")
        }
        val tempFile = File(parent, "${targetFile.name}.tmp_${UUID.randomUUID()}")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            if (!tempFile.renameTo(targetFile)) {
                FileOutputStream(targetFile).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                    fos.flush()
                    fos.fd.sync()
                }
                tempFile.delete()
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Copies a Storage Access Framework (SAF) or picker URI into persistent app-private storage
     * so the attachment never breaks after app restart or picker URI expiration.
     */
    suspend fun copyUriToPersistentStorage(
        sourceUri: Uri,
        explicitType: AttachmentType? = null
    ): AttachmentItem? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            try {
                resolver.takePersistableUriPermission(
                    sourceUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Not all URIs support persistable permissions; copying bytes guarantees persistence.
            }

            var displayName = "attachment_${System.currentTimeMillis()}"
            var fileSize = 0L

            resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            val mimeType = resolver.getType(sourceUri) ?: guessMimeType(displayName)
            val resolvedType = explicitType ?: when {
                mimeType.startsWith("image/", ignoreCase = true) -> AttachmentType.IMAGE
                mimeType.equals("application/pdf", ignoreCase = true) ||
                    displayName.endsWith(".pdf", ignoreCase = true) -> AttachmentType.PDF
                else -> AttachmentType.FILE
            }

            val sanitizedBase = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            val safeFileName = "${UUID.randomUUID()}_$sanitizedBase"
            val destFile = File(attachmentsDir, safeFileName)

            val canonicalDest = destFile.canonicalPath
            val canonicalRoot = attachmentsDir.canonicalPath + File.separator
            if (!canonicalDest.startsWith(canonicalRoot)) {
                return@withContext null
            }

            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            } ?: return@withContext null

            if (fileSize <= 0L) {
                fileSize = destFile.length()
            }

            AttachmentItem(
                id = UUID.randomUUID().toString(),
                type = resolvedType,
                name = displayName,
                uriOrUrl = destFile.absolutePath,
                mimeType = mimeType,
                sizeBytes = fileSize,
                addedAt = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun createLinkAttachment(title: String, url: String): AttachmentItem {
        val normalizedUrl = url.trim().let {
            if (!it.startsWith("http://", ignoreCase = true) && !it.startsWith("https://", ignoreCase = true)) {
                "https://$it"
            } else {
                it
            }
        }
        val cleanName = title.trim().ifEmpty { normalizedUrl }
        return AttachmentItem(
            id = UUID.randomUUID().toString(),
            type = AttachmentType.LINK,
            name = cleanName,
            uriOrUrl = normalizedUrl,
            mimeType = "text/uri-list",
            sizeBytes = 0L,
            addedAt = System.currentTimeMillis()
        )
    }

    /**
     * Deletes a local attachment file if no active, archived, or trashed item or method step still references it.
     */
    fun deleteLocalFileIfUnreferenced(
        filePath: String,
        allItems: List<VaultItemEntity>,
        allSteps: List<MethodStepEntity> = emptyList()
    ) {
        if (filePath.isBlank() || !filePath.startsWith("/")) return
        val targetFile = File(filePath)
        if (!targetFile.exists() || !targetFile.isFile) return

        val canonicalTarget = try {
            targetFile.canonicalPath
        } catch (_: IOException) {
            return
        }
        val canonicalRoot = try {
            attachmentsDir.canonicalPath + File.separator
        } catch (_: IOException) {
            return
        }
        if (!canonicalTarget.startsWith(canonicalRoot)) return

        val referencedByItems = allItems.any { item ->
            val atts = VaultJsonSerializers.decodeAttachments(item.attachmentsJson)
            atts.any { it.uriOrUrl == filePath || File(it.uriOrUrl).name == targetFile.name }
        }
        val referencedBySteps = allSteps.any { step ->
            val images = VaultJsonSerializers.decodeStringList(step.imagesJson)
            images.any { it == filePath || File(it).name == targetFile.name }
        }
        if (!referencedByItems && !referencedBySteps) {
            targetFile.delete()
        }
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun guessMimeType(fileName: String): String {
        return guessMimeTypeForName(fileName)
    }

    fun openAttachmentExternally(attachment: AttachmentItem): Boolean {
        return Companion.openAttachmentExternally(context, attachment)
    }

    companion object {
        fun guessMimeTypeForName(fileName: String): String {
            val lower = fileName.lowercase(Locale.US)
            return when {
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".webp") -> "image/webp"
                lower.endsWith(".pdf") -> "application/pdf"
                lower.endsWith(".txt") || lower.endsWith(".md") -> "text/plain"
                lower.endsWith(".json") -> "application/json"
                else -> "application/octet-stream"
            }
        }

        /**
         * Verifies that [file] exists, is a regular file, and resolves canonically inside one of
         * the application-owned attachment directories (`vault_attachments` or
         * `vault_attachment_generations`). Rejects path traversal (`..`) and unrelated private files.
         */
        fun isOwnedAttachmentFile(context: Context, file: File): Boolean {
            if (!file.exists() || !file.isFile) return false
            return try {
                val canonicalTarget = file.canonicalPath
                val primaryRoot = File(context.filesDir, "vault_attachments").canonicalPath + File.separator
                val generationsRoot = File(context.filesDir, "vault_attachment_generations").canonicalPath + File.separator
                canonicalTarget.startsWith(primaryRoot) || canonicalTarget.startsWith(generationsRoot)
            } catch (_: IOException) {
                false
            }
        }

        /**
         * Resolves a secure `content://` URI via [FileProvider] for an application-owned attachment
         * file, or returns `null` if the file does not exist or lies outside owned attachment directories.
         */
        fun getContentUriForOwnedAttachment(context: Context, file: File): Uri? {
            if (!isOwnedAttachmentFile(context, file)) return null
            val authority = "${context.packageName}.fileprovider"
            val canonical = try {
                file.canonicalFile
            } catch (_: IOException) {
                return null
            }
            return try {
                FileProvider.getUriForFile(context, authority, canonical)
            } catch (_: Throwable) {
                null
            }
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return ""
            return when {
                bytes < 1024L -> "$bytes B"
                bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }

        fun isDuplicateAttachment(
            existingList: List<AttachmentItem>,
            candidate: AttachmentItem
        ): Boolean {
            return existingList.any { existing ->
                val sameUri = existing.uriOrUrl.isNotBlank() &&
                    existing.uriOrUrl.equals(candidate.uriOrUrl, ignoreCase = true)
                val sameFileMeta = existing.type == candidate.type &&
                    existing.name.equals(candidate.name, ignoreCase = true) &&
                    existing.sizeBytes > 0L &&
                    existing.sizeBytes == candidate.sizeBytes &&
                    existing.mimeType.equals(candidate.mimeType, ignoreCase = true)
                sameUri || sameFileMeta
            }
        }

        fun deduplicateAttachments(list: List<AttachmentItem>): List<AttachmentItem> {
            val result = mutableListOf<AttachmentItem>()
            for (item in list) {
                if (!isDuplicateAttachment(result, item)) {
                    result.add(item)
                }
            }
            return result
        }

        fun openAttachmentExternally(
            context: Context,
            attachment: AttachmentItem
        ): Boolean {
            return try {
                if (attachment.type == AttachmentType.LINK ||
                    attachment.uriOrUrl.startsWith("http://", true) ||
                    attachment.uriOrUrl.startsWith("https://", true)
                ) {
                    val url = attachment.uriOrUrl.trim().let {
                        if (!it.startsWith("http://", true) && !it.startsWith("https://", true)) {
                            "https://$it"
                        } else it
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                } else {
                    val rawPath = attachment.uriOrUrl.trim()
                    if (rawPath.isBlank()) return false
                    val file = File(rawPath)
                    val uri = getContentUriForOwnedAttachment(context, file) ?: return false
                    val resolvedMime = attachment.mimeType.ifBlank {
                        guessMimeTypeForName(attachment.name.ifBlank { file.name })
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, resolvedMime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
            } catch (_: Throwable) {
                false
            }
        }
    }
}
