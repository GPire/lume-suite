package com.weenas.castbay.ui.screen

import com.weenas.castbay.ui.usingKeys
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import com.weenas.castbay.util.Log
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import com.weenas.castbay.service.DacpClient
import com.weenas.castbay.service.NowPlaying
import com.weenas.castbay.service.StatsFormat
import com.weenas.castbay.service.Lyrics
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.weenas.castbay.R
import com.weenas.castbay.ui.mirroringLabel
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.BrandSlogan
import com.weenas.castbay.ui.BrandTitle
import com.weenas.castbay.ui.CastingBadge
import com.weenas.castbay.ui.VolumeIndicator
import com.weenas.castbay.ui.Backdrop
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import com.weenas.castbay.ui.MediaIcons
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.weenas.castbay.ui.LocalBackgroundImage
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import com.weenas.castbay.ui.PaintingWall
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.weenas.castbay.service.PictureLayout
import com.weenas.castbay.service.ReceiverSettings
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import com.weenas.castbay.service.NetworkStatus
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.service.AirPlayConnectionState
import com.weenas.castbay.viewmodel.AirPlayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorScreen(viewModel: AirPlayViewModel) {
    val state by viewModel.state.collectAsState()

    // Mirroring and AirPlay video own the whole screen: no app bar, no padding, just the picture.
    val stream = state.streamInfo
    if (state.connectionState == AirPlayConnectionState.Streaming &&
        (stream.isVideoPlayback || stream.isAudioOnly || stream.isMirroring)
    ) {
        val settings by viewModel.settings.collectAsState()
        val kind = when {
            stream.isVideoPlayback -> StreamKind.VIDEO
            stream.isAudioOnly -> StreamKind.AUDIO
            else -> StreamKind.MIRRORING
        }
        var menuOpen by remember(kind) { mutableStateOf(false) }
        // Where the quick menu is, so a tap on it doesn't count as a tap on the picture.
        var menuBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        // The playing content takes D-pad focus, and gets it back when the quick menu closes.
        val contentFocus = remember(kind) { FocusRequester() }
        LaunchedEffect(kind, menuOpen) {
            if (!menuOpen) contentFocus.requestFocus()
        }
        BackHandler(enabled = menuOpen) { menuOpen = false }
        // Back leaves casting only when pressed twice, so a stray press doesn't cut it off.
        var backArmed by remember(kind) { mutableStateOf(false) }
        BackHandler(enabled = !menuOpen) {
            if (backArmed) viewModel.endCasting() else backArmed = true
        }
        LaunchedEffect(backArmed) {
            if (backArmed) {
                delay(BACK_AGAIN_WINDOW_MS)
                backArmed = false
            }
        }
        // Lyrics for the music screen (here, as the screen saver needs to know whether any are showing).
        val song = stream.nowPlaying
        // Waits a moment before looking up: the length usually arrives after the title.
        val lyrics by produceState<LyricsLookup>(LyricsLookup.Pending, kind, settings.showLyrics, song.title, song.artist, song.durationSec.toInt()) {
            value = LyricsLookup.Pending
            val title = song.title
            if (kind != StreamKind.AUDIO || !settings.showLyrics) return@produceState
            if (title.isNullOrBlank()) {
                value = LyricsLookup.None
                return@produceState
            }
            delay(LYRICS_LOOKUP_DELAY_MS)
            var found = withContext(Dispatchers.IO) {
                viewModel.findLyrics(title, song.artist, song.album, song.durationSec)
            }
            // The site answers with a 503 now and then (seen on two songs in a row): ask again a few times while the song plays.
            var attempt = 0
            while (found == null && viewModel.lyricsLookupFailed() && attempt < LYRICS_RETRY_DELAYS_MS.size) {
                delay(LYRICS_RETRY_DELAYS_MS[attempt++])
                found = withContext(Dispatchers.IO) { viewModel.findLyrics(title, song.artist, song.album, song.durationSec) }
            }
            // Words that run well past the song are another edit of it: wrong at every line, better none than lines that drift away.
            val fits = found != null && (song.durationSec <= 0 || (found.lines.lastOrNull()?.timeSec ?: 0.0) <= song.durationSec + 8.0)
            value = if (found != null && fits) LyricsLookup.Found(found) else LyricsLookup.None
        }
        // The music screen's screen saver; the quick menu or another phone casting wakes it.
        // Scrolling lyrics don't burn in, and someone is probably reading them.
        val saver = key(stream.sender, stream.isDlna) {
            val showingLyrics = lyrics is LyricsLookup.Found
            rememberMusicScreenSaver(kind == StreamKind.AUDIO && !menuOpen && !showingLyrics, settings.screenSaver, viewModel)
        }
        val saverShift = animatedSaverShift(saver)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // Down and Menu are free during playback (left/right/OK control it).
                    if (menuOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key != Key.DirectionDown && event.key != Key.Menu) return@onPreviewKeyEvent false
                    menuOpen = true
                    true
                }
                // Touch screens (car head units have no Down key): a tap opens or closes the
                // quick menu. Video and mirroring have no buttons of their own, so every tap
                // there counts (seen first, whatever the player's view does with it) except one
                // on the menu itself; on the music screen, only taps its buttons don't take.
                .pointerInput(kind) {
                    val pass = if (kind == StreamKind.AUDIO) PointerEventPass.Final else PointerEventPass.Initial
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
                        var up: PointerInputChange? = null
                        while (true) {
                            val change = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: break
                            // A drag (or a slide on the seek bar) isn't a tap.
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                            if (!change.pressed) {
                                up = change
                                break
                            }
                        }
                        val onMenu = menuOpen && menuBounds?.contains(down.position) == true
                        val taken = kind == StreamKind.AUDIO && (up?.isConsumed ?: true)
                        val tap = "Tap ${if (up == null) "cancelled" else "up"}, menu ${if (menuOpen) "open" else "closed"}" +
                            (if (onMenu) ", on the menu" else "") + (if (taken) ", taken by a control" else "")
                        Log.d("CastBayTouch", tap)
                        com.weenas.castbay.util.Diagnostics.record("touch", "$tap (${kind.name.lowercase()})")
                        if (up != null && !onMenu && !taken) menuOpen = !menuOpen
                    }
                }
        ) {
            val contentModifier = Modifier.focusRequester(contentFocus)
            when (kind) {
                StreamKind.VIDEO -> VideoPlayback(
                    viewModel = viewModel, player = viewModel.videoPlayer, pictureMode = settings.pictureMode,
                    awaitingNext = stream.awaitingNext && !stream.awaitingNextQuietly,
                    modifier = contentModifier
                )
                StreamKind.AUDIO -> {
                    AudioPlayback(
                        nowPlaying = song,
                        onCommand = viewModel::remoteControl,
                        onSkip = viewModel::skipMusic,
                        canChangeTrack = !stream.isDlna,
                        onSeek = if (stream.isDlna) viewModel::seekMusic else null,
                        lyrics = (lyrics as? LyricsLookup.Found)?.lyrics,
                        lyricsEnabled = settings.showLyrics,
                        lyricsDelaySec = settings.lyricsDelayTenths / 10.0,
                        noLyrics = lyrics == LyricsLookup.None,
                        modifier = contentModifier.offset { saverShift }
                    )
                }
                StreamKind.MIRRORING -> MirroringVideo(viewModel = viewModel, streamInfo = stream, pictureMode = settings.pictureMode, modifier = contentModifier)
            }
            if (kind == StreamKind.AUDIO) {
                // As wide as the cover below it, so a long phone name scrolls rather than
                // running into the title beside the cover.
                CastingBadge(stream, musicCoverSize(), Modifier.align(Alignment.TopStart).offset { saverShift }.padding(start = 56.dp, top = 20.dp))
                ScreenSaverOverlay(saver)
            }
            val senderVolume by viewModel.senderVolume.collectAsState()
            VolumeIndicator(senderVolume, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 40.dp))
            if (settings.showStats) {
                // On the music screen the top left holds the casting badge.
                val corner = if (kind == StreamKind.AUDIO) Alignment.TopEnd else Alignment.TopStart
                StatsOverlay(viewModel, Modifier.align(corner).padding(24.dp))
            }
            if (backArmed) {
                Text(
                    stringResource(R.string.press_back_again),
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                )
            }
            if (menuOpen) {
                QuickMenu(
                    viewModel = viewModel,
                    hasPicture = kind != StreamKind.AUDIO,
                    player = if (kind == StreamKind.VIDEO) viewModel.videoPlayer else null,
                    onDismiss = { menuOpen = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 48.dp, vertical = 32.dp)
                        .onGloballyPositioned { menuBounds = it.boundsInParent() }
                )
            }
        }
        return
    }

    // Back twice on the home screen stops receiving and quits; Home leaves it receiving.
    val activity = LocalContext.current as? android.app.Activity
    var quitArmed by remember { mutableStateOf(false) }
    // Quitting: "Stopping…" while the app closes, rather than "Starting…" as the stopped
    // receiver goes idle.
    var quitting by remember { mutableStateOf(false) }
    BackHandler {
        if (quitArmed) {
            quitting = true
            activity?.finish()
            viewModel.stopServer()
        } else {
            quitArmed = true
        }
    }
    LaunchedEffect(quitArmed) {
        if (quitArmed) {
            delay(BACK_AGAIN_WINDOW_MS)
            quitArmed = false
        }
    }
    // No app bar: the name is on screen already, and Settings sits beside the main button.
    AppBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (quitArmed) {
                // Below the home buttons (a 540 dp tall screen leaves room only near the edge),
                // and over them.
                Text(
                    stringResource(R.string.press_back_to_quit),
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .zIndex(1f)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                )
            }
            when (state.connectionState) {
                AirPlayConnectionState.Idle -> IdleScreen(viewModel = viewModel, stopping = quitting)
                AirPlayConnectionState.Discovering -> DiscoveringScreen(
                    viewModel = viewModel,
                    lastError = state.errorMessage
                )
                // Same screen as Idle ("Starting…"), so starting doesn't flash an extra screen.
                AirPlayConnectionState.Registering -> IdleScreen(viewModel = viewModel)
                AirPlayConnectionState.AdvertisingOnly -> AdvertisingOnlyScreen(
                    onStop = { viewModel.stopServer() }
                )
                AirPlayConnectionState.Connecting -> ConnectingScreen()
                AirPlayConnectionState.Connected -> ConnectedScreen(
                    viewModel = viewModel,
                    streamInfo = state.streamInfo
                )
                AirPlayConnectionState.Streaming -> StreamingScreen(
                    viewModel = viewModel,
                    streamInfo = state.streamInfo
                )
                AirPlayConnectionState.Disconnected -> DisconnectedScreen(
                    onStart = { viewModel.startServer() }
                )
                AirPlayConnectionState.Error -> ErrorScreen(
                    error = state.errorMessage ?: stringResource(R.string.unknown_error),
                    onRetry = { viewModel.startServer() },
                    onDiagnostics = { viewModel.navigateToDiagnostics() }
                )
            }
        }
    }
}


