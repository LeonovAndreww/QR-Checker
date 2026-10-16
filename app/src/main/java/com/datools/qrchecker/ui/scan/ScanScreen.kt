package com.datools.qrchecker.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipData
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.datools.qrchecker.navigateOnce
import com.datools.qrchecker.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datools.qrchecker.ui.CodeMetaLine
import com.datools.qrchecker.Screen
import com.datools.qrchecker.TYPE_NOT_SCANNED
import com.datools.qrchecker.TYPE_SCANNED
import androidx.compose.material3.MaterialTheme
import com.datools.qrchecker.ui.theme.OnColor
import com.datools.qrchecker.ui.theme.accents
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import com.datools.qrchecker.util.formatTimeAgo
import com.datools.qrchecker.util.shortCode
import com.datools.qrchecker.util.AppSettings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import com.datools.qrchecker.util.shareSessionFile
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import androidx.lifecycle.Lifecycle
import com.datools.qrchecker.util.rememberFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


private const val TAG = "Scan"

/** Сколько подсказок показывать: экран телефона всё равно не вместит больше. */
private const val MANUAL_SUGGESTIONS = 30

/** Сколько висит пузырёк с кратностью после того, как щипок закончился. */
private const val ZOOM_BUBBLE_MS = 1200L

/** «1x», «2.5x», «0.6x» - так их подписывают в камерах. */
internal fun formatZoom(ratio: Float): String {
    val rounded = (ratio * 10f).roundToInt() / 10f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    return "${text}x"
}

/** Сколько экран держится включённым без единого кода в кадре. */
private const val SCREEN_AWAKE_IDLE_MS = 5 * 60_000L
private const val SCREEN_AWAKE_CHECK_MS = 5_000L

/** Сколько места занимает самая широкая группа кнопок в верхней полосе. */
private val TOP_BAR_SIDE = 112.dp

/** Высота затемнения под верхней и нижней полосами управления. */
private val CONTROL_SCRIM = 140.dp

/**
 * Подписи на двух нижних кнопках подбирают размер под ширину.
 *
 * «Неотсканированные» - слово из семнадцати букв, и на узком экране оно не влезает ни
 * при каком фиксированном кегле; обрезать его нельзя - обе кнопки перестают читаться.
 */
