package com.example.data

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ui.theme.VaultCoral
import com.example.ui.theme.VaultDarkInk
import com.example.ui.theme.VaultLavender
import com.example.ui.theme.VaultLime
import com.example.ui.theme.VaultPink
import com.example.ui.theme.VaultPrimaryText
import com.example.ui.theme.VaultReferenceGray
import com.example.ui.theme.VaultYellow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class VaultCategory(
    val id: String,
    val label: String,
    val singularLabel: String,
    val cardColor: Color,
    val contentColor: Color,
    val creationSubtitle: String = ""
) {
    IDEA(
        id = "IDEA",
        label = "Ideas",
        singularLabel = "Idea",
        cardColor = VaultCoral,
        contentColor = VaultDarkInk,
        creationSubtitle = "Save something you're thinking about"
    ),
    METHOD(
        id = "METHOD",
        label = "Methods",
        singularLabel = "Method",
        cardColor = VaultLime,
        contentColor = VaultDarkInk,
        creationSubtitle = "Save how to do something step by step"
    ),
    PROMPT(
        id = "PROMPT",
        label = "Prompts",
        singularLabel = "Prompt",
        cardColor = VaultYellow,
        contentColor = VaultDarkInk,
        creationSubtitle = "Save an AI prompt for reuse"
    ),
    KNOWLEDGE(
        id = "KNOWLEDGE",
        label = "Notes",
        singularLabel = "Note",
        cardColor = VaultLavender,
        contentColor = VaultDarkInk,
        creationSubtitle = "Save useful information"
    ),
    REFERENCE(
        id = "REFERENCE",
        label = "Notes",
        singularLabel = "Note",
        cardColor = VaultLavender,
        contentColor = VaultDarkInk,
        creationSubtitle = "Save useful information"
    ),
    INBOX(
        id = "INBOX",
        label = "Inbox",
        singularLabel = "Note",
        cardColor = VaultPink,
        contentColor = VaultDarkInk,
        creationSubtitle = "Save useful information"
    );

    companion object {
        val v2PrimaryCategories: List<VaultCategory> = listOf(
            IDEA,
            METHOD,
            PROMPT,
            KNOWLEDGE
        )

        val mainCategories: List<VaultCategory> = listOf(
            IDEA,
            METHOD,
            PROMPT,
            KNOWLEDGE,
            REFERENCE
        )

        fun fromId(id: String): VaultCategory =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: IDEA
    }
}

enum class IdeaStatus(val id: String, val label: String, val badgeColor: Color) {
    NEW("NEW", "New", VaultYellow),
    EXPLORING("EXPLORING", "Exploring", VaultLavender),
    DEVELOPING("DEVELOPING", "Developing", VaultCoral),
    COMPLETED("COMPLETED", "Completed", VaultLime);

    companion object {
        fun fromId(id: String): IdeaStatus =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NEW
    }
}

enum class RichBlockType(val id: String, val label: String) {
    PARAGRAPH("PARAGRAPH", "Note Block"),
    HEADING("HEADING", "Heading"),
    BULLET_LIST("BULLET_LIST", "Bullet List"),
    NUMBERED_LIST("NUMBERED_LIST", "Numbered List"),
    CHECKLIST("CHECKLIST", "Checklist Item"),
    CODE_BLOCK("CODE_BLOCK", "Code Block"),
    QUOTE("QUOTE", "Quote"),
    DIVIDER("DIVIDER", "Divider");

    companion object {
        fun fromId(id: String): RichBlockType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PARAGRAPH
    }
}

data class RichContentBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: RichBlockType = RichBlockType.PARAGRAPH,
    val title: String = "",
    val content: String = "",
    val checked: Boolean = false
)

enum class AttachmentType(val id: String, val label: String) {
    IMAGE("IMAGE", "Image"),
    PDF("PDF", "PDF Document"),
    FILE("FILE", "File"),
    LINK("LINK", "Website Link");

