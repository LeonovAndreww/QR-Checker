package com.datools.qrchecker.ui.scan

import com.datools.qrchecker.R
import com.datools.qrchecker.ui.resolve
import androidx.activity.result.PickVisualMediaRequest
import com.datools.qrchecker.Screen
import com.datools.qrchecker.TYPE_NOT_SCANNED
import com.datools.qrchecker.TYPE_SCANNED
import com.datools.qrchecker.codes.shortCode
import com.datools.qrchecker.files.shareSessionFile
import com.datools.qrchecker.navigateOnce
import com.datools.qrchecker.settings.AppSettings
import com.datools.qrchecker.ui.CodeMetaLine
import com.datools.qrchecker.ui.theme.OnColor
import com.datools.qrchecker.ui.theme.SignalAmber
import com.datools.qrchecker.ui.theme.SignalGreen
import com.datools.qrchecker.ui.theme.SignalRed
import com.datools.qrchecker.ui.theme.accents
import com.datools.qrchecker.util.formatTimeAgo
import com.datools.qrchecker.util.Outcome
import com.datools.qrchecker.util.rememberFeedback
import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


private const val TAG = "Scan"

/** Suggestions shown in manual entry; a phone screen does not fit more. */
private const val MANUAL_SUGGESTIONS = 30

/** How long the zoom bubble stays after a pinch ends. */
private const val ZOOM_BUBBLE_MS = 1200L

/** "1x", "2.5x", "0.6x", the way camera apps label zoom. */
internal fun formatZoom(ratio: Float): String {
    val rounded = (ratio * 10f).roundToInt() / 10f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    return "${text}x"
}

/** How long the screen stays on without a single code in view. */
private const val SCREEN_AWAKE_IDLE_MS = 5 * 60_000L
private const val SCREEN_AWAKE_CHECK_MS = 5_000L

/** Height of the dark gradient under the top and bottom controls. */
private val CONTROL_SCRIM = 140.dp