/** Before the receiver is up: starting as the app opens, or [stopping] as it quits. */
@Composable
fun IdleScreen(viewModel: AirPlayViewModel, stopping: Boolean = false) {
    HomeScreen(viewModel) {
        // As "Waiting for a connection…", which it turns into a moment later.
        StatusLine(stringResource(if (stopping) R.string.stopping else R.string.starting), ready = false)
    }
}

@Composable
fun DiscoveringScreen(viewModel: AirPlayViewModel, lastError: String? = null) {
    HomeScreen(viewModel) {
        // How to cast from each device is on the Help screen.
        // Text's default style has a fixed 24 sp line height, so larger text that may wrap sets
        // its own (in sp: an em line height in the theme crashed text fields' label animation).
        StatusLine(stringResource(R.string.waiting_title))
        if (lastError != null) {
            // Why the last AirPlay video stopped, e.g. the TV couldn't reach the video site.
            Spacer(modifier = Modifier.height(16.dp))
            Text(lastError, color = Color(0xFFFFB4AB), fontSize = 18.sp)
        }
    }
}

/** Room for the home screen's status, so the brand and buttons stay put as it changes. */
private val HOME_STATUS_MIN_HEIGHT = 190.dp
/** Less of it on short screens (see [LocalShortHome]). */
private val SHORT_HOME_STATUS_MIN_HEIGHT = 88.dp
private val SHORT_HOME_HEIGHT = 500.dp

/**
 * The waiting screen in every receiver state, redesigned as a calm Apple TV-style page: a painting on the wall,
 * and one glass panel with the AirPlay mark, this TV's name big, its state, the three things to do on the phone
 * and, small, the settings buttons. The technical table (network, address, modes) is now one quiet line here and
 * in full under About, so nothing a first-time user does not need competes for attention.
 */
