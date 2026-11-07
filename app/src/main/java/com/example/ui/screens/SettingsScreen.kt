package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.UserPrefsSnapshot
import com.example.data.ValidatedBackupPayload
import com.example.data.VaultCategory
import com.example.data.VaultItemWithDetails
import com.example.ui.components.CategoryPillBadge
import com.example.ui.components.VaultEmptyState
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.IdeaVaultTheme
import kotlinx.coroutines.launch

/**
 * IDEAVAULT V2 Profile Screen (Board 01 - 5. Profile):
 * - Header: "Profile"
 * - Three primary cards with icon badge, title, subtitle, and right chevron:
 *   1. Collections ("Organize folders and saved entries") -> opens Collections/Folders
 *   2. Settings ("Manage app preferences and your data") -> opens SettingsScreen
 *   3. About ("Application information") -> opens About dialog preserving developer identity
 */
@Composable
fun ProfileScreen(
    onOpenCollections: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    var showAboutDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("profile_screen_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("profile_header_title")
                )
            }

            // 1. Collections Card
            item {
                ProfileMenuCard(
                    icon = Icons.Filled.Folder,
                    iconTint = vaultColors.darkInk,
                    iconBadgeColor = vaultColors.actionYellow,
                    title = "Collections",
                    subtitle = "Organize folders and saved entries",
                    testTag = "profile_card_collections",
                    onClick = onOpenCollections
                )
            }

            // 2. Settings Card
            item {
                ProfileMenuCard(
                    icon = Icons.Outlined.Settings,
                    iconTint = if (vaultColors.isDark) vaultColors.primaryText else vaultColors.darkInk,
                    iconBadgeColor = vaultColors.secondarySurface,
                    title = "Settings",
                    subtitle = "Manage app preferences and your data",
                    testTag = "profile_card_settings",
                    onClick = onOpenSettings
                )
            }

            // 3. About Card
            item {
                ProfileMenuCard(
                    icon = Icons.Outlined.Info,
                    iconTint = vaultColors.darkInk,
                    iconBadgeColor = vaultColors.lavender,
                    title = "About",
                    subtitle = "Application information",
                    testTag = "profile_card_about",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.tagline),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = vaultColors.primaryText
                    )
                    Text(
                        text = "Personal Knowledge Management • v${BuildConfig.VERSION_NAME}\n100% offline-first storage for capturing ideas, step-by-step methods, reusable AI prompts, and rich notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = vaultColors.secondaryText
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = vaultColors.secondarySurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Developer",
                                style = MaterialTheme.typography.labelSmall,
                                color = vaultColors.secondaryText
                            )
                            Text(
                                text = stringResource(R.string.developer_credit),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = vaultColors.primaryText
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.actionYellow,
                        contentColor = vaultColors.darkInk
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ProfileMenuCard(
    icon: ImageVector,
    iconTint: Color,
    iconBadgeColor: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = vaultColors.surface,
        border = BorderStroke(1.dp, vaultColors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBadgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = vaultColors.secondaryText
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = vaultColors.secondaryText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun SettingsScreen(
    prefs: UserPrefsSnapshot,
    activeCount: Int,
    archivedCount: Int,
    trashedCount: Int,
    pendingBackupPayload: ValidatedBackupPayload?,
    onBack: (() -> Unit)? = null,
    onUpdateUserName: (String) -> Unit,
    onUpdateThemeMode: (AppThemeMode) -> Unit,
    onSetupLockPin: suspend (String) -> Boolean,
    onChangeOrDisableLockPin: suspend (String, String) -> Boolean,
    onOpenArchived: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenStats: () -> Unit,
    onSeedSampleData: () -> Unit = {},
    onExportBackupUri: (Uri) -> Unit,
    onExportQuickLocalBackup: () -> Unit,
    onImportBackupUri: (Uri) -> Unit,
    onConfirmRestoreBackup: (ValidatedBackupPayload) -> Unit,
    onDismissPendingBackup: () -> Unit,
    isPrimaryTab: Boolean = false
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }
    val vaultColors = IdeaVaultTheme.colors
    val coroutineScope = rememberCoroutineScope()

    var showProfileDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onExportBackupUri(uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportBackupUri(uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("settings_screen_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onBack != null && !isPrimaryTab) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(vaultColors.surface)
                                .testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = vaultColors.primaryText
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText,
                            modifier = Modifier.testTag("settings_header_title")
                        )
                        Text(
                            text = "Appearance, data & backup, app lock, archive & trash",
                            style = MaterialTheme.typography.bodyMedium,
                            color = vaultColors.secondaryText
                        )
                    }
                }
            }

            // Section 1: Appearance
            item {
                SettingsSectionHeader("Appearance")
            }

            item {
                val modeLabel = when (prefs.themeMode) {
                    AppThemeMode.DARK -> "Dark Theme"
                    AppThemeMode.LIGHT -> "White / Light Theme"
                    AppThemeMode.SYSTEM -> "Follow System"
                }
                SettingsActionCard(
                    icon = Icons.Filled.Palette,
                    iconTint = vaultColors.actionYellow,
                    title = "Appearance & Theme",
                    subtitle = modeLabel,
                    testTag = "settings_row_theme",
                    onClick = { showThemeDialog = true }
                )
            }

            item {
                SettingsActionCard(
                    icon = Icons.Filled.Person,
                    iconTint = vaultColors.coral,
                    title = "Display Name",
                    subtitle = if (prefs.userName.isNotBlank()) prefs.userName else "Personalize your Home greeting",
                    testTag = "settings_row_profile",
                    onClick = { showProfileDialog = true }
                )
            }

            // Section 2: Data & Backup
            item {
                SettingsSectionHeader("Data & Backup")
            }

            item {
                SettingsActionCard(
                    icon = Icons.Filled.Backup,
                    iconTint = vaultColors.lavender,
                    title = "Backup & Restore (ZIP)",
                    subtitle = "Export or restore a verified ZIP backup of your vault",
                    testTag = "settings_row_backup",
                    onClick = { showBackupDialog = true }
                )
            }

            item {
                SettingsActionCard(
                    icon = Icons.Filled.BarChart,
                    iconTint = vaultColors.pink,
                    title = "Vault Statistics",
                    subtitle = "$activeCount active entries across categories",
                    testTag = "settings_row_stats",
                    onClick = onOpenStats
                )
            }

            // Section 3: App Lock & Privacy
            item {
                SettingsSectionHeader("App Lock & Privacy")
            }

            item {
                SettingsActionCard(
                    icon = Icons.Filled.Lock,
                    iconTint = vaultColors.lime,
                    title = "App Lock (PIN Protection)",
                    subtitle = if (prefs.isAppLockEnabled) "Enabled — PIN protection active" else "Disabled — tap to set a 4–8 digit PIN",
                    testTag = "settings_row_applock",
                    onClick = { showAppLockDialog = true }
                )
            }

            // Section 4: Archive & Trash
            item {
                SettingsSectionHeader("Archive & Trash")
            }

            item {
                SettingsActionCard(
                    icon = Icons.Filled.Archive,
                    iconTint = vaultColors.secondaryText,
                    title = "Archived Entries",
                    subtitle = "$archivedCount archived item(s)",
                    testTag = "settings_row_archive",
                    onClick = onOpenArchived
                )
            }

            item {
                SettingsActionCard(
                    icon = Icons.Filled.DeleteOutline,
                    iconTint = vaultColors.coral,
                    title = "Trash",
                    subtitle = "$trashedCount item(s) in Trash",
                    testTag = "settings_row_trash",
                    onClick = onOpenTrash
                )
            }

            // Section 5: About
            item {
                SettingsSectionHeader("About")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = vaultColors.surface,
                    border = BorderStroke(1.dp, vaultColors.subtleBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_section_about")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.app_name)} • v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                        Text(
                            text = stringResource(R.string.tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                        Text(
                            text = "100% offline-first personal knowledge vault for Ideas, Methods, Prompts, and Notes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                        Text(
                            text = stringResource(R.string.developer_credit),
                            style = MaterialTheme.typography.labelMedium,
                            color = vaultColors.coral
                        )
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        var nameInput by remember(prefs.userName) { mutableStateOf(prefs.userName) }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            title = { Text("Display Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = vaultColors.primaryText,
                        unfocusedTextColor = vaultColors.primaryText,
                        focusedBorderColor = vaultColors.actionYellow,
                        unfocusedBorderColor = vaultColors.subtleBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateUserName(nameInput)
                        showProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.actionYellow,
                        contentColor = vaultColors.darkInk
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            title = { Text("Select Theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppThemeMode.DARK to "Dark Theme (V2 Editorial Dark)",
                        AppThemeMode.LIGHT to "White Theme (V2 Editorial Light)",
                        AppThemeMode.SYSTEM to "Follow System Appearance"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp)
                                .testTag("theme_option_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.themeMode == mode,
                                onClick = {
                                    onUpdateThemeMode(mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = vaultColors.actionYellow)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = vaultColors.primaryText
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Done", color = vaultColors.primaryText)
                }
            }
        )
    }

    if (showAppLockDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAppLockDialog = false },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            modifier = Modifier.testTag("app_lock_dialog"),
            title = {
                Text(if (prefs.isAppLockEnabled) "Manage App Lock PIN" else "Set App Lock PIN")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (prefs.isAppLockEnabled) {
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = {
                                currentPin = it.filter { ch -> ch.isDigit() }.take(8)
                                errorText = null
                            },
                            label = { Text("Current PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.testTag("app_lock_current_pin_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = vaultColors.primaryText,
                                unfocusedTextColor = vaultColors.primaryText,
                                focusedBorderColor = vaultColors.lime,
                                unfocusedBorderColor = vaultColors.subtleBorder
                            )
                        )
                    }
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            newPin = it.filter { ch -> ch.isDigit() }.take(8)
                            errorText = null
                        },
                        label = {
                            Text(if (prefs.isAppLockEnabled) "New PIN (leave blank to disable)" else "New 4–8 digit PIN")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.testTag("app_lock_new_pin_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = vaultColors.lime,
                            unfocusedBorderColor = vaultColors.subtleBorder
                        )
                    )
                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.coral,
                            modifier = Modifier.testTag("app_lock_dialog_error_text")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!prefs.isAppLockEnabled) {
                            if (newPin.length < 4) {
                                errorText = "PIN must be at least 4 digits."
                            } else {
                                coroutineScope.launch {
                                    val saved = onSetupLockPin(newPin)
                                    if (saved) {
                                        showAppLockDialog = false
                                    } else {
                                        errorText = "Failed to save PIN. Please try again."
                                    }
                                }
                            }
                        } else {
                            if (newPin.isNotEmpty() && newPin.length < 4) {
                                errorText = "New PIN must be at least 4 digits, or leave empty to disable."
                            } else {
                                coroutineScope.launch {
                                    val ok = onChangeOrDisableLockPin(currentPin, newPin)
                                    if (ok) {
                                        showAppLockDialog = false
                                    } else {
                                        errorText = "Current PIN is incorrect."
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.lime,
                        contentColor = vaultColors.darkInk
                    ),
                    modifier = Modifier.testTag("app_lock_dialog_save_button")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAppLockDialog = false },
                    modifier = Modifier.testTag("app_lock_dialog_cancel_button")
                ) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Backup & Restore") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Export your entire vault (entries, rich formatting, method steps, folders, and attachments) to a verified ZIP archive, or restore from an existing backup."
                    )
                    Button(
                        onClick = {
                            showBackupDialog = false
                            exportBackupLauncher.launch("ideavault_backup_${System.currentTimeMillis()}.zip")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vaultColors.lavender,
                            contentColor = vaultColors.darkInk
                        )
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Export ZIP to Device Storage", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showBackupDialog = false
                            onExportQuickLocalBackup()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, vaultColors.subtleBorder)
                    ) {
                        Text("Quick Snapshot to App Storage", color = vaultColors.primaryText)
                    }

                    OutlinedButton(
                        onClick = {
                            showBackupDialog = false
                            importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, vaultColors.coral)
                    ) {
                        Icon(Icons.Filled.RestorePage, contentDescription = null, tint = vaultColors.coral)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Import & Validate ZIP Backup", color = vaultColors.primaryText)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("Close", color = vaultColors.secondaryText)
                }
            }
        )
    }

    pendingBackupPayload?.let { payload ->
        AlertDialog(
            onDismissRequest = onDismissPendingBackup,
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Confirm Vault Restore") },
            text = {
                Text(
                    "Backup verified successfully:\n" +
                        "• ${payload.items.size} Entries\n" +
                        "• ${payload.folders.size} Folders\n" +
                        "• ${payload.steps.size} Method Steps\n" +
                        "• ${payload.stagedAttachmentFiles.size} Attachment Files\n\n" +
                        "Restoring will replace current vault contents transactionally with automatic rollback protection."
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmRestoreBackup(payload) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.lime,
                        contentColor = vaultColors.darkInk
                    )
                ) {
                    Text("Restore Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPendingBackup) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val vaultColors = IdeaVaultTheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = vaultColors.secondaryText,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = vaultColors.surface,
        border = BorderStroke(1.dp, vaultColors.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(vaultColors.secondarySurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = vaultColors.secondaryText
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = vaultColors.secondaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ArchivedItemsScreen(
    items: List<VaultItemWithDetails>,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onUnarchive: (String) -> Unit
) {
    BackHandler { onBack() }
    val vaultColors = IdeaVaultTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("archived_items_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.surface)
                            .testTag("archived_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = vaultColors.primaryText
                        )
                    }
                    Column {
                        Text(
                            text = "Archived Entries",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = vaultColors.primaryText
                        )
                        Text(
                            text = "${items.size} archived item(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    VaultEmptyState(
                        title = "No Archived Entries",
                        subtitle = "Archive completed or reference entries to keep your active vault clean without deleting them."
                    )
                }
            } else {
                items(items, key = { it.entity.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = vaultColors.surface,
                        border = BorderStroke(1.dp, vaultColors.subtleBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenItem(item.entity.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CategoryPillBadge(category = item.categoryEnum)
                                Text(
                                    text = item.entity.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = vaultColors.primaryText
                                )
                            }
                            IconButton(
                                onClick = { onUnarchive(item.entity.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("archived_unarchive_${item.entity.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Unarchive,
                                    contentDescription = "Unarchive",
                                    tint = vaultColors.lime
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrashScreen(
    items: List<VaultItemWithDetails>,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onDeletePermanently: (String) -> Unit,
    onEmptyTrash: () -> Unit
) {
    BackHandler { onBack() }
    val vaultColors = IdeaVaultTheme.colors
    var showEmptyTrashConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var itemIdPendingPermanentDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val itemPendingPermanentDelete = remember(items, itemIdPendingPermanentDelete) {
        items.firstOrNull { it.entity.id == itemIdPendingPermanentDelete }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("trash_items_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(vaultColors.surface)
                                .testTag("trash_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = vaultColors.primaryText
                            )
                        }
                        Column {
                            Text(
                                text = "Trash",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = vaultColors.primaryText
                            )
                            Text(
                                text = "${items.size} item(s) in Trash",
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.secondaryText
                            )
                        }
                    }

                    if (items.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showEmptyTrashConfirmDialog = true },
                            border = BorderStroke(1.dp, vaultColors.coral),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("trash_empty_button")
                        ) {
                            Text("Empty Trash", color = vaultColors.coral)
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    VaultEmptyState(
                        title = "Trash Is Empty",
                        subtitle = "Items moved to Trash can be restored or permanently deleted here."
                    )
                }
            } else {
                items(items, key = { it.entity.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = vaultColors.surface,
                        border = BorderStroke(1.dp, vaultColors.subtleBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trash_item_row_${item.entity.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CategoryPillBadge(category = item.categoryEnum)
                                Text(
                                    text = item.entity.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = vaultColors.primaryText
                                )
                            }
                            IconButton(
                                onClick = { onRestore(item.entity.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("trash_restore_${item.entity.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Restore,
                                    contentDescription = "Restore",
                                    tint = vaultColors.lime
                                )
                            }
                            IconButton(
                                onClick = { itemIdPendingPermanentDelete = item.entity.id },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("trash_delete_${item.entity.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteForever,
                                    contentDescription = "Delete Permanently",
                                    tint = vaultColors.coral
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmDialog = false },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = {
                Text(
                    text = "Empty Trash Permanently?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all ${items.size} item(s) in Trash and remove any unreferenced local attachments. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashConfirmDialog = false
                        onEmptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.coral,
                        contentColor = vaultColors.darkInk
                    ),
                    modifier = Modifier.testTag("confirm_empty_trash_button")
                ) {
                    Text("Empty Trash", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEmptyTrashConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_empty_trash_button")
                ) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    if (itemIdPendingPermanentDelete != null) {
        val targetId = itemIdPendingPermanentDelete!!
        val targetTitle = itemPendingPermanentDelete?.entity?.title ?: "this entry"
        AlertDialog(
            onDismissRequest = { itemIdPendingPermanentDelete = null },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = {
                Text(
                    text = "Delete Permanently?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Permanently delete \"$targetTitle\" and any unreferenced local attachments? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemIdPendingPermanentDelete = null
                        onDeletePermanently(targetId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.coral,
                        contentColor = vaultColors.darkInk
                    ),
                    modifier = Modifier.testTag("confirm_permanent_delete_button")
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemIdPendingPermanentDelete = null },
                    modifier = Modifier.testTag("cancel_permanent_delete_button")
                ) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }
}

@Composable
fun ActivityStatsScreen(
    items: List<VaultItemWithDetails>,
    categoryCounts: Map<VaultCategory, Int>,
    folderCount: Int,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val vaultColors = IdeaVaultTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("activity_stats_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.surface)
                            .testTag("activity_stats_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = vaultColors.primaryText
                        )
                    }
                    Column {
                        Text(
                            text = "Vault Statistics",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = vaultColors.primaryText
                        )
                        Text(
                            text = "${items.size} active entries • $folderCount folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }
            }

            items(VaultCategory.v2PrimaryCategories, key = { it.id }) { cat ->
                val count = if (cat == VaultCategory.KNOWLEDGE) {
                    (categoryCounts[VaultCategory.KNOWLEDGE] ?: 0) +
                        (categoryCounts[VaultCategory.REFERENCE] ?: 0) +
                        (categoryCounts[VaultCategory.INBOX] ?: 0)
                } else {
                    categoryCounts[cat] ?: 0
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = vaultColors.surface,
                    border = BorderStroke(1.dp, vaultColors.subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryPillBadge(category = cat)
                        Text(
                            text = "$count entries",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                    }
                }
            }
        }
    }
}