/**
 * The bottom button labels shrink to fit: "Неотсканированные" does not fit a narrow
 * screen at any fixed size, and a truncated label is unreadable.
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
    // Back from the code lists or the edit screen: marks may have been removed there. The
    // composition is rebuilt on the way back, so this also runs on every return.
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { lifecycleState ->
            if (lifecycleState == Lifecycle.State.RESUMED) viewModel.reload()
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
    val undoText = stringResource(id = R.string.undo_mark)
    val fromImageText = stringResource(id = R.string.manual_entry_from_image)
    val chooseImageCodeTitle = stringResource(id = R.string.image_choose_code)

    // the system photo picker: no storage permission, the user grants this one image
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.readImage(uri) }
    LaunchedEffect(viewModel, context) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message.resolve(context))
        }
    }

    fun colorOf(kind: AnswerKind): OnColor = when (kind) {
        AnswerKind.MARKED, AnswerKind.RECORDED -> accents.success
        AnswerKind.REPEAT -> accents.warning
        AnswerKind.FOREIGN, AnswerKind.SAVE_FAILED -> accents.danger
    }

    val banner = state.banner
    val bannerColor = banner?.let { colorOf(it.kind) }

    fun signalOf(kind: AnswerKind): Color = when (kind) {
        AnswerKind.MARKED, AnswerKind.RECORDED -> SignalGreen
        AnswerKind.REPEAT -> SignalAmber
        AnswerKind.FOREIGN, AnswerKind.SAVE_FAILED -> SignalRed
    }
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
        // The camera goes under the system bars, the controls do not. Side insets
        // matter in landscape, where the navigation bar sits on one side.
        val layoutDirection = LocalLayoutDirection.current
        val sides = Modifier.padding(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection)
        )
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(
                    highlightColor = banner?.let { signalOf(it.kind) },
                    // The colour of the last answer to this code, not its state: by state a
                    // code scanned yesterday would turn green once the orange "already
                    // scanned" banner faded, as if it had just been marked.
                    colorOf = { code ->
                        val current = state.session
                        state.answered[code]?.let { signalOf(it) } ?: when {
                            current == null -> null
                            code !in current.codes -> SignalRed
                            code in current.scannedCodes -> SignalAmber
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

                // Dark gradients under the controls: the live frame can be any brightness,
                // so the text on it is always white over a predictable background.
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

            // all controls sit inside the side insets; one wrapper so nothing forgets them
            Box(modifier = Modifier.fillMaxSize().then(sides)) {
                // Back on the left, actions on the right, the title in between and aligned
                // to the start, as in a regular app bar. A centred title had to keep clear
                // of the wider side and was cut off while space was left on the other.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding() + 4.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        // same as system back: to the session list, even when the user
                        // came here from the create screen
                        onClick = {
                            navController.navigateOnce(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backCd,
                            tint = Color.White
                        )
                    }

                    Text(
                        loaded.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // between shelves and inside a truck it is dark, and Data Matrix
                    // does not read without light; shown only if there is a flash
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

                    IconButton(onClick = {
                        scope.launch {
                            try {
                                context.startActivity(shareSessionFile(context, loaded))
                            } catch (c: CancellationException) {
                                throw c
                            } catch (t: Throwable) {
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

                    IconButton(onClick = { manualCode = "" }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_keyboard),
                            contentDescription = manualEntryCd,
                            tint = Color.White
                        )
                    }
                }

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

                // the lists in the colours of their answers: found by colour, not by reading
                val scannedColors = ButtonDefaults.buttonColors(
                    containerColor = accents.success.container,
                    contentColor = accents.success.content
                )
                // a collecting session has one list, so one centred button with the count
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
                        shape = MaterialTheme.shapes.small,
                        colors = scannedColors
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
                        shape = MaterialTheme.shapes.small,
                        colors = scannedColors
                    ) {
                        Text(
                            text = scannedButtonText,
                            maxLines = 1,
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
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accents.danger.container,
                            contentColor = accents.danger.content
                        )
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
                    // Nobody types a 31-character marking code, so manual entry is a search
                    // over the session's codes. Unscanned codes come first; scanned ones stay
                    // listed, so a box that arrived twice is visibly a repeat.
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
                                // when the label is too damaged for the camera, a sharper
                                // photo of it, or of the waybill, may still be readable
                                TextButton(
                                    onClick = {
                                        manualCode = null
                                        imagePicker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 0.dp)
                                ) {
                                    Text(fromImageText)
                                }

                                if (suggestions.isEmpty()) {
                                    Text(
                                        // in a collecting session a code with no match is recorded
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
                                                        viewModel.submitManual(code)
                                                    }
                                                    .padding(vertical = 8.dp)
                                            ) {
                                                // One scroll state for all rows: codes are
                                                // longer than the screen, and rows scrolled
                                                // apart could not be compared.
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
                            // What is typed is usually a fragment. In a checking session a
                            // fragment just answers "not in this session"; in a collecting
                            // one it would be recorded as a code, so only a text matching
                            // nothing can be recorded, and the button says so.
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

                if (state.readingImage) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.imageChoices?.let { choices ->
                    AlertDialog(
                        onDismissRequest = { viewModel.chooseImageCode(null) },
                        title = { Text(chooseImageCodeTitle) },
                        text = {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(choices, key = { it }) { code ->
                                    Text(
                                        text = shortCode(code),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.chooseImageCode(code) }
                                            .padding(vertical = 12.dp)
                                    )
                                    HorizontalDivider()
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { viewModel.chooseImageCode(null) }) {
                                Text(cancelText)
                            }
                        }
                    )
                }

                // without a hint, a camera that waits for a tap looks broken
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
                        // Centred, like the answer it is. The actions are rare, so they are
                        // small icons on the code line rather than buttons of their own.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .wrapContentHeight()
                                .background(color = bannerColor.container, shape = MaterialTheme.shapes.small)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val actions: @Composable () -> Unit = {
                                BannerAction(
                                    painter = painterResource(id = R.drawable.ic_content_copy),
                                    description = copyCodeCd,
                                    tint = bannerColor.content,
                                    // copies the full code, not the shortened one
                                    onClick = { copyScanned(banner.code) }
                                )
                                // a code scanned by mistake, now or earlier, is taken back
                                // right here, without looking for it in the list
                                if (banner.undoable) {
                                    BannerAction(
                                        painter = rememberVectorPainter(Icons.Default.Delete),
                                        description = undoText,
                                        tint = bannerColor.content,
                                        onClick = {
                                            feel(Outcome.ACTION)
                                            viewModel.undo()
                                        }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = bannerText,
                                    color = bannerColor.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                // with the code hidden, the actions follow the answer
                                if (!showScanCode) actions()
                            }
                            // the printed text under a Data Matrix is often missing,
                            // and the code cannot be read by eye
                            if (showScanCode) {
                                Row(
                                    modifier = Modifier.padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = shortCode(banner.code),
                                        color = bannerColor.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    actions()
                                }
                            }
                            // expiry and batch are checked at the moment of scanning
                            CodeMetaLine(
                                code = banner.code,
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

/**
 * A small action on the banner, as tall as a line of text. The touch area is wider than
 * the icon, so it is still easy to hit.
 */
@Composable
private fun BannerAction(
    painter: Painter,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(width = 32.dp, height = 24.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