@Composable
private fun HomeScreen(viewModel: AirPlayViewModel, status: @Composable ColumnScope.() -> Unit) {
    val crashNotice by viewModel.crashNotice.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val network by viewModel.network.collectAsState()
    val name = settings.advertisedName
    val light = com.weenas.castbay.ui.Perf.light(androidx.compose.ui.platform.LocalContext.current)
    var entered by remember { mutableStateOf(light) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(if (entered) 1f else 0f, tween(if (light) 0 else 520, easing = FastOutSlowInEasing), label = "panelEnter")
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        IdleWall()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val short = maxHeight < SHORT_HOME_HEIGHT
            val shape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .align(if (wide) Alignment.CenterStart else Alignment.Center)
                    .padding(horizontal = if (wide) 88.dp else 24.dp, vertical = 32.dp)
                    .widthIn(max = 680.dp)
                    .graphicsLayer { alpha = enter; translationY = (1f - enter) * 28.dp.toPx() }
                    .verticalScroll(rememberScrollState())
                    .clip(shape)
                    .background(Color(0x66000000))
                    // A lit top edge fading to nothing: glass catching light, as on Apple's panels.
                    .border(1.dp, Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x12FFFFFF))), shape)
                    .padding(horizontal = if (short) 24.dp else 32.dp, vertical = if (short) 16.dp else 22.dp)
            ) {
                BrandTitle(modifier = Modifier.height(40.dp), compact = true)
                Spacer(modifier = Modifier.height(if (short) 6.dp else 10.dp))
                Text(name, color = Color.White, fontSize = if (short) 26.sp else 32.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, lineHeight = if (short) 32.sp else 38.sp, modifier = Modifier.marquee())
                Spacer(modifier = Modifier.height(6.dp))
                // A crash to ask about takes the status's place for a moment, so nothing moves.
                val notice = crashNotice
                if (notice != null) CrashNoticeCard(notice, viewModel) else Column(content = status)
                if (notice == null && !short) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HomeStep("1", stringResource(R.string.home_step_wifi_title), stringResource(R.string.home_step_wifi), 0, light)
                    HomeStep("2", stringResource(R.string.home_step_music_title), stringResource(R.string.home_step_music, name), 1, light)
                    HomeStep("3", stringResource(R.string.home_step_screen_title), stringResource(R.string.home_step_screen, name), 2, light, last = true)
                }
                Spacer(modifier = Modifier.height(if (short) 10.dp else 16.dp))
                HomeButtons(viewModel)
                Spacer(modifier = Modifier.height(10.dp))
                // The Wi-Fi band and how good the signal is: the usual cause of AirPlay stutter, said before it happens.
                val context = androidx.compose.ui.platform.LocalContext.current
                var wifi by remember { mutableStateOf<com.weenas.castbay.ui.WifiQuality?>(null) }
                LaunchedEffect(network.type) {
                    while (network.type == NetworkStatus.Type.WIFI) { wifi = com.weenas.castbay.ui.WifiQuality.read(context); delay(10_000) }
                    wifi = null
                }
                val q = wifi
                val bandText = when (q?.band) {
                    com.weenas.castbay.ui.WifiQuality.Band.GHZ_2 -> "2,4 GHz"
                    com.weenas.castbay.ui.WifiQuality.Band.GHZ_5 -> "5 GHz"
                    com.weenas.castbay.ui.WifiQuality.Band.GHZ_6 -> "6 GHz"
                    null -> null
                }
                val levelText = when (q?.level) {
                    com.weenas.castbay.ui.WifiQuality.Level.GREAT -> stringResource(R.string.wifi_great)
                    com.weenas.castbay.ui.WifiQuality.Level.GOOD -> stringResource(R.string.wifi_good)
                    com.weenas.castbay.ui.WifiQuality.Level.WEAK -> stringResource(R.string.wifi_weak)
                    null -> null
                }
                Text(
                    networkLabel(network) + (bandText?.let { " $it" } ?: "") + (levelText?.let { "  \u00B7  $it" } ?: "") +
                        "  \u00B7  " + network.ipv4.joinToString(", ").ifEmpty { "\u2014" },
                    color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp, maxLines = 1, modifier = Modifier.marquee()
                )
                // A guest network is the commonest reason an iPhone cannot see the TV: routers keep guests apart from
                // each other. The name only shows with the location permission, so this speaks only when it is known.
                val guest = network.ssid?.let { Regex("guest|ospit|invitad|gast|invit|convidad|visit", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true
                val tip = when {
                    guest -> R.string.wifi_tip_guest
                    q?.level == com.weenas.castbay.ui.WifiQuality.Level.WEAK -> R.string.wifi_tip_weak
                    q?.band == com.weenas.castbay.ui.WifiQuality.Band.GHZ_2 -> R.string.wifi_tip_24
                    else -> null
                }
                if (tip != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(tip), color = Color(0xFFFFD60A).copy(alpha = 0.85f), fontSize = 15.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

/** One of the three steps: a white numbered disc, a title and what to do. */
@Composable
private fun HomeStep(number: String, title: String, detail: String, index: Int = 0, light: Boolean = false, last: Boolean = false) {
    // Each step settles a beat after the one above (90 ms apart), so the eye reads them in order.
    var shown by remember { mutableStateOf(light) }
    LaunchedEffect(Unit) { delay(260L + index * 90L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(if (light) 0 else 380, easing = FastOutSlowInEasing), label = "stepIn")
    Row(modifier = Modifier.padding(vertical = 5.dp).graphicsLayer { alpha = a; translationX = (1f - a) * 14.dp.toPx() }, verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) { Text(number, color = Color(0xFF111114), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
            Text(detail, color = Color.White.copy(alpha = 0.78f), fontSize = 17.sp, lineHeight = 22.sp, maxLines = 2)
        }
    }
}

/** The state line: a dot that breathes while waiting (calm, slow, never blinking), then the text. */
@Composable
private fun StatusLine(text: String, ready: Boolean = true) {
    // Breathes three times when the screen appears, then rests. A forever-animation makes this TV's GPU
    // redraw the whole 1080p scene every frame (measured: ~97% late frames on a Fire TV Stick); at rest the
    // screen now draws nothing. Read inside graphicsLayer so only the dot repaints.
    val light = com.weenas.castbay.ui.Perf.light(androidx.compose.ui.platform.LocalContext.current)
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        if (!light) repeat(3) {
            pulse.animateTo(0.45f, tween(900, easing = FastOutSlowInEasing))
            pulse.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).graphicsLayer { alpha = pulse.value }.clip(CircleShape).background(if (ready) Color(0xFF34C759) else Color(0xFFFFB020)))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 21.sp, lineHeight = 28.sp)
    }
}

/** The AirPlay mark (screen outline open at the bottom, triangle below), drawn as vectors. */
@Composable
private fun AirPlayGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier) {
        val k = size.minDimension / 100f
        val screen = androidx.compose.ui.graphics.Path().apply {
            moveTo(28f * k, 75f * k); lineTo(12f * k, 75f * k); lineTo(12f * k, 14f * k)
            lineTo(88f * k, 14f * k); lineTo(88f * k, 75f * k); lineTo(72f * k, 75f * k)
        }
        drawPath(screen, color, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 7f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val tri = androidx.compose.ui.graphics.Path().apply {
            moveTo(50f * k, 58f * k); lineTo(84f * k, 92f * k); lineTo(16f * k, 92f * k); close()
        }
        drawPath(tri, color)
    }
}

/** The waiting screen's wall: the shared painting wall, darker on the left where the panel sits. */
@Composable
private fun IdleWall() {
    PaintingWall()
    // Dark where the panel sits so the text reads, clear on the right where the painting shows.
    Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0x66000000), Color(0x1A000000)))))
    Box(modifier = Modifier.fillMaxSize().background(veilColor().copy(alpha = 0.22f)))
}

/**
 * After a crash in the last run (with Send error reports off): asks whether to send a problem
 * report, then says how that went. In the status's place; Up from the buttons reaches it.
 */
@Composable
private fun CrashNoticeCard(notice: AirPlayViewModel.CrashNotice, viewModel: AirPlayViewModel) {
    Column(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            when (notice) {
                AirPlayViewModel.CrashNotice.Ask -> stringResource(R.string.crash_ask)
                AirPlayViewModel.CrashNotice.Sending -> stringResource(R.string.crash_sending)
                is AirPlayViewModel.CrashNotice.Sent -> stringResource(R.string.crash_sent, notice.id)
                AirPlayViewModel.CrashNotice.Failed -> stringResource(R.string.crash_failed)
            },
            fontSize = 16.sp,
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        CompositionLocalProvider(LocalHomeButtonMinWidth provides 96.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (notice) {
                    AirPlayViewModel.CrashNotice.Ask -> {
                        HomeButton(stringResource(R.string.crash_send), onClick = { viewModel.sendCrashReport() })
                        HomeButton(stringResource(R.string.crash_dismiss), muted = true, onClick = { viewModel.dismissCrashNotice() })
                    }
                    AirPlayViewModel.CrashNotice.Sending -> {}
                    else -> HomeButton(stringResource(R.string.crash_ok), muted = true, onClick = { viewModel.dismissCrashNotice() })
                }
            }
        }
    }
}