private val BUTTON_TEXT_SIZE = TextAutoSize.StepBased(
    minFontSize = 11.sp,
    maxFontSize = 16.sp,
    stepSize = 0.5.sp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionId = viewModel.sessionId
    val scope = rememberCoroutineScope()
    val accents = MaterialTheme.accents
    val view = LocalView.current

    val showScanCode by AppSettings.scanCodeState(context)
    val showCodeFields by AppSettings.codeFieldsState(context)
    val scanOnTap by AppSettings.scanOnTapState(context)

    // a torn or smudged label is otherwise a dead end
    var manualCode by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(0f) }
    var zoomShownAt by remember { mutableLongStateOf(0L) }
    var zoomVisible by remember { mutableStateOf(false) }
    // one horizontal scroll for all suggestions, so long codes stay aligned for comparing
    val suggestionScroll = rememberScrollState()
    var hasTorch by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val clipboard = LocalClipboard.current
    val copyCodeCd = stringResource(id = R.string.cd_copy_code)
    val codeCopiedText = stringResource(id = R.string.code_copied)

    fun copyScanned(code: String) {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyCodeCd, code)))
            // Android 13+ shows its own confirmation
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                snackbarHostState.showSnackbar(codeCopiedText)
            }
        }
    }

    val feel = rememberFeedback(withSound = true)
    LaunchedEffect(viewModel) {
        viewModel.answers.collect { feel(it.outcome) }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // The screen stays on while work goes on, but not forever over a forgotten phone:
    // after five minutes without a code in view the system timeout takes over again.
    var lastActiveAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { lifecycleState ->
            if (lifecycleState == Lifecycle.State.RESUMED) lastActiveAt = System.currentTimeMillis()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            view.keepScreenOn = System.currentTimeMillis() - lastActiveAt < SCREEN_AWAKE_IDLE_MS
            delay(SCREEN_AWAKE_CHECK_MS)
        }
    }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }

    // marked as opened once the transition is over, so it does not jump on the home list
    // while that is still visible
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        viewModel.touchOpened()
    }

    BackHandler {
        navController.navigateOnce(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        }
    }

    val alreadyScannedMsg = stringResource(id = R.string.msg_already_scanned)
    val alreadyScannedAgoTemplate = stringResource(id = R.string.msg_already_scanned_ago)
    val scannedMsg = stringResource(id = R.string.msg_scanned)
    val recordedMsg = stringResource(id = R.string.msg_recorded)
    val saveFailedMsg = stringResource(id = R.string.msg_save_failed)
    val collectedButtonText = stringResource(id = R.string.btn_collected)
    val notFoundMsg = stringResource(id = R.string.msg_not_in_list)
    val scannedButtonText = stringResource(id = R.string.btn_scanned)
    val notScannedButtonText = stringResource(id = R.string.btn_not_scanned)
    val noCameraPermissionText = stringResource(id = R.string.no_camera_permission)
    val manualEntryCd = stringResource(id = R.string.cd_manual_entry)
    val backCd = stringResource(id = R.string.cd_back)
    val shareSessionCd = stringResource(id = R.string.share_session)
    val manualEntryTitle = stringResource(id = R.string.manual_entry_title)
    val manualEntryLabel = stringResource(id = R.string.manual_entry_label)
    val manualEntryConfirm = stringResource(id = R.string.manual_entry_confirm)
    val manualEntryHint = stringResource(id = R.string.manual_entry_hint)
    val manualEntryNoMatches = stringResource(id = R.string.manual_entry_no_matches)
    val manualEntryWillRecord = stringResource(id = R.string.manual_entry_will_record)
    val manualEntryRecord = stringResource(id = R.string.manual_entry_record)
    val alreadyScannedLabel = stringResource(id = R.string.manual_entry_already_scanned)
    val cancelText = stringResource(id = R.string.delete_cancel)
    val shareFailedText = stringResource(id = R.string.session_share_failed)
    val torchOnCd = stringResource(id = R.string.cd_torch_on)
    val torchOffCd = stringResource(id = R.string.cd_torch_off)

    fun colorOf(kind: AnswerKind): OnColor = when (kind) {
        AnswerKind.MARKED, AnswerKind.RECORDED -> accents.success
        AnswerKind.REPEAT -> accents.warning
        AnswerKind.FOREIGN, AnswerKind.SAVE_FAILED -> accents.danger
    }

    val banner = state.banner
    val bannerColor = banner?.let { colorOf(it.kind) }
    val bannerText = banner?.let {
        when (it.kind) {
            AnswerKind.MARKED -> scannedMsg
            AnswerKind.RECORDED -> recordedMsg
            AnswerKind.FOREIGN -> notFoundMsg
            AnswerKind.SAVE_FAILED -> saveFailedMsg
            // when exactly matters: recounting the same box now, or one from the day before
            AnswerKind.REPEAT -> it.scannedAt?.let { at ->
                alreadyScannedAgoTemplate.format(formatTimeAgo(context, at))
            } ?: alreadyScannedMsg
        }
    }

    val loaded = state.session
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.loadFinished) {
                Text(stringResource(id = R.string.session_not_found))
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val progressText =
        stringResource(id = R.string.progress_format, loaded.scannedCodes.size, loaded.codes.size)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        // Камера идёт под системные панели во весь экран, а управление - нет. Сверху и
        // снизу отступы учитывались всегда, а по бокам нет: в альбомной ориентации
        // кнопки навигации телефона встают сбоку и закрывали собой кнопки приложения.
        val layoutDirection = LocalLayoutDirection.current
        val sides = Modifier.padding(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection)
        )
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(
                    highlightColor = bannerColor?.container,
                    // Цвет последнего ответа на этот код, а не его состояние.
                    //
                    // По состоянию отмеченный код был зелёным всегда - и коробка,
                    // отсканированная вчера, после того как погасла оранжевая плашка
                    // «уже отсканирован», обводилась зелёным, будто её отметили только
                    // что. Без ответа на этом экране отмеченный код - это «уже был».
                    // The colour of the last answer to this code, not its state: by state a
                    // code scanned yesterday would turn green once the orange "already
                    // scanned" banner faded, as if it had just been marked.
                    colorOf = { code ->
                        val current = state.session
                        state.answered[code]?.let { colorOf(it).container } ?: when {
                            current == null -> null
                            code !in current.codes -> accents.danger.container
                            code in current.scannedCodes -> accents.warning.container
                            else -> null
                        }
                    },
                    torchOn = torchOn,
                    onTorchAvailable = { hasTorch = it },
                    scanOnTap = scanOnTap,
                    // a tap asks again, even about the same code
                    onTapped = {
                        viewModel.askAgain()
                        lastActiveAt = System.currentTimeMillis()
                    },
                    onZoomChanged = { ratio ->
                        zoomRatio = ratio
                        zoomShownAt = System.currentTimeMillis()
                    },
                    onFrame = { visible, candidate ->
                        lastActiveAt = System.currentTimeMillis()
                        viewModel.onFrame(visible, candidate)
                    }
                )

                // Затемнение под полосами с кнопками.
                //
                // Управление лежит поверх живого кадра, а кадр бывает любой яркости, так
                // что цвет из темы на нём то читается, то нет: в светлой теме чёрные
                // буквы пропадали на тёмном кадре, в тёмной - белые на светлом. Здесь
                // фон делается предсказуемым, а буквы ниже - всегда белыми.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(CONTROL_SCRIM)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(CONTROL_SCRIM)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )
            }

            // Всё управление - внутри отступов от боковых системных панелей, камера под ними
            // остаётся во весь экран. Отступ один на всех, чтобы ни один новый элемент
            // не мог про него забыть.
            Box(modifier = Modifier.fillMaxSize().then(sides)) {
                // Одна полоса поверх камеры: слева «назад», справа действия, название -
                // ровно по центру экрана.
                //
                // Раньше название стояло в общем ряду и делило место с кнопками: слева от
                // него была одна стрелка, справа - значок и кнопка, и центр названия уезжал
                // влево тем сильнее, чем шире кнопка. Здесь кнопки лежат поверх, а название
                // центрируется по всей ширине и отступает от краёв на ширину самой широкой
                // стороны - так оно и по центру, и ни на что не налезает.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding() + 4.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        loaded.name,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = TOP_BAR_SIDE),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        // то же, что и системная «назад»: на список сессий. Обычный
                        // popBackStack возвращал на создание сессии, если пришли оттуда,
                        // и два способа уйти с экрана вели в разные места
                        onClick = {
                            navController.navigateOnce(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backCd,
                            tint = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Фонарь - первым: на складе между стеллажами и в кузове темно, а
                        // без света Data Matrix не читается вообще никак. Кнопка есть
                        // только там, где вспышка есть у камеры.
                        if (hasTorch) {
                            IconButton(onClick = { torchOn = !torchOn }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_torch_on),
                                    contentDescription = if (torchOn) torchOffCd else torchOnCd,
                                    tint = if (torchOn) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White
                                    }
                                )
                            }
                        }

                        // «Поделиться» живёт здесь, а не в правке сессии: это действие над
                        // той сессией, с которой человек сейчас работает, и искать его в
                        // настройках никто не станет
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    context.startActivity(shareSessionFile(context, loaded))
                                } catch (c: CancellationException) {
                                    throw c
                                } catch (t: Throwable) {
                                    // молчать нельзя: нажатие без единого следа читается как
                                    // сломанная кнопка
                                    Log.e(TAG, "Can't share the session", t)
                                    snackbarHostState.showSnackbar(shareFailedText)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = shareSessionCd,
                                tint = Color.White
                            )
                        }

                        // клавиатура вместо карандаша и подписи: карандаш - это «поправить
                        // то, что есть», а здесь код набирают с нуля, и по клавиатуре это
                        // видно без слов. Заодно с экрана уходит синяя плашка, которая
                        // закрывала кадр и спорила со всем вокруг
                        IconButton(onClick = { manualCode = "" }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_keyboard),
                                contentDescription = manualEntryCd,
                                tint = Color.White
                            )
                        }
                    }
                }

                // Кратность показывается пузырьком, а не полосой с делениями: пальцы уже
                // заняты щипком, а знать нужно ровно одно - на какой объектив попал.
                LaunchedEffect(zoomShownAt) {
                    if (zoomShownAt == 0L) return@LaunchedEffect
                    zoomVisible = true
                    delay(ZOOM_BUBBLE_MS)
                    if (System.currentTimeMillis() - zoomShownAt >= ZOOM_BUBBLE_MS) {
                        zoomVisible = false
                    }
                }

                AnimatedVisibility(
                    visible = zoomVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = padding.calculateBottomPadding() + 88.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = MaterialTheme.shapes.large
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = formatZoom(zoomRatio),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // У собирающей сессии список один, и кнопка одна - посередине, со счётчиком
                // второй строкой. Отдельная подпись «записано» рядом с кнопкой «записанные»
                // говорила одно и то же дважды и перекашивала низ экрана.
                if (loaded.collecting) {
                    Button(
                        onClick = {
                            navController.navigateOnce(Screen.CodesList.createRoute(sessionId, TYPE_SCANNED))
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = padding.calculateBottomPadding() + 8.dp)
                            .fillMaxWidth(0.6f)
                            .height(64.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = collectedButtonText,
                                maxLines = 1,
                                autoSize = BUTTON_TEXT_SIZE,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = loaded.scannedCodes.size.toString(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 4.dp,
                            end = 4.dp,
                            bottom = padding.calculateBottomPadding() + 8.dp
                        )
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Button(
                        onClick = {
                            navController.navigateOnce(Screen.CodesList.createRoute(sessionId, TYPE_SCANNED))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = scannedButtonText,
                            maxLines = 1,
                            // подпись занимает столько, сколько влезло, а не обрезается:
                            // «Неотсканирован...» - это не название кнопки
                            autoSize = BUTTON_TEXT_SIZE,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        text = progressText,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Button(
                        onClick = {
                            navController.navigateOnce(
                                Screen.CodesList.createRoute(sessionId, TYPE_NOT_SCANNED)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = notScannedButtonText,
                            maxLines = 1,
                            autoSize = BUTTON_TEXT_SIZE,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (!hasPermission) {
                    Text(
                        text = noCameraPermissionText,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = padding.calculateBottomPadding() + 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displayMedium
                    )
                }

                manualCode?.let { typed ->
                    // Набрать 31 знак кода маркировки руками невозможно, поэтому ввод здесь -
                    // это поиск по кодам сессии, а не набор. Неотмеченные идут первыми: их и
                    // ищут. Отмеченные остаются в списке, потому что коробка могла приехать
                    // дважды, и человеку нужно это увидеть, а не гадать, куда делся код.
                    val query = typed.trim()
                    val suggestions = remember(loaded, query) {
                        val scanned = loaded.scannedCodes.toHashSet()
                        loaded.codes
                            .asSequence()
                            .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
                            .sortedBy { it in scanned }
                            .take(MANUAL_SUGGESTIONS)
                            .map { it to (it in scanned) }
                            .toList()
                    }

                    AlertDialog(
                        onDismissRequest = { manualCode = null },
                        title = { Text(manualEntryTitle) },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = typed,
                                    onValueChange = { manualCode = it.filterNot { ch -> ch == '\n' } },
                                    label = { Text(manualEntryLabel) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = manualEntryHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (suggestions.isEmpty()) {
                                    Text(
                                        // в собирающей сессии отсутствие совпадений - не
                                        // тупик: набранный код в неё запишется
                                        text = if (loaded.collecting) {
                                            manualEntryWillRecord
                                        } else {
                                            manualEntryNoMatches
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                        items(suggestions, key = { it.first }) { (code, isScanned) ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        manualCode = null
                                                        // тот же путь, что у кадра с камеры,
                                                        // чтобы отклик был тем же самым
                                                        viewModel.submitManual(code)
                                                    }
                                                    .padding(vertical = 8.dp)
                                            ) {
                                                // Подсказки листаются вбок все разом, одним
                                                // состоянием на список: код длиннее экрана,
                                                // и по первым двум десяткам символов от
                                                // соседа его не отличить. У каждой строки
                                                // своя прокрутка означала бы, что строки
                                                // разъезжаются и сравнивать их уже нельзя.
                                                Text(
                                                    text = shortCode(code),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.horizontalScroll(
                                                        suggestionScroll
                                                    ),
                                                    color = if (isScanned) {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                if (isScanned) {
                                                    Text(
                                                        text = alreadyScannedLabel,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            // Диалог - это поиск по кодам сессии, и набранное в нём обычно
                            // кусок кода, а не код. В сверяющей сессии такой кусок безобиден:
                            // ответом будет «нет в этой сессии». В собирающей он записался бы
                            // в неё настоящим кодом, поэтому записать можно только то, что не
                            // совпало ни с чем, - и кнопка тогда так и называется.
                            val records = loaded.collecting && suggestions.isEmpty()
                            TextButton(
                                enabled = typed.isNotBlank() && (!loaded.collecting || records),
                                onClick = {
                                    manualCode = null
                                    viewModel.submitManual(typed)
                                }
                            ) { Text(if (records) manualEntryRecord else manualEntryConfirm) }
                        },
                        dismissButton = {
                            TextButton(onClick = { manualCode = null }) { Text(cancelText) }
                        }
                    )
                }

                // В режиме «по касанию» камера молчит, пока её не попросят, - без
                // подсказки молчание читалось бы как поломка
                if (scanOnTap && banner == null && hasPermission) {
                    Text(
                        text = stringResource(id = R.string.scan_on_tap_hint),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = padding.calculateTopPadding() + 64.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = MaterialTheme.shapes.large
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // FEEDBACK OVERLAY (animated)
                AnimatedVisibility(
                    visible = banner != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = padding.calculateTopPadding() + 64.dp)
                ) {
                    if (banner != null && bannerText != null && bannerColor != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .wrapContentHeight()
                                .background(color = bannerColor.container, shape = MaterialTheme.shapes.small)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = bannerText,
                                    color = bannerColor.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // сам код под ответом: подпись под Data Matrix печатают не
                                // всегда, а глазами он не читается - без строки на экране
                                // сверить нечем, и особенно когда код оказался чужим
                                if (showScanCode) {
                                    banner.code.let { scanned ->
                                        // нажатие кладёт в буфер код целиком, а не
                                        // укороченный: в учётную систему нужен весь.
                                        // clickable съедает касание, и наводка фокуса,
                                        // висящая на видоискателе, его не увидит
                                        Row(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .clip(MaterialTheme.shapes.extraSmall)
                                                .clickable { copyScanned(scanned) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = shortCode(scanned),
                                                color = bannerColor.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                painter = painterResource(
                                                    id = R.drawable.ic_content_copy
                                                ),
                                                contentDescription = copyCodeCd,
                                                tint = bannerColor.content,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                // срок и партия - прямо в ответе: смотрят на них в момент
                                // сканирования, а не потом в списке
                                banner.code.let { scanned ->
                                    CodeMetaLine(
                                        code = scanned,
                                        showKind = false,
                                        showFields = showCodeFields,
                                        color = bannerColor.content,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