    companion object {
        fun fromId(id: String): AttachmentType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FILE
    }
}

data class AttachmentItem(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType = AttachmentType.LINK,
    val name: String = "",
    val uriOrUrl: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["category"]),
        Index(value = ["updatedAt"])
    ]
)
data class FolderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = VaultCategory.IDEA.id,
    val colorHex: String = "#FF7654",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "custom_types",
    indices = [
        Index(value = ["name"]),
        Index(value = ["updatedAt"])
    ]
)
data class CustomTypeEntity(
    @PrimaryKey val id: String = "ctype_${UUID.randomUUID()}",
    val name: String,
    val iconName: String = "Extension",
    val colorHex: String = "#5E35B1",
    val description: String = "",
    val fieldsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vault_items",
    indices = [
        Index(value = ["category"]),
        Index(value = ["folderId"]),
        Index(value = ["isFavorite"]),
        Index(value = ["isPinned"]),
        Index(value = ["isTrashed"]),
        Index(value = ["isArchived"]),
        Index(value = ["updatedAt"]),
        Index(value = ["createdAt"]),
        Index(value = ["customTypeId"])
    ]
)
data class VaultItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val content: String = "",
    val category: String = VaultCategory.IDEA.id,
    val folderId: String? = null,
    val tagsJson: String = "[]",
    val richBlocksJson: String = "[]",
    val attachmentsJson: String = "[]",
    val ideaStatus: String = IdeaStatus.NEW.id,
    val platform: String = "",
    val estimatedTime: String = "",
    val notes: String = "",
    val customTypeId: String? = null,
    val customFieldsJson: String = "{}",
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

const val MAIN_METHOD_ROUTE_ID = "main"
const val DEFAULT_MAIN_METHOD_NAME = "Main Method"

@Entity(
    tableName = "method_routes",
    indices = [Index(value = ["itemId", "routeOrder"])]
)
data class MethodRouteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val name: String,
    val description: String = "",
    val routeOrder: Int = 0,
    val isMain: Boolean = false
)

@Entity(
    tableName = "method_steps",
    indices = [
        Index(value = ["itemId", "stepOrder"]),
        Index(value = ["itemId", "routeId", "stepOrder"])
    ]
)
data class MethodStepEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val stepOrder: Int,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val linksJson: String = "[]",
    val imagesJson: String = "[]",
    val routeId: String = MAIN_METHOD_ROUTE_ID
)