/** The home screen's buttons: Settings, Help and About; the one last opened has the focus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeButtons(viewModel: AirPlayViewModel) {
    // Narrower than elsewhere so three fit beside the info panel; they wrap if they still don't.
    CompositionLocalProvider(LocalHomeButtonMinWidth provides 120.dp) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val focused = viewModel.homeFocus
            HomeButton(
                stringResource(R.string.settings),
                if (focused == "settings") Modifier.initialFocus() else Modifier,
                onClick = { viewModel.navigateToSettings() }
            )
            HomeButton(
                stringResource(R.string.help),
                if (focused == "help") Modifier.initialFocus() else Modifier,
                onClick = { viewModel.navigateToHelp() }
            )
            if (android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) {
                HomeButton(
                    stringResource(R.string.health_button),
                    if (focused == "health") Modifier.initialFocus() else Modifier,
                    onClick = { viewModel.navigateToHealth() }
                )
            }
            val update by viewModel.update.collectAsState()
            HomeButton(
                stringResource(R.string.about),
                if (focused == "about") Modifier.initialFocus() else Modifier,
                badge = update != null,
                onClick = { viewModel.navigateToAbout() }
            )
        }
    }
}

/** The update dot on About. */
private val BADGE_COLOR = Color(0xFFFF453A)

private val LocalHomeButtonMinWidth = staticCompositionLocalOf { 160.dp }

/** A home/Settings button. The focused one gets a white outline, visible from the sofa. */
@Composable
fun HomeButton(
    text: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    /** A dot at the top right, e.g. on About when a newer version is out. */
    badge: Boolean = false,
    onClick: () -> Unit
) {
    // Read from the focus state itself: a screen that focuses the button as it opens does so
    // before an interaction-source collector starts, and the outline was missing.
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "buttonScale")
    Button(
        onClick = onClick,
        // Frosted glass at rest; a solid white pill with dark text when focused, as on Apple TV.
        // Muted: the same glass, quieter, e.g. a switch that is off.
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Color.White else Color.White.copy(alpha = if (muted) 0.08f else 0.16f),
            contentColor = if (focused) Color(0xFF111114) else Color.White.copy(alpha = if (muted) 0.6f else 1f)
        ),
        shape = RoundedCornerShape(50),
        border = null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 9.dp),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                if (focused) { shadowElevation = 18.dp.toPx(); shape = RoundedCornerShape(50); clip = false
                    ambientShadowColor = Color.White; spotShadowColor = Color.White }
            }
            .onFocusChanged { focused = it.hasFocus }
            .widthIn(min = LocalHomeButtonMinWidth.current)
            .then(if (badge) Modifier.drawWithContent {
                drawContent()
                val radius = 6.dp.toPx()
                drawCircle(BADGE_COLOR, radius, androidx.compose.ui.geometry.Offset(size.width - radius * 2.4f, radius * 2.4f))
            } else Modifier)
    ) {
        Text(text, fontSize = 20.sp)
    }
}

/**
 * Whether the home screen is short (a car display or a phone on its side, under ~500 dp; a
 * 1080p TV is ~540 dp): its status area then takes less room, so the buttons stay in view.
 */
private val LocalShortHome = staticCompositionLocalOf { false }

/**
 * Status and actions beside the receiver info on wide screens (TVs are only ~540 dp tall at
 * 1080p), stacked and scrollable on narrow ones. Each side is centred, and scrolls where the
 * screen is too short for it, so the buttons are always reachable.
 */
@Composable
private fun HomeLayout(info: @Composable () -> Unit, primary: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            val padding = if (maxHeight < SHORT_HOME_HEIGHT) 12.dp else 24.dp
            val side = maxHeight - padding * 2
            CompositionLocalProvider(LocalShortHome provides (maxHeight < SHORT_HOME_HEIGHT)) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = padding)) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).heightIn(min = side),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        content = primary
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                    Box(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).heightIn(min = side),
                        contentAlignment = Alignment.Center
                    ) { info() }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                primary()
                Spacer(modifier = Modifier.height(32.dp))
                info()
            }
        }
    }
}

/**
 * What a user needs to cast to this TV at a glance: its AirPlay name, network and address,
 * and how the receiver is set up.
 */
@Composable
fun ReceiverInfo(viewModel: AirPlayViewModel) {
    val settings by viewModel.settings.collectAsState()
    val network by viewModel.network.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshNetwork()
    }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1FFFFFFF))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        InfoRow(stringResource(R.string.info_name), settings.advertisedName)
        InfoRow(stringResource(R.string.info_network), networkLabel(network))
        if (network.type == NetworkStatus.Type.WIFI && network.ssid == null) {
            if (!viewModel.canReadWifiName()) {
                // Android only reveals the Wi-Fi name to apps with the location permission.
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text(stringResource(R.string.show_wifi_name))
                }
            } else {
                Text(stringResource(R.string.wifi_name_needs_location), color = Color.Gray, fontSize = 14.sp)
            }
        }
        InfoRow(stringResource(R.string.info_ip), network.ipv4.joinToString(", ").ifEmpty { "—" })
        val mirroring = remember(settings) { viewModel.mirroringProfile(settings) }
        InfoRow(stringResource(R.string.info_mirroring), mirroringLabel(mirroring))
        InfoRow(stringResource(R.string.info_desktop), stringResource(if (settings.desktopEnabled) R.string.on else R.string.off))
        InfoRow(stringResource(R.string.info_dlna), stringResource(if (settings.dlnaEnabled) R.string.on else R.string.off))
        InfoRow(stringResource(R.string.info_access), accessLabel(settings.access))
        InfoRow(stringResource(R.string.info_second_device), stringResource(if (settings.allowTakeover) R.string.info_takes_over else R.string.info_refused))
        if (network.type == NetworkStatus.Type.NONE) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.no_network), color = Color(0xFFFFB4AB), fontSize = 16.sp)
        }
    }
}

@Composable
private fun networkLabel(network: NetworkStatus): String = when (network.type) {
    NetworkStatus.Type.WIFI -> network.ssid?.let { stringResource(R.string.network_wifi_named, it) } ?: stringResource(R.string.network_wifi)
    NetworkStatus.Type.ETHERNET -> stringResource(R.string.network_ethernet)
    NetworkStatus.Type.OTHER -> stringResource(R.string.network_other)
    NetworkStatus.Type.NONE -> stringResource(R.string.network_none)
}

/** One line each: text too long for it scrolls, so the panel's layout never shifts. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label, color = Color.Gray, fontSize = 18.sp, maxLines = 1,
            modifier = Modifier.weight(0.4f).padding(end = 12.dp).marquee()
        )
        Text(value, color = Color.White, fontSize = 18.sp, maxLines = 1, modifier = Modifier.weight(0.6f).marquee())
    }
}

@Composable
fun AdvertisingOnlyScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.advertising_only_title), color = Color.White, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.advertising_only_detail), color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStop, modifier = Modifier.initialFocus()) { Text(stringResource(R.string.action_stop)) }
    }
}

/**
 * The moment a phone connects: three rings spread from the AirPlay mark over a calm night, then the music or video
 * takes over. A calm profile (low RAM) keeps the rings still instead of drawing every frame.
 */
@Composable
fun ConnectingScreen() {
    val light = com.weenas.castbay.ui.Perf.light(androidx.compose.ui.platform.LocalContext.current)
    val transition = rememberInfiniteTransition(label = "connect")
    val moving by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = androidx.compose.animation.core.LinearEasing)), label = "rings")
    val phase = if (light) 0.4f else moving
    val appear by animateFloatAsState(1f, tween(500), label = "appear")
    Box(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B1433), Color(0xFF0B0910)))),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(520.dp)) {
            for (i in 0 until 3) {
                val p = (phase + i / 3f) % 1f
                drawCircle(
                    MUSIC_ACCENT.copy(alpha = (1f - p) * 0.5f * appear),
                    radius = 64.dp.toPx() + p * 190.dp.toPx(), center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = appear; translationY = (1f - appear) * 24f }) {
            AirPlayGlyph(Modifier.size(84.dp))
            Spacer(modifier = Modifier.height(28.dp))
            Text(stringResource(R.string.connecting), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ConnectedScreen(viewModel: AirPlayViewModel, streamInfo: com.weenas.castbay.service.StreamInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.connected), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.connected_from, streamInfo.sourceName), color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${streamInfo.videoWidth}x${streamInfo.videoHeight}", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_stop), fontSize = 20.sp)
        }
    }
}

