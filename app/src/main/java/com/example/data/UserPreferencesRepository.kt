package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "ideavault_prefs")

internal interface PreferencesFaultPolicy {
    fun beforeRead() {}
    fun beforeWrite() {}

    companion object {
        val NONE: PreferencesFaultPolicy = object : PreferencesFaultPolicy {}
    }
}

data class UserPrefsSnapshot(
    val isLoaded: Boolean = false,
    val hasLoadError: Boolean = false,
    val loadErrorMessage: String? = null,
    val hasCompletedOnboarding: Boolean = false,
    val userName: String = "",
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val isAppLockEnabled: Boolean = false,
    val editorDraftJson: String = ""
)

class UserPreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val faultPolicy: PreferencesFaultPolicy
) {
    constructor(dataStore: DataStore<Preferences>) : this(dataStore, PreferencesFaultPolicy.NONE)
    constructor(context: Context) : this(context.applicationContext.vaultDataStore, PreferencesFaultPolicy.NONE)

    companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("has_completed_onboarding")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        // Legacy plaintext key kept strictly for one-way migration & removal
        val KEY_LEGACY_APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val KEY_APP_LOCK_HASH = stringPreferencesKey("app_lock_pin_hash_v2")
        val KEY_APP_LOCK_SALT = stringPreferencesKey("app_lock_pin_salt_v2")
        val KEY_APP_LOCK_ITERATIONS = intPreferencesKey("app_lock_pin_iterations_v2")
        val KEY_EDITOR_DRAFT = stringPreferencesKey("editor_draft_json")

        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_PBKDF2_ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH_BYTES = 16
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "ideavault_applock_pepper_v2"
        private const val PREFIX_KEYSTORE = "v2_ks:"
        private const val PREFIX_PBKDF2 = "v2_pbkdf2:"
    }

    private sealed interface StreamPrefsState {
        data class Loaded(val prefs: Preferences) : StreamPrefsState
        data class Failed(val message: String) : StreamPrefsState
    }

    private val retryTriggerFlow = MutableStateFlow(0)
    private val committedPrefsFlow = MutableStateFlow<Preferences?>(null)

    fun retryLoadPreferences() {
        retryTriggerFlow.value += 1
    }

    private suspend fun editPrefs(transform: suspend (MutablePreferences) -> Unit): Preferences {
        val updated = dataStore.edit(transform)
        committedPrefsFlow.value = updated
        return updated
    }

    private fun mapPreferencesToSnapshot(prefs: Preferences): UserPrefsSnapshot {
        val rawTheme = prefs[KEY_THEME_MODE] ?: AppThemeMode.DARK.name
        val themeMode = try {
            AppThemeMode.valueOf(rawTheme)
        } catch (_: Exception) {
            AppThemeMode.DARK
        }
        val hasHashedLock = !prefs[KEY_APP_LOCK_HASH].isNullOrBlank() &&
            !prefs[KEY_APP_LOCK_SALT].isNullOrBlank()
        val hasLegacyLock = !prefs[KEY_LEGACY_APP_LOCK_PIN].isNullOrBlank()
        return UserPrefsSnapshot(
            isLoaded = true,
            hasLoadError = false,
            loadErrorMessage = null,
            hasCompletedOnboarding = prefs[KEY_ONBOARDING_DONE] ?: false,
            userName = prefs[KEY_USER_NAME] ?: "",
            themeMode = themeMode,
            isAppLockEnabled = hasHashedLock || hasLegacyLock,
            editorDraftJson = prefs[KEY_EDITOR_DRAFT] ?: ""
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val prefsFlow: Flow<UserPrefsSnapshot> = retryTriggerFlow
        .flatMapLatest {
            val streamFlow: Flow<StreamPrefsState> = dataStore.data
                .onStart {
                    faultPolicy.beforeRead()
                }
                .onEach { prefs ->
                    faultPolicy.beforeRead()
                    committedPrefsFlow.value = prefs
                    if (prefs.contains(KEY_LEGACY_APP_LOCK_PIN)) {
                        migrateLegacyPlaintextPinIfNeeded()
                    }
                }
                .map<Preferences, StreamPrefsState> { prefs ->
                    StreamPrefsState.Loaded(prefs)
                }
                .catch { exception ->
                    emit(
                        StreamPrefsState.Failed(
                            exception.message ?: "Unable to read security preferences."
                        )
                    )
                }

            combine(streamFlow, committedPrefsFlow) { streamState, committedPrefs ->
                when (streamState) {
                    is StreamPrefsState.Failed -> UserPrefsSnapshot(
                        isLoaded = false,
                        hasLoadError = true,
                        loadErrorMessage = streamState.message
                    )
                    is StreamPrefsState.Loaded -> {
                        mapPreferencesToSnapshot(committedPrefs ?: streamState.prefs)
                    }
                }
            }
        }
        .distinctUntilChanged()

    suspend fun getSnapshot(): UserPrefsSnapshot {
        return try {
            faultPolicy.beforeRead()
            migrateLegacyPlaintextPinIfNeeded()
            prefsFlow.first()
        } catch (e: Exception) {
            UserPrefsSnapshot(
                isLoaded = false,
                hasLoadError = true,
                loadErrorMessage = e.message ?: "Unable to read security preferences."
            )
        }
    }

    /**
     * Migrates any legacy plaintext PIN in DataStore into a salted PBKDF2-HMAC-SHA256
     * credential record and removes the plaintext key so existing users are never locked out
     * while plaintext storage is eliminated.
     */
    suspend fun migrateLegacyPlaintextPinIfNeeded() {
        editPrefs { prefs ->
            val legacyPin = prefs[KEY_LEGACY_APP_LOCK_PIN] ?: return@editPrefs
            val cleaned = legacyPin.trim()
            prefs.remove(KEY_LEGACY_APP_LOCK_PIN)
            if (cleaned.isNotEmpty() && prefs[KEY_APP_LOCK_HASH].isNullOrBlank()) {
                val record = createHashedPinRecord(cleaned)
                prefs[KEY_APP_LOCK_HASH] = record.encodedHash
                prefs[KEY_APP_LOCK_SALT] = record.saltHex
                prefs[KEY_APP_LOCK_ITERATIONS] = record.iterations
            }
        }
    }

    suspend fun completeOnboarding(userName: String): Boolean {
        val cleaned = userName.trim().ifEmpty { "Creator" }
        return try {
            faultPolicy.beforeWrite()
            val updated = editPrefs { prefs ->
                prefs[KEY_USER_NAME] = cleaned
                prefs[KEY_ONBOARDING_DONE] = true
            }
            updated[KEY_ONBOARDING_DONE] == true && updated[KEY_USER_NAME] == cleaned
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateUserName(userName: String) {
        val cleaned = userName.trim()
        if (cleaned.isNotEmpty()) {
            editPrefs { prefs ->
                prefs[KEY_USER_NAME] = cleaned
            }
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        editPrefs { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    /**
     * Updates or disables the App Lock PIN using salted PBKDF2-HMAC-SHA256.
     * Never writes plaintext PIN values to DataStore.
     * Returns true only after persistence is verified in DataStore.
     */
    suspend fun updateAppLockPin(pin: String): Boolean {
        val cleaned = pin.trim()
        if (cleaned.isNotEmpty() && (cleaned.length !in 4..8 || !cleaned.all { it.isDigit() })) {
            return false
        }
        return try {
            faultPolicy.beforeWrite()
            val record = if (cleaned.isEmpty()) null else createHashedPinRecord(cleaned)
            val updatedPrefs = editPrefs { prefs ->
                prefs.remove(KEY_LEGACY_APP_LOCK_PIN)
                if (record == null) {
                    prefs.remove(KEY_APP_LOCK_HASH)
                    prefs.remove(KEY_APP_LOCK_SALT)
                    prefs.remove(KEY_APP_LOCK_ITERATIONS)
                } else {
                    prefs[KEY_APP_LOCK_HASH] = record.encodedHash
                    prefs[KEY_APP_LOCK_SALT] = record.saltHex
                    prefs[KEY_APP_LOCK_ITERATIONS] = record.iterations
                }
            }
            if (record == null) {
                updatedPrefs[KEY_APP_LOCK_HASH].isNullOrBlank() &&
                    updatedPrefs[KEY_LEGACY_APP_LOCK_PIN].isNullOrBlank()
            } else {
                !updatedPrefs[KEY_APP_LOCK_HASH].isNullOrBlank() &&
                    !updatedPrefs[KEY_APP_LOCK_SALT].isNullOrBlank() &&
                    updatedPrefs[KEY_LEGACY_APP_LOCK_PIN].isNullOrBlank()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies a candidate PIN in constant time against the stored salted PBKDF2-HMAC-SHA256 hash.
     */
    suspend fun verifyAppLockPin(candidatePin: String): Boolean {
        return try {
            faultPolicy.beforeRead()
            migrateLegacyPlaintextPinIfNeeded()
            val cleaned = candidatePin.trim()
            if (cleaned.isEmpty()) return false

            val rawPrefs = dataStore.data.first()
            val storedHash = rawPrefs[KEY_APP_LOCK_HASH] ?: return false
            val saltHex = rawPrefs[KEY_APP_LOCK_SALT] ?: return false
            val iterations = rawPrefs[KEY_APP_LOCK_ITERATIONS] ?: DEFAULT_PBKDF2_ITERATIONS
            if (storedHash.isBlank() || saltHex.isBlank() || iterations <= 0) return false

            val saltBytes = try {
                hexToBytes(saltHex)
            } catch (_: Exception) {
                return false
            }
            if (saltBytes.isEmpty()) return false

            val useKeystore = storedHash.startsWith(PREFIX_KEYSTORE)
            val expectedHex = when {
                storedHash.startsWith(PREFIX_KEYSTORE) -> storedHash.removePrefix(PREFIX_KEYSTORE)
                storedHash.startsWith(PREFIX_PBKDF2) -> storedHash.removePrefix(PREFIX_PBKDF2)
                else -> storedHash
            }
            val expectedBytes = try {
                hexToBytes(expectedHex)
            } catch (_: Exception) {
                return false
            }

            val pbkdf2Bytes = derivePbkdf2Bytes(cleaned, saltBytes, iterations)
            val candidateBytes = if (useKeystore) {
                computeKeystoreHmacIfAvailable(pbkdf2Bytes) ?: return false
            } else {
                pbkdf2Bytes
            }

            MessageDigest.isEqual(candidateBytes, expectedBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Changes or disables the App Lock PIN only after verifying the existing PIN when App Lock is active.
     */
    suspend fun changeOrDisableAppLockPin(currentPin: String, newPin: String): Boolean {
        val snapshot = getSnapshot()
        if (!snapshot.isLoaded || snapshot.hasLoadError) {
            return false
        }
        if (snapshot.isAppLockEnabled) {
            if (!verifyAppLockPin(currentPin)) {
                return false
            }
        }
        val cleanedNew = newPin.trim()
        if (cleanedNew.isNotEmpty() && (cleanedNew.length !in 4..8 || !cleanedNew.all { it.isDigit() })) {
            return false
        }
        return updateAppLockPin(cleanedNew)
    }

    /**
     * Used by security verification tests to confirm no plaintext PIN key remains in raw storage
     * and the stored hash does not equal the raw PIN.
     */
    suspend fun inspectStoredLockMetadataForVerification(): StoredLockVerificationMetadata {
        val rawPrefs = dataStore.data.first()
        return StoredLockVerificationMetadata(
            legacyPlaintextValue = rawPrefs[KEY_LEGACY_APP_LOCK_PIN],
            storedHashValue = rawPrefs[KEY_APP_LOCK_HASH],
            storedSaltHex = rawPrefs[KEY_APP_LOCK_SALT],
            storedIterations = rawPrefs[KEY_APP_LOCK_ITERATIONS]
        )
    }

    suspend fun saveEditorDraft(draftJson: String) {
        editPrefs { prefs ->
            prefs[KEY_EDITOR_DRAFT] = draftJson
        }
    }

    suspend fun clearEditorDraft() {
        editPrefs { prefs ->
            prefs.remove(KEY_EDITOR_DRAFT)
        }
    }

    private data class HashedPinRecord(
        val encodedHash: String,
        val saltHex: String,
        val iterations: Int
    )

    private fun createHashedPinRecord(rawPin: String): HashedPinRecord {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        val iterations = DEFAULT_PBKDF2_ITERATIONS
        val pbkdf2Bytes = derivePbkdf2Bytes(rawPin, salt, iterations)
        val keystoreWrapped = computeKeystoreHmacIfAvailable(pbkdf2Bytes)
        val encodedHash = if (keystoreWrapped != null) {
            PREFIX_KEYSTORE + bytesToHex(keystoreWrapped)
        } else {
            PREFIX_PBKDF2 + bytesToHex(pbkdf2Bytes)
        }
        return HashedPinRecord(
            encodedHash = encodedHash,
            saltHex = bytesToHex(salt),
            iterations = iterations
        )
    }

    private fun derivePbkdf2Bytes(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun computeKeystoreHmacIfAvailable(input: ByteArray): ByteArray? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val secretKey: SecretKey = (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey) ?: run {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
            mac.init(secretKey)
            mac.doFinal(input)
        } catch (_: Exception) {
            null
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Invalid hex length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

data class StoredLockVerificationMetadata(
    val legacyPlaintextValue: String?,
    val storedHashValue: String?,
    val storedSaltHex: String?,
    val storedIterations: Int?
)