@Entity(tableName = "method_follow_runs")
data class MethodFollowRunEntity(
    @PrimaryKey val itemId: String,
    val completedStepIdsJson: String = "[]",
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "method_route_follow_runs",
    primaryKeys = ["itemId", "routeId"]
)
data class MethodRouteFollowRunEntity(
    val itemId: String,
    val routeId: String = MAIN_METHOD_ROUTE_ID,
    val completedStepIdsJson: String = "[]",
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class MethodRouteWithSteps(
    val route: MethodRouteEntity,
    val steps: List<MethodStepEntity> = emptyList(),
    val completedStepIds: Set<String> = emptySet()
) {
    val id: String get() = route.id
    val itemId: String get() = route.itemId
    val name: String get() = route.name
    val description: String get() = route.description
    val routeOrder: Int get() = route.routeOrder
    val isMain: Boolean get() = route.isMain

    constructor(
        id: String = UUID.randomUUID().toString(),
        itemId: String = "",
        name: String,
        description: String = "",
        routeOrder: Int = 0,
        isMain: Boolean = false,
        steps: List<MethodStepEntity> = emptyList(),
        completedStepIds: Set<String> = emptySet()
    ) : this(
        route = MethodRouteEntity(
            id = id,
            itemId = itemId,
            name = name,
            description = description,
            routeOrder = routeOrder,
            isMain = isMain
        ),
        steps = steps,
        completedStepIds = completedStepIds
    )

    fun copy(
        id: String = this.route.id,
        itemId: String = this.route.itemId,
        name: String = this.route.name,
        description: String = this.route.description,
        routeOrder: Int = this.route.routeOrder,
        isMain: Boolean = this.route.isMain,
        steps: List<MethodStepEntity> = this.steps,
        completedStepIds: Set<String> = this.completedStepIds
    ): MethodRouteWithSteps = MethodRouteWithSteps(
        route = route.copy(
            id = id,
            itemId = itemId,
            name = name,
            description = description,
            routeOrder = routeOrder,
            isMain = isMain
        ),
        steps = steps,
        completedStepIds = completedStepIds
    )
}

data class CategoryCountRow(
    val category: String,
    val count: Int
)

data class FolderCountRow(
    val folderId: String?,
    val count: Int
)

data class VaultItemWithDetails(
    val entity: VaultItemEntity,
    val tags: List<String>,
    val richBlocks: List<RichContentBlock>,
    val attachments: List<AttachmentItem>,
    val steps: List<MethodStepEntity> = emptyList(),
    val completedFollowStepIds: Set<String> = emptySet(),
    val folderName: String? = null,
    val methodRoutes: List<MethodRouteWithSteps> = emptyList(),
    val customType: CustomTypeEntity? = null,
    val customFieldsMap: Map<String, String> = VaultJsonSerializers.decodeCustomFieldsMap(entity.customFieldsJson)
) {
    val categoryEnum: VaultCategory
        get() = VaultCategory.fromId(entity.category)

    val statusEnum: IdeaStatus
        get() = IdeaStatus.fromId(entity.ideaStatus)

    val effectiveMethodRoutes: List<MethodRouteWithSteps>
        get() {
            if (methodRoutes.isNotEmpty()) return methodRoutes
            return listOf(
                MethodRouteWithSteps(
                    route = MethodRouteEntity(
                        id = MAIN_METHOD_ROUTE_ID,
                        itemId = entity.id,
                        name = DEFAULT_MAIN_METHOD_NAME,
                        description = "",
                        routeOrder = 0,
                        isMain = true
                    ),
                    steps = steps,
                    completedStepIds = completedFollowStepIds
                )
            )
        }

    val alternativeMethodRoutes: List<MethodRouteWithSteps>
        get() = effectiveMethodRoutes.filter { !it.route.isMain && it.route.id != MAIN_METHOD_ROUTE_ID }

    val hasAlternativeMethods: Boolean
        get() = alternativeMethodRoutes.isNotEmpty()

    val wordCount: Int
        get() {
            val customValuesText = customFieldsMap.values.joinToString(" ")
            val combined = buildString {
                append(entity.title).append(" ")
                append(entity.description).append(" ")
                append(entity.content).append(" ")
                append(entity.notes).append(" ")
                if (customValuesText.isNotBlank()) {
                    append(customValuesText).append(" ")
                }
                richBlocks.forEach {
                    append(it.title).append(" ").append(it.content).append(" ")
                }
                if (methodRoutes.isNotEmpty()) {
                    methodRoutes.forEach { routeWithSteps ->
                        if (!routeWithSteps.route.isMain && routeWithSteps.route.id != MAIN_METHOD_ROUTE_ID) {
                            append(routeWithSteps.route.name).append(" ")
                            append(routeWithSteps.route.description).append(" ")
                        }
                        routeWithSteps.steps.forEach {
                            append(it.title).append(" ").append(it.description).append(" ").append(it.notes).append(" ")
                        }
                    }
                } else {
                    steps.forEach {
                        append(it.title).append(" ").append(it.description).append(" ")
                    }
                }
            }.trim()
            if (combined.isBlank()) return 0
            return combined.split(Regex("\\s+")).count { it.isNotBlank() }
        }

    val noteBlockCount: Int
        get() {
            val blockCount = richBlocks.count { it.type != RichBlockType.DIVIDER }
            return if (blockCount > 0) blockCount else if (entity.content.isNotBlank()) 1 else 0
        }
}

object VaultJsonSerializers {
    fun encodeStringList(list: List<String>): String {
        val arr = JSONArray()
        list.filter { it.isNotBlank() }.forEach { arr.put(it.trim()) }
        return arr.toString()
    }

    fun decodeStringList(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeRichBlocks(blocks: List<RichContentBlock>): String {
        val arr = JSONArray()
        blocks.forEach { block ->
            val obj = JSONObject()
            obj.put("id", block.id)
            obj.put("type", block.type.id)
            obj.put("title", block.title)
            obj.put("content", block.content)
            obj.put("checked", block.checked)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeRichBlocks(json: String): List<RichContentBlock> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        RichContentBlock(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            type = RichBlockType.fromId(obj.optString("type", RichBlockType.PARAGRAPH.id)),
                            title = obj.optString("title", ""),
                            content = obj.optString("content", ""),
                            checked = obj.optBoolean("checked", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeAttachments(items: List<AttachmentItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("type", item.type.id)
            obj.put("name", item.name)
            obj.put("uriOrUrl", item.uriOrUrl)
            obj.put("mimeType", item.mimeType)
            obj.put("sizeBytes", item.sizeBytes)
            obj.put("addedAt", item.addedAt)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeAttachments(json: String): List<AttachmentItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        AttachmentItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            type = AttachmentType.fromId(obj.optString("type", AttachmentType.LINK.id)),
                            name = obj.optString("name", ""),
                            uriOrUrl = obj.optString("uriOrUrl", ""),
                            mimeType = obj.optString("mimeType", ""),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            addedAt = obj.optLong("addedAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeMethodSteps(steps: List<MethodStepEntity>): String {
        val arr = JSONArray()
        steps.forEachIndexed { index, step ->
            val obj = JSONObject()
            obj.put("id", step.id)
            obj.put("itemId", step.itemId)
            obj.put("stepOrder", index + 1)
            obj.put("title", step.title)
            obj.put("description", step.description)
            obj.put("notes", step.notes)
            obj.put("linksJson", step.linksJson)
            obj.put("imagesJson", step.imagesJson)
            obj.put("routeId", step.routeId.ifBlank { MAIN_METHOD_ROUTE_ID })
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeMethodSteps(
        json: String,
        fallbackItemId: String = "",
        fallbackRouteId: String = MAIN_METHOD_ROUTE_ID
    ): List<MethodStepEntity> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        MethodStepEntity(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            itemId = obj.optString("itemId", fallbackItemId),
                            stepOrder = obj.optInt("stepOrder", i + 1),
                            title = obj.optString("title", ""),
                            description = obj.optString("description", ""),
                            notes = obj.optString("notes", ""),
                            linksJson = obj.optString("linksJson", "[]"),
                            imagesJson = obj.optString("imagesJson", "[]"),
                            routeId = obj.optString("routeId", fallbackRouteId).ifBlank { fallbackRouteId }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeMethodRoutesWithSteps(routes: List<MethodRouteWithSteps>): String {
        val arr = JSONArray()
        routes.forEachIndexed { index, routeWithSteps ->
            val route = routeWithSteps.route
            val obj = JSONObject()
            obj.put("id", route.id)
            obj.put("itemId", route.itemId)
            obj.put("name", route.name)
            obj.put("description", route.description)
            obj.put("routeOrder", index + 1)
            obj.put("isMain", route.isMain)
            obj.put("stepsJson", encodeMethodSteps(routeWithSteps.steps.map { it.copy(routeId = route.id) }))
            obj.put("completedStepIdsJson", encodeStringList(routeWithSteps.completedStepIds.toList()))
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeMethodRoutesWithSteps(json: String, fallbackItemId: String = ""): List<MethodRouteWithSteps> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val routeId = obj.optString("id", UUID.randomUUID().toString()).ifBlank { UUID.randomUUID().toString() }
                    val itemId = obj.optString("itemId", fallbackItemId).ifBlank { fallbackItemId }
                    val steps = decodeMethodSteps(
                        json = obj.optString("stepsJson", "[]"),
                        fallbackItemId = itemId,
                        fallbackRouteId = routeId
                    )
                    val rawName = obj.optString("name", "").trim()
                    if (rawName.isEmpty() && steps.isEmpty()) continue
                    val name = rawName.ifEmpty { "Method ${i + 2}" }
                    val route = MethodRouteEntity(
                        id = routeId,
                        itemId = itemId,
                        name = name,
                        description = obj.optString("description", ""),
                        routeOrder = obj.optInt("routeOrder", i + 1),
                        isMain = obj.optBoolean("isMain", false)
                    )
                    val completedIds = decodeStringList(obj.optString("completedStepIdsJson", "[]")).toSet()
                    add(
                        MethodRouteWithSteps(
                            route = route,
                            steps = steps,
                            completedStepIds = completedIds
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeEditorDraft(draft: EditorDraftPayload): String {
        val obj = JSONObject()
        obj.put("draftVersion", 4)
        obj.put("itemId", draft.itemId)
        obj.put("isEditingExisting", draft.isEditingExisting)
        if (draft.baseUpdatedAt != null) {
            obj.put("baseUpdatedAt", draft.baseUpdatedAt)
        } else {
            obj.put("baseUpdatedAt", JSONObject.NULL)
        }
        obj.put("category", draft.categoryId)
        if (draft.folderId != null) {
            obj.put("folderId", draft.folderId)
        } else {
            obj.put("folderId", JSONObject.NULL)
        }
        obj.put("ideaStatus", draft.ideaStatusId)
        obj.put("title", draft.title)
        obj.put("description", draft.description)
        obj.put("content", draft.content)
        obj.put("platform", draft.platform)
        obj.put("estimatedTime", draft.estimatedTime)
        obj.put("notes", draft.notes)
        obj.put("isPinned", draft.isPinned)
        obj.put("isFavorite", draft.isFavorite)
        obj.put("tagsJson", encodeStringList(draft.tags))
        obj.put("richBlocksJson", encodeRichBlocks(draft.richBlocks))
        obj.put("attachmentsJson", encodeAttachments(draft.attachments))
        obj.put("stepsJson", encodeMethodSteps(draft.steps))
        obj.put("mainRouteName", draft.mainRouteName)
        obj.put("mainRouteDescription", draft.mainRouteDescription)
        obj.put("alternativeRoutesJson", encodeMethodRoutesWithSteps(draft.alternativeRoutes))
        obj.put("activeRouteId", draft.activeRouteId)
        obj.put("pendingStepTitle", draft.pendingStepTitle)
        obj.put("pendingStepDetails", draft.pendingStepDetails)
        obj.put("pendingNewTag", draft.pendingNewTag)
        if (draft.customTypeId != null) {
            obj.put("customTypeId", draft.customTypeId)
        } else {
            obj.put("customTypeId", JSONObject.NULL)
        }
        obj.put("customFieldsJson", draft.customFieldsJson)
        if (draft.pendingCustomType != null) {
            obj.put("pendingCustomTypeJson", encodeCustomTypeEntity(draft.pendingCustomType))
        } else {
            obj.put("pendingCustomTypeJson", JSONObject.NULL)
        }
        obj.put("savedAt", draft.savedAt)
        return obj.toString()
    }

    fun decodeEditorDraft(json: String): EditorDraftPayload? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            val itemId = obj.optString("itemId", "").ifBlank { UUID.randomUUID().toString() }
            val isEditingExisting = obj.optBoolean("isEditingExisting", false)
            val baseUpdatedAt = if (obj.has("baseUpdatedAt") && !obj.isNull("baseUpdatedAt")) {
                obj.optLong("baseUpdatedAt")
            } else {
                null
            }
            val folderId = if (obj.has("folderId") && !obj.isNull("folderId")) {
                obj.optString("folderId", "").ifBlank { null }
            } else {
                null
            }
            val customTypeId = if (obj.has("customTypeId") && !obj.isNull("customTypeId")) {
                obj.optString("customTypeId", "").ifBlank { null }
            } else {
                null
            }
            val pendingCustomType = if (obj.has("pendingCustomTypeJson") && !obj.isNull("pendingCustomTypeJson")) {
                decodeCustomTypeEntity(obj.optString("pendingCustomTypeJson", ""))
            } else {
                null
            }
            val tags = decodeStringList(obj.optString("tagsJson", "[]"))
            val richBlocks = decodeRichBlocks(obj.optString("richBlocksJson", "[]"))
            val attachments = decodeAttachments(obj.optString("attachmentsJson", "[]"))
            val steps = decodeMethodSteps(obj.optString("stepsJson", "[]"), itemId, MAIN_METHOD_ROUTE_ID)
            val mainRouteName = obj.optString("mainRouteName", DEFAULT_MAIN_METHOD_NAME)
                .ifBlank { DEFAULT_MAIN_METHOD_NAME }
            val mainRouteDescription = obj.optString("mainRouteDescription", "")
            val alternativeRoutes = decodeMethodRoutesWithSteps(
                obj.optString("alternativeRoutesJson", "[]"),
                itemId
            )
            val activeRouteId = obj.optString("activeRouteId", MAIN_METHOD_ROUTE_ID)
                .ifBlank { MAIN_METHOD_ROUTE_ID }
            EditorDraftPayload(
                itemId = itemId,
                isEditingExisting = isEditingExisting,
                baseUpdatedAt = baseUpdatedAt,
                categoryId = obj.optString("category", VaultCategory.IDEA.id),
                folderId = folderId,
                ideaStatusId = obj.optString("ideaStatus", IdeaStatus.NEW.id),
                title = obj.optString("title", ""),
                description = obj.optString("description", ""),
                content = obj.optString("content", ""),
                platform = obj.optString("platform", ""),
                estimatedTime = obj.optString("estimatedTime", ""),
                notes = obj.optString("notes", ""),
                isPinned = obj.optBoolean("isPinned", false),
                isFavorite = obj.optBoolean("isFavorite", false),
                tags = tags,
                richBlocks = richBlocks,
                attachments = attachments,
                steps = steps,
                mainRouteName = mainRouteName,
                mainRouteDescription = mainRouteDescription,
                alternativeRoutes = alternativeRoutes,
                activeRouteId = activeRouteId,
                pendingStepTitle = obj.optString("pendingStepTitle", ""),
                pendingStepDetails = obj.optString("pendingStepDetails", ""),
                pendingNewTag = obj.optString("pendingNewTag", ""),
                customTypeId = customTypeId,
                customFieldsJson = obj.optString("customFieldsJson", "{}"),
                pendingCustomType = pendingCustomType,
                savedAt = obj.optLong("savedAt", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun encodeCustomTypeEntity(entity: CustomTypeEntity): String {
        val obj = JSONObject()
        obj.put("id", entity.id)
        obj.put("name", entity.name)
        obj.put("iconName", entity.iconName)
        obj.put("colorHex", entity.colorHex)
        obj.put("description", entity.description)
        obj.put("fieldsJson", entity.fieldsJson)
        obj.put("createdAt", entity.createdAt)
        obj.put("updatedAt", entity.updatedAt)
        return obj.toString()
    }

    fun decodeCustomTypeEntity(json: String): CustomTypeEntity? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) return null
            CustomTypeEntity(
                id = id,
                name = name,
                iconName = obj.optString("iconName", "star"),
                colorHex = obj.optString("colorHex", "#3B82F6"),
                description = obj.optString("description", ""),
                fieldsJson = obj.optString("fieldsJson", "[]"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun encodeCustomFieldDefinitions(fields: List<CustomFieldDefinition>): String {
        val arr = JSONArray()
        fields.sortedBy { it.orderIndex }.forEachIndexed { idx, field ->
            val obj = JSONObject()
            obj.put("id", field.id)
            obj.put("name", field.name)
            obj.put("type", field.type.name)
            obj.put("required", field.required)
            obj.put("optionsJson", encodeStringList(field.options))
            obj.put("placeholder", field.placeholder)
            obj.put("orderIndex", idx)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeCustomFieldDefinitions(json: String): List<CustomFieldDefinition> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    add(
                        CustomFieldDefinition(
                            id = obj.optString("id", "field_${UUID.randomUUID()}"),
                            name = name,
                            type = CustomFieldType.fromKeyOrLabel(obj.optString("type", CustomFieldType.SHORT_TEXT.name)),
                            required = obj.optBoolean("required", false),
                            options = decodeStringList(obj.optString("optionsJson", "[]")),
                            placeholder = obj.optString("placeholder", ""),
                            orderIndex = obj.optInt("orderIndex", i)
                        )
                    )
                }
            }.sortedBy { it.orderIndex }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeCustomFieldsMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            if (k.isNotBlank()) {
                obj.put(k, v)
            }
        }
        return obj.toString()
    }

    fun decodeCustomFieldsMap(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return try {
            val obj = JSONObject(json)
            val out = linkedMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!obj.isNull(k)) {
                    out[k] = obj.optString(k, "")
                }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encodeCustomChecklistItems(items: List<CustomChecklistItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("text", item.text)
            obj.put("checked", item.checked)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeCustomChecklistItems(json: String): List<CustomChecklistItem> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val text = obj.optString("text", "")
                    add(
                        CustomChecklistItem(
                            id = obj.optString("id", "chk_${UUID.randomUUID()}"),
                            text = text,
                            checked = obj.optBoolean("checked", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

data class EditorDraftPayload(
    val itemId: String = UUID.randomUUID().toString(),
    val isEditingExisting: Boolean = false,
    val baseUpdatedAt: Long? = null,
    val categoryId: String = VaultCategory.IDEA.id,
    val folderId: String? = null,
    val ideaStatusId: String = IdeaStatus.NEW.id,
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val platform: String = "",
    val estimatedTime: String = "",
    val notes: String = "",
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val richBlocks: List<RichContentBlock> = emptyList(),
    val attachments: List<AttachmentItem> = emptyList(),
    val steps: List<MethodStepEntity> = emptyList(),
    val mainRouteName: String = DEFAULT_MAIN_METHOD_NAME,
    val mainRouteDescription: String = "",
    val alternativeRoutes: List<MethodRouteWithSteps> = emptyList(),
    val activeRouteId: String = MAIN_METHOD_ROUTE_ID,
    val pendingStepTitle: String = "",
    val pendingStepDetails: String = "",
    val pendingNewTag: String = "",
    val customTypeId: String? = null,
    val customFieldsJson: String = "{}",
    val pendingCustomType: CustomTypeEntity? = null,
    val savedAt: Long = System.currentTimeMillis()
)

sealed class SaveItemResult {
    data class Success(
        val itemId: String,
        val updatedAt: Long
    ) : SaveItemResult()

    data class Conflict(
        val itemId: String,
        val currentDatabaseItem: VaultItemWithDetails?,
        val expectedUpdatedAt: Long?,
        val actualUpdatedAt: Long,
        val message: String
    ) : SaveItemResult()

    data class ValidationFailure(
        val reason: String
    ) : SaveItemResult()

    data class StorageFailure(
        val reason: String,
        val diagnosticCode: String? = null
    ) : SaveItemResult()
}