@Composable
fun StreamingScreen(viewModel: AirPlayViewModel, streamInfo: com.weenas.castbay.service.StreamInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.streaming), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.streaming_source, streamInfo.sourceName), color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_stop), fontSize = 20.sp)
        }
    }
}

/**
 * Audio streaming (e.g. a music app): cover art, track details and progress. The remote's
 * OK and left/right keys control the sender; media keys reach it through the media session.
 */
@Composable
fun AudioPlayback(
    nowPlaying: NowPlaying,
    onCommand: (DacpClient.Command) -> Unit,
    /** Skips about ten seconds forward (true) or back. */
    onSkip: (Boolean) -> Unit,
    /** Previous/next track: AirPlay senders can; a DLNA sender's playlist is its own. */
    canChangeTrack: Boolean = true,
    /**
     * Moves to a time, for music played here (DLNA): the progress bar can then be dragged.
     * Null for AirPlay music, which the phone plays out and can't be asked to seek.
     */
    onSeek: ((Double) -> Unit)? = null,
    lyrics: Lyrics? = null,
    /**
     * Lyrics are on: their space is kept while a song's are looked up (or if it has none), so
     * the layout doesn't jump when a new song starts and again when its lyrics arrive.
     */
    lyricsEnabled: Boolean = false,
    /** Shifts the lyrics against the voice, in seconds (positive: later). */
    lyricsDelaySec: Double = 0.0,
    /** The song has no lyrics to show (not merely still being looked up). */
    noLyrics: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cover = remember(nowPlaying.coverArt) {
        nowPlaying.coverArt?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    // Ticks the progress between the sender's (infrequent) reports.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(nowPlaying, lyrics != null) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            // Lyrics lines change faster than the progress bar needs.
            delay(if (lyrics != null) 200 else 500)
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // The accent follows the cover, glides to the next song's colour and never jumps.
    var accentArgb by remember { mutableIntStateOf(MUSIC_ACCENT.toArgb()) }
    val accent by animateColorAsState(Color(accentArgb), tween(900), label = "accent")
    // The cover, blurred, behind everything.
    CompositionLocalProvider(LocalMusicAccent provides accent) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // The backdrop (image, veils, grain) is five full-screen layers; as one cached layer it costs a single textured quad per frame,
        // which is what lets the lyrics move smoothly on a stick with a weak GPU.
        Box(modifier = Modifier.fillMaxSize().graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)) {
            MusicBackdrop(nowPlaying.coverArt) { accentArgb = it }
        }
        // Centred as before; the cover only shrinks where the screen is too short for it to clear
        // the casting badge above it (540 dp tall TVs; 720 dp ones keep 400 dp).
        // At most 30% of the width too: on a 16:9 TV (960 dp wide) the height alone left a cover
        // over a third of the screen and the text squeezed; a wide car display looked right at ~30%.
        val coverSize = musicCoverSize()
        // Short screens (a car's, a phone on its side): everything starts below the casting
        // badge, which the title ran into when centred on the whole height.
        val short = LocalConfiguration.current.screenHeightDp < SHORT_MUSIC_HEIGHT_DP
        // Lyrics in their own tall column beside a smaller cover (Apple Music's layout); otherwise the usual centred one.
        val bigLyrics = lyrics != null && !short && onSeek == null
        if (bigLyrics && lyrics != null) {
            BigLyricsLayout(nowPlaying, cover, lyrics, now, lyricsDelaySec, onCommand, onSkip, canChangeTrack, modifier)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 88.dp, end = 88.dp, top = if (short) MUSIC_BADGE_CLEARANCE * 0.8f else 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Apple Music: the cover sits full size while the song plays, settles back with a little spring on
                // pause, and breathes very slowly while it plays (not on a calm profile).
                val calm = com.weenas.castbay.ui.Perf.light(androidx.compose.ui.platform.LocalContext.current)
                val restScale by animateFloatAsState(
                    if (nowPlaying.playing) 1f else 0.92f,
                    androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 220f), label = "coverScale"
                )
                val breathing = rememberInfiniteTransition(label = "breath")
                val breath by breathing.animateFloat(1f, 1.012f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
                val coverScale = restScale * (if (nowPlaying.playing && !calm) breath else 1f)
                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                        .size(coverSize)
                        .shadow(44.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    // Dissolves between covers instead of cutting, like the backdrop behind it.
                    Crossfade(targetState = cover, animationSpec = tween(600), label = "cover") { shown ->
                        if (shown != null) {
                            Image(bitmap = shown, contentDescription = stringResource(R.string.cover_art), contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Text("♪", fontSize = 120.sp, color = Color.Gray)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(56.dp))
                // On 540 dp tall TVs (1080p UI, e.g. the Sony) everything below barely fitted, from
                // the top edge to the bottom: fewer lyric lines and tighter gaps leave a margin.
                val compact = LocalConfiguration.current.screenHeightDp < COMPACT_MUSIC_HEIGHT_DP
                Column(modifier = Modifier.weight(1f)) {
                    // An explicit line height: the default text style's is 24 sp, so a long title wrapped
                    // onto a second line drawn over the first.
                    // One line each, scrolling when too long, so long names never push the layout
                    // (a TV screen is only ~540 dp tall).
                    val titleText = nowPlaying.title ?: stringResource(R.string.airplay_audio)
                    Crossfade(targetState = titleText, animationSpec = tween(450), label = "title") { shownTitle ->
                        Text(shownTitle, fontSize = if (short) 34.sp else 44.sp,
                            fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, modifier = Modifier.marquee())
                    }
                    nowPlaying.artist?.let { artistName ->
                        Spacer(modifier = Modifier.height(if (short) 4.dp else 10.dp))
                        Crossfade(targetState = artistName, animationSpec = tween(450), label = "artist") { shownArtist ->
                            Text(shownArtist, fontSize = if (short) 22.sp else 30.sp, color = MUSIC_TEXT_SECONDARY, maxLines = 1, modifier = Modifier.marquee())
                        }
                    }
                    // Short screens leave the album out, to keep the controls in view.
                    nowPlaying.album?.takeUnless { short }?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, fontSize = 24.sp, color = MUSIC_TEXT_TERTIARY, maxLines = 1, modifier = Modifier.marquee())
                    }
                    if (lyrics != null || lyricsEnabled) {
                        // Short screens show the line being sung alone.
                        val contextLines = if (short) 0 else if (compact) 1 else 2
                        Spacer(modifier = Modifier.height(if (short) 8.dp else if (compact) 14.dp else 20.dp))
                        if (lyrics != null) {
                            LyricsView(lyrics, nowPlaying.currentPositionSec(now), contextLines)
                        } else {
                            // Blank while looking up; a faint note once it's known there are none.
                            Box(modifier = Modifier.height(lyricsHeight(contextLines)), contentAlignment = Alignment.CenterStart) {
                                if (noLyrics) {
                                    Text(stringResource(R.string.lyrics_none), fontSize = 22.sp, color = Color.White.copy(alpha = 0.35f))
                                }
                            }
                        }
                    }
                    if (nowPlaying.durationSec > 0) {
                        // Where the thumb is while it is dragged (or moved with Left and Right).
                        var dragSec by remember { mutableStateOf<Float?>(null) }
                        val position = dragSec?.toDouble() ?: nowPlaying.currentPositionSec(now)
                        Spacer(modifier = Modifier.height(if (short) 12.dp else if (compact) 20.dp else 28.dp))
                        if (onSeek != null) {
                            // Music played here moves wherever the thumb is let go.
                            val maxSec = nowPlaying.durationSec.toFloat()
                            var focused by remember { mutableStateOf(false) }
                            fun seekToDrag() {
                                dragSec?.let { onSeek(it.toDouble()) }
                                dragSec = null
                            }
                            Slider(
                                value = position.toFloat().coerceIn(0f, maxSec),
                                onValueChange = { dragSec = it },
                                onValueChangeFinished = ::seekToDrag,
                                valueRange = 0f..maxSec,
                                colors = SliderDefaults.colors(
                                    thumbColor = if (focused) Color.White else accent,
                                    activeTrackColor = accent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { focused = it.hasFocus }
                                    .seekKeys(1f, maxSec, { position.toFloat() }, { dragSec = it }, ::seekToDrag)
                                    .fillMaxWidth()
                                    .height(24.dp)
                            )
                        } else {
                            // No thumb: it would suggest dragging, and AirPlay senders can't seek to a time.
                            LinearProgressIndicator(
                                progress = { (position / nowPlaying.durationSec).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = accent,
                                trackColor = Color.White.copy(alpha = 0.25f),
                                drawStopIndicator = {}
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(position), fontSize = 20.sp, color = MUSIC_TEXT_TERTIARY)
                            Text(formatTime(nowPlaying.durationSec), fontSize = 20.sp, color = MUSIC_TEXT_TERTIARY)
                        }
                    }
                    Spacer(modifier = Modifier.height(if (short) 8.dp else if (compact) 16.dp else 24.dp))
                    // Centred under the progress bar, as in Apple Music. The screen's focus (and so
                    // the remote's OK) starts on play/pause.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MusicControls(nowPlaying.playing, onCommand, onSkip, canChangeTrack, playModifier = modifier)
                    }
                }
            }
        }
    }
    }
}

