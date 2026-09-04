package com.datools.qrchecker.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.datools.qrchecker.popBackStackOnce
import com.datools.qrchecker.R
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.util.AppSettings
import com.datools.qrchecker.util.ClockChoice
import com.datools.qrchecker.util.LanguageChoice
import com.datools.qrchecker.util.Outcome
import com.datools.qrchecker.util.SessionBackup
import com.datools.qrchecker.util.ThemeChoice
import com.datools.qrchecker.util.rememberFeedback
import kotlinx.coroutines.launch

private const val TAG = "QRChecker"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var folderName by remember { mutableStateOf(SessionBackup.folderName(context)) }
    val theme by AppSettings.themeState(context)
    val language by AppSettings.languageState(context)
    val clock by AppSettings.clockState(context)
    val haptics by AppSettings.hapticsState(context)
    val sound by AppSettings.soundState(context)
    val feel = rememberFeedback(withSound = true)
    var enabled by remember { mutableStateOf(SessionBackup.isEnabled(context)) }
    var restoring by remember { mutableStateOf(false) }

    val title = stringResource(id = R.string.settings_title)
    val appearanceTitle = stringResource(id = R.string.appearance_title)
    val themeLabel = stringResource(id = R.string.theme_label)
    val languageLabel = stringResource(id = R.string.language_label)
    val feedbackTitle = stringResource(id = R.string.feedback_title)
    val feedbackWhy = stringResource(id = R.string.feedback_why)
    val hapticsLabel = stringResource(id = R.string.feedback_haptics)
    val soundLabel = stringResource(id = R.string.feedback_sound)
    val themeOptions = listOf(
        ThemeChoice.SYSTEM to stringResource(id = R.string.theme_system),
        ThemeChoice.LIGHT to stringResource(id = R.string.theme_light),
        ThemeChoice.DARK to stringResource(id = R.string.theme_dark)
    )
    val languageOptions = listOf(
        LanguageChoice.SYSTEM to stringResource(id = R.string.language_system),
        LanguageChoice.RUSSIAN to stringResource(id = R.string.language_russian),
        LanguageChoice.ENGLISH to stringResource(id = R.string.language_english)
    )
    val clockLabel = stringResource(id = R.string.clock_label)
    val clockOptions = listOf(
        ClockChoice.SYSTEM to stringResource(id = R.string.clock_system),
        ClockChoice.H24 to stringResource(id = R.string.clock_24),
        ClockChoice.H12 to stringResource(id = R.string.clock_12)
    )
    val backupTitle = stringResource(id = R.string.backup_title)
    val backupWhy = stringResource(id = R.string.backup_why)
    val pickFolderFirst = stringResource(id = R.string.backup_pick_folder_first)
    val folderNone = stringResource(id = R.string.backup_folder_none)
    val chooseFolder = stringResource(id = R.string.backup_choose_folder)
    val changeFolder = stringResource(id = R.string.backup_change_folder)
    val forgetFolder = stringResource(id = R.string.backup_forget_folder)
    val autoLabel = stringResource(id = R.string.backup_auto)
    val restoreLabel = stringResource(id = R.string.backup_restore)
    val folderFailed = stringResource(id = R.string.backup_folder_failed)
    val restoreTemplate = stringResource(id = R.string.backup_restored)
    val restoreFailed = stringResource(id = R.string.backup_restore_failed)

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (SessionBackup.setFolder(context, uri)) {
            folderName = SessionBackup.folderName(context)
            enabled = SessionBackup.isEnabled(context)
        } else {
            scope.launch { snackbarHostState.showSnackbar(folderFailed) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStackOnce() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(appearanceTitle, style = MaterialTheme.typography.titleMedium)

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(themeLabel, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    // тема показывается собой: подписью «тёмная» можно было бы и
                    // ошибиться, а образцом - нет
                    ThemePicker(
                        options = themeOptions,
                        selected = theme,
                        onSelect = { AppSettings.setTheme(context, it) }
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(languageLabel, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    ChoiceRow(
                        options = languageOptions,
                        selected = language,
                        onSelect = { AppSettings.setLanguage(context, it) }
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(clockLabel, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    ChoiceRow(
                        options = clockOptions,
                        selected = clock,
                        onSelect = { AppSettings.setClock(context, it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(feedbackTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = feedbackWhy,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SwitchRow(
                        icon = R.drawable.ic_vibration,
                        label = hapticsLabel,
                        checked = haptics,
                        onCheckedChange = {
                            AppSettings.setHaptics(context, it)
                            // включил - тут же почувствовал; иначе о том, что тумблер
                            // подействовал, можно узнать только на складе. Только
                            // вибрация: пищать в ответ на вибрацию - это ответ соседа
                            if (it) feel.vibrateOnly(Outcome.SUCCESS)
                        }
                    )

                    SwitchRow(
                        icon = R.drawable.ic_volume,
                        label = soundLabel,
                        checked = sound,
                        onCheckedChange = {
                            AppSettings.setSound(context, it)
                            if (it) feel.playOnly(Outcome.SUCCESS)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(backupTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = backupWhy,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = folderName ?: folderNone,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickFolder.launch(null) }) {
                            Text(if (folderName == null) chooseFolder else changeFolder)
                        }
                        if (folderName != null) {
                            TextButton(onClick = {
                                SessionBackup.clearFolder(context)
                                folderName = null
                                enabled = false
                            }) {
                                Text(forgetFolder)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(autoLabel, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                // тумблер не заперт: запертый неотличим от сломанного.
                                // Без папки он объясняет, чего не хватает
                                if (folderName == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(pickFolderFirst)
                                    }
                                    return@Switch
                                }
                                enabled = it
                                SessionBackup.setEnabled(context, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        enabled = folderName != null && !restoring,
                        onClick = {
                            restoring = true
                            scope.launch {
                                try {
                                    val known = repo.existingIds()
                                    // восстанавливаются только те, которых на устройстве
                                    // нет: повторное восстановление ничего не затирает
                                    val missing =
                                        SessionBackup.readAll(context).filter { it.id !in known }
                                    for (session in missing) repo.insert(session)
                                    snackbarHostState.showSnackbar(
                                        restoreTemplate.format(missing.size)
                                    )
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Can't restore from the backup folder", t)
                                    snackbarHostState.showSnackbar(restoreFailed)
                                } finally {
                                    restoring = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(restoreLabel)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Ряд взаимоисключающих вариантов.
 *
 * Не выпадающий список и не столбик переключателей: вариантов три, они короткие, и
 * выбранный виден без единого нажатия.
 *
 * Галочка у выбранного убрана. Она вставала вплотную к левому краю кнопки, ужимала
 * подпись до «Как в» и объясняла то, что и так видно по заливке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { if (value != selected) onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {}
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Выбор темы образцами.
 *
 * Каждый вариант нарисован в той теме, которую включает: не подпись «тёмная», а сама
 * тёмная плашка с полосами вместо содержимого - видно, что получится, ещё до нажатия.
 * «Системная» показывает ту тему, что стоит в телефоне сейчас: это и есть ответ на
 * вопрос «а что мне включится», а отличается она от соседней рамкой и подписью.
 */
@Composable
private fun ThemePicker(
    options: List<Pair<ThemeChoice, String>>,
    selected: ThemeChoice,
    onSelect: (ThemeChoice) -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for ((value, label) in options) {
            val chosen = value == selected
            val dark = when (value) {
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
                ThemeChoice.SYSTEM -> systemIsDark
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { if (!chosen) onSelect(value) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SwatchFace(
                    dark = dark,
                    selected = chosen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chosen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private val SwatchLightBack = Color(0xFFFBFBFB)
private val SwatchLightCard = Color(0xFFECECEF)
private val SwatchLightInk = Color(0xFF1B1B1B)
private val SwatchDarkBack = Color(0xFF16181A)
private val SwatchDarkCard = Color(0xFF25282B)
private val SwatchDarkInk = Color(0xFFE8E8E8)

/** Кусочек экрана в заданной теме: полоса заголовка и две карточки под ней. */
@Composable
private fun SwatchFace(
    dark: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium
    val back = if (dark) SwatchDarkBack else SwatchLightBack
    val card = if (dark) SwatchDarkCard else SwatchLightCard
    val ink = if (dark) SwatchDarkInk else SwatchLightInk
    val ring =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Box(
        // обводка снаружи, обрезка после неё: в обратном порядке рамка рисуется внутри
        // уже обрезанной области, и по скруглённым углам от неё остаются огрызки
        modifier = modifier
            .border(if (selected) 2.dp else 1.dp, ring, shape)
            .clip(shape)
            .background(back)
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ink)
            )
            repeat(2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(card)
                )
            }
        }
    }
}

/** Строка настройки со значком, подписью и тумблером. */
@Composable
private fun SwitchRow(
    @DrawableRes icon: Int,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