/**
 * The music screen when lyrics are showing: a smaller cover with the song's name, progress and controls on the left, the
 * lyrics tall on the right. It fades in over the plain screen instead of cutting to it.
 */
@Composable
private fun BigLyricsLayout(
    nowPlaying: NowPlaying,
    cover: androidx.compose.ui.graphics.ImageBitmap?,
    lyrics: Lyrics,
    now: Long,
    lyricsDelaySec: Double,
    onCommand: (DacpClient.Command) -> Unit,
    onSkip: (Boolean) -> Unit,
    canChangeTrack: Boolean,
    playModifier: Modifier
) {
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val restScale by animateFloatAsState(
        if (nowPlaying.playing) 1f else 0.92f,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 220f), label = "smallCoverScale"
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .padding(start = 72.dp, end = 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        // The lyrics column runs the full height of the screen, so a line leaves at the edge itself instead of being cut mid-screen.
        Column(modifier = Modifier.weight(0.8f).padding(top = 100.dp, bottom = 24.dp)) {
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = restScale; scaleY = restScale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                    .size(176.dp)
                    .shadow(32.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = cover, animationSpec = tween(600), label = "smallCover") { shown ->
                    if (shown != null) {
                        Image(bitmap = shown, contentDescription = stringResource(R.string.cover_art), contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                    } else {
                        Text("♪", fontSize = 72.sp, color = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            val titleText = nowPlaying.title ?: stringResource(R.string.airplay_audio)
            Crossfade(targetState = titleText, animationSpec = tween(450), label = "bigTitle") { shownTitle ->
                Text(shownTitle, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, modifier = Modifier.marquee())
            }
            nowPlaying.artist?.let { artistName ->
                Spacer(modifier = Modifier.height(4.dp))
                Crossfade(targetState = artistName, animationSpec = tween(450), label = "bigArtist") { shownArtist ->
                    Text(shownArtist, fontSize = 22.sp, color = MUSIC_TEXT_SECONDARY, maxLines = 1, modifier = Modifier.marquee())
                }
            }
            if (nowPlaying.durationSec > 0) {
                val position = nowPlaying.currentPositionSec(now)
                Spacer(modifier = Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { (position / nowPlaying.durationSec).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = LocalMusicAccent.current,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), fontSize = 16.sp, color = MUSIC_TEXT_TERTIARY)
                    Text(formatTime(nowPlaying.durationSec), fontSize = 16.sp, color = MUSIC_TEXT_TERTIARY)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MusicControls(nowPlaying.playing, onCommand, onSkip, canChangeTrack, playModifier = playModifier)
            }
        }
        Spacer(modifier = Modifier.width(40.dp))
        LyricsStage(lyrics, nowPlaying, lyricsDelaySec, Modifier.weight(1.3f).fillMaxHeight().padding(end = 48.dp))
    }
}

/**
 * Apple-style round icon buttons, focused the way the rest of the app is (the accent colour
 * and a white outline): previous, play/pause (larger), next. DLNA music, played here, has no
 * tracks to change but seeks exactly, so it has rewind and fast forward instead; AirPlay
 * senders can't be asked to seek.
 */
@Composable
private fun MusicControls(
    playing: Boolean,
    onCommand: (DacpClient.Command) -> Unit,
    onSkip: (Boolean) -> Unit,
    canChangeTrack: Boolean,
    playModifier: Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!canChangeTrack) {
            MediaButton(MediaIcons.Back10, R.string.music_rewind, MEDIA_BUTTON_SIZE, iconScale = 0.58f) { onSkip(false) }
        }
        if (canChangeTrack) {
            MediaButton(MediaIcons.Previous, R.string.music_previous, MEDIA_BUTTON_SIZE) {
                onCommand(DacpClient.Command.PREVIOUS)
            }
        }
        MediaButton(
            if (playing) MediaIcons.Pause else MediaIcons.Play,
            if (playing) R.string.music_pause else R.string.music_play,
            PLAY_BUTTON_SIZE,
            playModifier
        ) { onCommand(DacpClient.Command.PLAY_PAUSE) }
        if (canChangeTrack) {
            MediaButton(MediaIcons.Next, R.string.music_next, MEDIA_BUTTON_SIZE) { onCommand(DacpClient.Command.NEXT) }
        }
        if (!canChangeTrack) {
            MediaButton(MediaIcons.Ahead10, R.string.music_fast_forward, MEDIA_BUTTON_SIZE, iconScale = 0.58f) { onSkip(true) }
        }
    }
}

@Composable
private fun MediaButton(
    icon: ImageVector,
    description: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    /** The icon's share of the button; detailed icons (the "10" ones) need more. */
    iconScale: Float = 0.42f,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "mediaButtonScale")
    val accentColor = LocalMusicAccent.current
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // The focused button alone glows, so there is only ever one highlight on screen.
            // Drawn behind (and beyond) the button without taking layout space.
            .then(
                if (focused) {
                    Modifier.drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(accentColor.copy(alpha = 0.5f), Color.Transparent),
                                center = center,
                                radius = this.size.minDimension * 0.85f
                            ),
                            radius = this.size.minDimension * 0.85f
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            // Frosted glass at rest; the app's accent colour and outline when focused.
            .background(if (focused) accentColor.deepen() else Color.White.copy(alpha = 0.14f))
            .then(
                if (focused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = stringResource(description), tint = Color.White, modifier = Modifier.size(size * iconScale))
    }
}

private val MEDIA_BUTTON_SIZE = 60.dp

private val COVER_SIZE = 400.dp
private const val COVER_WIDTH_SHARE = 0.3f
/** Space the cover keeps from the top (and, to stay centred, the bottom) for the casting badge. */
private val MUSIC_BADGE_CLEARANCE = 100.dp

/** The music screen's cover: at most [COVER_SIZE], clear of the casting badge, and 30% of the width. */
@Composable
private fun musicCoverSize(): Dp {
    val configuration = LocalConfiguration.current
    return minOf(
        COVER_SIZE,
        configuration.screenHeightDp.dp - MUSIC_BADGE_CLEARANCE * 2,
        configuration.screenWidthDp.dp * COVER_WIDTH_SHARE
    )
}

/** Scrolls text that doesn't fit, pausing before each pass; text that fits stays still. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.marquee(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 2000,
    repeatDelayMillis = 2000,
    velocity = 40.dp
)
/**
 * The sung lyric line's scroll: a line lasts a few seconds, so it starts soon and moves
 * faster than titles do, once.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.lyricMarquee(): Modifier = basicMarquee(
    iterations = 1,
    initialDelayMillis = 500,
    velocity = 90.dp
)
/** A lighter tint of the app's purple, bright enough on dark backdrops (progress, the focus glow). */
private val MUSIC_ACCENT = Color(0xFFA48BF5)
/**
 * Secondary text on the music screen: translucent white rather than grey, so it keeps its
 * contrast and picks up the tint of the cover's backdrop, as in Apple Music.
 */
private val MUSIC_TEXT_SECONDARY = Color.White.copy(alpha = 0.85f)
private val MUSIC_TEXT_TERTIARY = Color.White.copy(alpha = 0.6f)
private val PLAY_BUTTON_SIZE = 80.dp

/**
 * The album cover, enlarged and blurred under a dark veil, as Apple Music does; the launch
 * artwork when the song has no cover. Cross-fades when the song changes.
 */
@Composable
private fun MusicBackdrop(coverArt: ByteArray?, onAccent: (Int) -> Unit = {}) {
    val backdrop by produceState<Backdrop.Result?>(null, coverArt) {
        value = coverArt?.let { cover -> withContext(Dispatchers.Default) { Backdrop.fromCover(cover) } }
    }
    LaunchedEffect(backdrop) { backdrop?.let { onAccent(it.accent) } }
    // Remembered: this recomposes with every progress tick, and a fresh wrapper each time made
    // the Crossfade below restart over and over, so the backdrop flickered.
    val blurred = remember(backdrop) { backdrop?.bitmap?.asImageBitmap() }
    // Our own calm night while no cover is there yet (or the song has none): never an old launch picture that
    // flashes for a moment and then changes. The cover's blur fades in over it.
    val image = blurred
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B1433), Color(0xFF0B0910)))))
    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(
        listOf(MUSIC_ACCENT.copy(alpha = 0.22f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(0.3f * 1920f, 0.35f * 1080f), radius = 1100f)))
    Crossfade(targetState = image, animationSpec = tween(BACKDROP_FADE_MS), label = "backdrop") { current ->
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    // Light over most covers, darker over bright ones, so white text stays readable.
    val veil by animateFloatAsState(backdrop?.veil ?: FALLBACK_VEIL, tween(BACKDROP_FADE_MS), label = "veil")
    Box(modifier = Modifier.fillMaxSize().background(veilColor().copy(alpha = veil)))
    // A faint grain keeps the wide, smooth gradients from showing bands on 8-bit panels.
    val grain = remember { ShaderBrush(ImageShader(Backdrop.grain().asImageBitmap(), TileMode.Repeated, TileMode.Repeated)) }
    Box(modifier = Modifier.fillMaxSize().background(grain, alpha = GRAIN_ALPHA))
}

/**
 * The veil's colour by the hour: black by day, a warm dark brown in the evening and a deeper one at night, so the
 * screen loses blue light as the room gets dark (the same hours the launcher's wallpaper follows).
 */
private fun veilColor(): Color {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour in 18..22 -> Color(0xFF1E1006)
        hour >= 23 || hour < 7 -> Color(0xFF0C0603)
        else -> Color.Black
    }
}

/** The focus colour taken from the cover (see [Backdrop.accentOf]); the app's purple until one is known. */
private val LocalMusicAccent = compositionLocalOf { MUSIC_ACCENT }

/** A darker shade of [this] for fills that carry white icons: white on the bright accent itself would wash out. */
private fun Color.deepen(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[2] *= 0.72f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private const val BACKDROP_FADE_MS = 700
/** Over the launch artwork, for songs without a cover. */
private const val FALLBACK_VEIL = 0.6f
private const val GRAIN_ALPHA = 0.04f

/**
 * The line of [lyrics] sung at [positionSec], with [contextLines] before and after. As the song moves on the block
 * glides up one line (450 ms) instead of jumping, and the sung line fills with white from left to right over the time
 * it lasts, so the eye follows the voice (the karaoke feel of Apple Music's lyrics). Motion is a layer offset and a
 * shader, no blur: cheap on a stick. A calm profile (low RAM) keeps the fade and skips the glide and the sweep.
 */
@Composable
private fun LyricsView(lyrics: Lyrics, positionSec: Double, contextLines: Int) {
    val light = com.weenas.castbay.ui.Perf.light(androidx.compose.ui.platform.LocalContext.current)
    val current = lyrics.indexAt(positionSec)
    val first = (current - contextLines).coerceAtLeast(0)
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { LYRIC_LINE_HEIGHT.toPx() }
    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    var shown by remember { mutableIntStateOf(current) }
    LaunchedEffect(current) {
        // One line forward glides; a jump (seek) or the first lines just settle.
        if (!light && current == shown + 1 && current > contextLines) {
            slide.snapTo(stepPx)
            slide.animateTo(0f, tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
        shown = current
    }
    // How far through the sung line the song is, finishing a little before the next line starts.
    val start = lyrics.lines.getOrNull(current)?.timeSec ?: 0.0
    val end = lyrics.lines.getOrNull(current + 1)?.timeSec ?: (start + 4.0)
    val raw = if (current < 0) 0f else (((positionSec - start) / ((end - start) * 0.85).coerceAtLeast(0.5))).toFloat().coerceIn(0f, 1f)
    val sweep by animateFloatAsState(if (light) 1f else raw, tween(if (light) 0 else 200, easing = androidx.compose.animation.core.LinearEasing), label = "lyricSweep")
    // A fixed height, so the controls below don't move as lines come and go.
    Box(modifier = Modifier.height(lyricsHeight(contextLines)).clipToBounds()) {
        Column(modifier = Modifier.graphicsLayer { translationY = slide.value }) {
            for (index in first..(current + contextLines).coerceAtMost(lyrics.lines.lastIndex)) {
                val line = lyrics.lines[index].text.ifEmpty { "♪" }
                val active = index == current
                // The sung line is brightest; the others fade the further away they are.
                val target = when (kotlin.math.abs(index - current)) {
                    0 -> 1f
                    1 -> 0.55f
                    else -> 0.3f
                }
                key(index) {
                    // A line fades up or down over 450 ms as the song moves on, instead of switching.
                    val alpha by animateFloatAsState(target, tween(450), label = "lyricAlpha")
                    val style = if (active && !light) {
                        // White up to the voice, a softer white after it.
                        val edge = (sweep * 0.94f)
                        androidx.compose.ui.text.TextStyle(brush = object : ShaderBrush() {
                            override fun createShader(size: androidx.compose.ui.geometry.Size) = androidx.compose.ui.graphics.LinearGradientShader(
                                androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f),
                                listOf(Color.White, Color.White, Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.5f)),
                                listOf(0f, edge, edge + 0.06f, 1f)
                            )
                        })
                    } else androidx.compose.ui.text.TextStyle(color = Color.White.copy(alpha = alpha))
                    Text(
                        line,
                        style = style,
                        fontSize = if (active) 36.sp else 26.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = if (active) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(vertical = 3.dp)
                            .then(if (active) Modifier.lyricMarquee() else Modifier)
                    )
                }
            }
        }
    }
}

/** A song's lyrics: being looked up, found, or known to be unavailable. */
private sealed interface LyricsLookup {
    data object Pending : LyricsLookup
    data object None : LyricsLookup
    data class Found(val lyrics: Lyrics) : LyricsLookup
}

/** A line of lyrics around the sung one (22 sp, with its padding). */
private val LYRIC_LINE_HEIGHT = 42.dp
/** The sung line: larger (26 sp, bold). Counting it as an ordinary line clipped the last one. */
private val SUNG_LYRIC_LINE_HEIGHT = 56.dp

private fun lyricsHeight(contextLines: Int) = LYRIC_LINE_HEIGHT * (contextLines * 2) + SUNG_LYRIC_LINE_HEIGHT + 4.dp
/** Below this, the music screen is compact (see AudioPlayback). */
private const val COMPACT_MUSIC_HEIGHT_DP = 600
private const val SHORT_MUSIC_HEIGHT_DP = 500
private const val LYRICS_LOOKUP_DELAY_MS = 1500L

/** Waits before asking the lyrics site again after it could not be reached. */
private val LYRICS_RETRY_DELAYS_MS = longArrayOf(4_000L, 10_000L, 25_000L)

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Full-screen AirPlay video. The phone stays the main remote; the TV remote can also pause
 * (OK / play-pause) and skip 10 s (left/right, rewind/fast-forward).
 */
@Composable
fun VideoPlayback(
    viewModel: AirPlayViewModel,
    // A parameter, so a new player (the next DLNA episode, after the last one was released)
    // recomposes this even when nothing else about the screen changed.
    player: androidx.media3.common.Player?,
    pictureMode: String,
    awaitingNext: Boolean = false,
    modifier: Modifier = Modifier
) {
    var paused by remember(player) { mutableStateOf(player?.playWhenReady == false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
                        viewModel.toggleVideoPause()
                    Key.DirectionLeft, Key.MediaRewind -> viewModel.seekVideoBy(-SEEK_STEP_SEC)
                    Key.DirectionRight, Key.MediaFastForward -> viewModel.seekVideoBy(SEEK_STEP_SEC)
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    // Spinner while loading or rebuffering, so a slow start isn't a black screen.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    // Keys are handled by the Compose container above.
                    isFocusable = false
                }
            },
            update = {
                it.player = player
                it.resizeMode = when (pictureMode) {
                    ReceiverSettings.PICTURE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    ReceiverSettings.PICTURE_STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        // Shown only if the wait lasts: iQiyi's next episode usually comes within a second.
        var waitShown by remember { mutableStateOf(false) }
        LaunchedEffect(awaitingNext) {
            waitShown = false
            if (awaitingNext) {
                kotlinx.coroutines.delay(AWAITING_NEXT_TEXT_DELAY_MS)
                waitShown = true
            }
        }
        if (awaitingNext && waitShown) {
            // A DLNA video ended: the app casts the next episode in a few seconds.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.awaiting_next_video), fontSize = 26.sp, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.awaiting_next_video_help), fontSize = 18.sp, color = Color(0xB3FFFFFF))
            }
        } else if (paused && !awaitingNext) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Text("❚❚", fontSize = 44.sp, color = Color.White)
            }
        }
    }
}

private const val SEEK_STEP_SEC = 10
private const val AWAITING_NEXT_TEXT_DELAY_MS = 1000L
private const val BACK_AGAIN_WINDOW_MS = 3000L

private enum class StreamKind { VIDEO, AUDIO, MIRRORING }

/** "Stats for nerds": what is playing and how, refreshed every second. */
@Composable
fun StatsOverlay(viewModel: AirPlayViewModel, modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf(viewModel.playbackStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = viewModel.playbackStats()
            delay(1000)
        }
    }
    val current = stats ?: return
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        StatsLine(current.source, "", bold = true)
        current.video?.let { video ->
            StatsLine(
                "Video",
                listOf(
                    video.codec, StatsFormat.resolution(video.width, video.height),
                    StatsFormat.fps(video.fps), StatsFormat.bitrate(video.bitrateBps)
                ).joinToString(" · ")
            )
            StatsLine("", listOfNotNull(
                video.decoder, video.decodeLatencyMs?.let { "decode $it ms" }, "dropped ${video.droppedFrames}"
            ).joinToString(" · "))
        }
        current.audio?.let { audio ->
            StatsLine(
                "Audio",
                listOf(audio.codec, StatsFormat.audioFormat(audio), StatsFormat.bitrate(audio.bitrateBps))
                    .joinToString(" · ")
            )
            audio.decoder?.let { StatsLine("", it) }
        }
        current.extra.forEach { (label, value) -> StatsLine(label, value) }
    }
}

@Composable
private fun StatsLine(label: String, value: String, bold: Boolean = false) {
    Row {
        Text(
            label,
            color = if (bold) Color.White else Color(0xFFB0B0B0),
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            modifier = if (bold) Modifier else Modifier.width(80.dp)
        )
        Text(value, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Full-screen mirrored picture, sized by [pictureMode]: letterboxed to the sender's aspect ratio
 * (Fit), cropped to cover the screen (Fill) or stretched.
 */
@Composable
fun MirroringVideo(
    viewModel: AirPlayViewModel,
    streamInfo: com.weenas.castbay.service.StreamInfo,
    pictureMode: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            // Focusable so the remote's keys reach the quick menu shortcut.
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        val (width, height) = PictureLayout.size(
            pictureMode, maxWidth.value, maxHeight.value, streamInfo.frameWidth, streamInfo.frameHeight
        )
        val videoModifier = Modifier.requiredSize(width.dp, height.dp)
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            viewModel.setVideoSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            viewModel.setVideoSurface(null)
                        }
                    })
                }
            },
            modifier = videoModifier
        )
    }
}

@Composable
fun DisconnectedScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.disconnected), fontSize = 32.sp, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStart, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_start), fontSize = 20.sp)
        }
    }
}

@Composable
fun ErrorScreen(error: String, onRetry: () -> Unit, onDiagnostics: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.error_title), fontSize = 32.sp, color = Color.Red)
        Spacer(modifier = Modifier.height(16.dp))
        Text(error, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_retry), fontSize = 20.sp)
        }
        // The diagnostics hold what went wrong and, if the person chooses, upload it with a report ID
        // to quote in a problem report (the upload asks first, there).
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onDiagnostics) {
            Text(stringResource(R.string.diagnostics_title), fontSize = 18.sp)
        }
    }
}

/** Moves D-pad focus to this element when it first appears, so the primary action is one OK press away. */
@Composable
private fun Modifier.initialFocus(): Modifier {
    val requester = remember { FocusRequester() }
    // On a touch screen nothing is focused up front (see usingKeys).
    val keys = usingKeys()
    LaunchedEffect(requester) { if (keys) requester.requestFocus() }
    return focusRequester(requester)
}
