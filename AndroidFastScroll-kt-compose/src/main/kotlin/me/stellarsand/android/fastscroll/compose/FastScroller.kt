package me.stellarsand.android.fastscroll.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

private val AccelerateDecelerateEasing = Easing { fraction -> (cos((fraction + 1) * PI) / 2.0 + 0.5).toFloat() }

private val LocalFastScrollerState = staticCompositionLocalOf<FastScrollerState?> { null }

private enum class OverlayPart { Track, Thumb, Popup }

internal class ScrollbarGeometry(
    val viewWidth: Int = 0,
    val viewHeight: Int = 0,
    val paddingTop: Int = 0,
    val track: IntRect = IntRect.Zero,
    val thumb: IntRect = IntRect.Zero,
    val thumbOffset: Int = 0,
    val thumbOffsetRange: Int = 0,
    val scrollOffsetRange: Int = 0,
    val enabled: Boolean = false,
    val rtl: Boolean = false,
) {
    val hideTranslation: Float
        get() {
            val width = max(track.width, thumb.width).toFloat()
            return if (rtl) {
                if (track.left == 0) -width else 0f
            } else {
                if (track.right == viewWidth) width else 0f
            }
        }
}

@Stable
class FastScrollerState internal constructor(
    internal val helper: FastScrollHelper,
    private val scope: CoroutineScope,
    private val autoHide: Boolean,
) {
    var isDragging by mutableStateOf(false)
        private set

    internal val scrollbarAlpha = Animatable(1f)
    internal val scrollbarTranslation = Animatable(0f)
    internal val popupAlpha = Animatable(0f)
    internal var geometry = ScrollbarGeometry()
    internal var touchSlop = 0f
    internal var minTouchTargetSize = 0
    internal var coordinates: LayoutCoordinates? = null
    internal val exclusions = mutableListOf<ExclusionZone>()

    private var showingScrollbar = true
    private var showingPopup = false
    private var autoHideJob: Job? = null
    private var scrollJob: Job? = null

    internal fun onScrollChanged() {
        if (!geometry.enabled) return
        showScrollbar()
        postAutoHideScrollbar()
    }

    internal fun setDragging(dragging: Boolean) {
        if (isDragging == dragging) return
        isDragging = dragging
        if (dragging) {
            cancelAutoHideScrollbar()
            showScrollbar()
            showPopup()
        } else {
            postAutoHideScrollbar()
            hidePopup()
        }
    }

    internal fun scrollToThumbOffset(offset: Int) {
        val current = geometry
        if (current.thumbOffsetRange <= 0) return
        val clamped = offset.coerceIn(0, current.thumbOffsetRange)
        val scrollOffset = (current.scrollOffsetRange.toLong() * clamped / current.thumbOffsetRange).toInt()
        scrollJob?.cancel()
        scrollJob = scope.launch { helper.scrollTo(scrollOffset) }
    }

    internal fun postAutoHideScrollbar() {
        cancelAutoHideScrollbar()
        if (!autoHide) return
        autoHideJob = scope.launch {
            delay(FastScrollerDefaults.AutoHideDelayMillis.toLong())
            if (!isDragging) hideScrollbar()
        }
    }

    internal fun isInThumbTouchTarget(x: Float, y: Float): Boolean = isInTouchTarget(geometry.thumb, x, y)

    internal fun isInTrackTouchTarget(x: Float, y: Float): Boolean = isInTouchTarget(geometry.track, x, y)

    internal fun isOnThumb(x: Float, y: Float): Boolean = geometry.thumb.contains(IntOffset(x.toInt(), y.toInt()))

    internal fun isExcluded(position: Offset): Boolean {
        val own = coordinates?.takeIf { it.isAttached } ?: return false
        return exclusions.any { zone ->
            val area = zone.coordinates?.takeIf { it.isAttached } ?: return@any false
            own.localBoundingBoxOf(area).contains(position)
        }
    }

    private fun cancelAutoHideScrollbar() {
        autoHideJob?.cancel()
        autoHideJob = null
    }

    private fun showScrollbar() {
        if (showingScrollbar) return
        showingScrollbar = true
        animateScrollbar(1f, 0f, FastScrollerDefaults.ShowDurationMillis, LinearOutSlowInEasing)
    }

    private fun hideScrollbar() {
        if (!showingScrollbar) return
        showingScrollbar = false
        animateScrollbar(0f, geometry.hideTranslation, FastScrollerDefaults.HideDurationMillis, FastOutLinearInEasing)
    }

    private fun animateScrollbar(alpha: Float, translation: Float, durationMillis: Int, easing: Easing) {
        scope.launch { scrollbarAlpha.animateTo(alpha, tween(durationMillis, easing = easing)) }
        scope.launch { scrollbarTranslation.animateTo(translation, tween(durationMillis, easing = easing)) }
    }

    private fun showPopup() {
        if (showingPopup) return
        showingPopup = true
        animatePopup(1f, FastScrollerDefaults.ShowDurationMillis)
    }

    private fun hidePopup() {
        if (!showingPopup) return
        showingPopup = false
        animatePopup(0f, FastScrollerDefaults.HideDurationMillis)
    }

    private fun animatePopup(alpha: Float, durationMillis: Int) {
        scope.launch { popupAlpha.animateTo(alpha, tween(durationMillis, easing = AccelerateDecelerateEasing)) }
    }

    private fun isInTouchTarget(rect: IntRect, x: Float, y: Float): Boolean {
        val current = geometry
        return isInTouchTarget(x, rect.left, rect.right, 0, current.viewWidth) &&
            isInTouchTarget(y, rect.top, rect.bottom, 0, current.viewHeight)
    }

    private fun isInTouchTarget(position: Float, viewStart: Int, viewEnd: Int, parentStart: Int, parentEnd: Int): Boolean {
        val viewSize = viewEnd - viewStart
        if (viewSize >= minTouchTargetSize) return position >= viewStart && position < viewEnd
        var touchTargetStart = viewStart - (minTouchTargetSize - viewSize) / 2
        if (touchTargetStart < parentStart) touchTargetStart = parentStart
        var touchTargetEnd = touchTargetStart + minTouchTargetSize
        if (touchTargetEnd > parentEnd) {
            touchTargetEnd = parentEnd
            touchTargetStart = touchTargetEnd - minTouchTargetSize
            if (touchTargetStart < parentStart) touchTargetStart = parentStart
        }
        return position >= touchTargetStart && position < touchTargetEnd
    }
}

@Composable
fun rememberFastScrollerState(helper: FastScrollHelper, autoHide: Boolean = true): FastScrollerState {
    val scope = rememberCoroutineScope()
    return remember(helper, autoHide) { FastScrollerState(helper, scope, autoHide) }
}

@Composable
fun FastScroller(
    helper: FastScrollHelper,
    modifier: Modifier = Modifier,
    style: FastScrollerStyle = FastScrollerDefaults.defaultStyle(),
    padding: PaddingValues? = null,
    autoHide: Boolean = true,
    state: FastScrollerState = rememberFastScrollerState(helper, autoHide),
    content: @Composable BoxScope.() -> Unit,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    SideEffect {
        state.touchSlop = viewConfiguration.touchSlop
        state.minTouchTargetSize = with(density) { FastScrollerDefaults.MinTouchTargetSize.roundToPx() }
    }
    LaunchedEffect(state) {
        state.postAutoHideScrollbar()
        snapshotFlow { helper.scrollOffset }.drop(1).collect { state.onScrollChanged() }
    }
    Box(
        modifier
            .onPlaced { state.coordinates = it }
            .pointerInput(state) { awaitEachGesture { fastScrollGesture(state) } },
    ) {
        CompositionLocalProvider(LocalFastScrollerState provides state) { this@Box.content() }
        FastScrollerOverlay(state, style, padding, Modifier.matchParentSize())
    }
}

internal class ExclusionZone {
    var coordinates: LayoutCoordinates? = null
}

/** Area where a press belongs to its own content: only the visible thumb can be grabbed there. */
fun Modifier.fastScrollExclusion(): Modifier = composed {
    val state = LocalFastScrollerState.current ?: return@composed this
    val zone = remember { ExclusionZone() }
    DisposableEffect(state, zone) {
        state.exclusions += zone
        onDispose { state.exclusions -= zone }
    }
    onPlaced { zone.coordinates = it }
}

private suspend fun AwaitPointerEventScope.fastScrollGesture(state: FastScrollerState) {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (!state.geometry.enabled) return
    val pointerId = down.id
    val downX = down.position.x
    val downY = down.position.y
    val excluded = state.isExcluded(down.position)
    val onThumb = if (excluded) state.isOnThumb(downX, downY) else state.isInThumbTouchTarget(downX, downY)
    var lastY = downY
    var dragStartY = 0f
    var dragStartThumbOffset = 0
    var dragging = false
    if (state.scrollbarAlpha.value > 0f && onThumb) {
        dragStartY = downY
        dragStartThumbOffset = state.geometry.thumbOffset
        dragging = true
        state.setDragging(true)
        down.consume()
    }
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.first()
            if (!change.pressed) {
                if (dragging) change.consume()
                return
            }
            if (!state.geometry.enabled) return
            val y = change.position.y
            if (!dragging && !excluded && state.isInTrackTouchTarget(downX, downY) && abs(y - downY) > state.touchSlop) {
                if (onThumb) {
                    dragStartY = lastY
                    dragStartThumbOffset = state.geometry.thumbOffset
                } else {
                    dragStartY = y
                    dragStartThumbOffset = (y - state.geometry.paddingTop - state.geometry.thumb.height / 2f).toInt()
                    state.scrollToThumbOffset(dragStartThumbOffset)
                }
                dragging = true
                state.setDragging(true)
            }
            if (dragging) {
                state.scrollToThumbOffset(dragStartThumbOffset + (y - dragStartY).toInt())
                change.consume()
            }
            lastY = y
        }
    } finally {
        state.setDragging(false)
    }
}

private fun Modifier.scrollbarLayer(state: FastScrollerState): Modifier = graphicsLayer {
    alpha = state.scrollbarAlpha.value
    translationX = state.scrollbarTranslation.value
}

private fun clamp(value: Int, min: Int, max: Int): Int = if (value < min) min else if (value > max) max else value

@Composable
private fun FastScrollerOverlay(
    state: FastScrollerState,
    style: FastScrollerStyle,
    padding: PaddingValues?,
    modifier: Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val helper = state.helper
    val popup = style.popup
    val popupText by remember(helper, popup) {
        derivedStateOf { if (popup == null) null else helper.popupText.orEmpty() }
    }
    val text = popupText
    val thumbColor = if (state.isDragging) style.thumb.pressedColor else style.thumb.color
    Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier
                    .layoutId(OverlayPart.Track)
                    .scrollbarLayer(state)
                    .background(style.track.color, style.track.shape),
            )
            Box(
                Modifier
                    .layoutId(OverlayPart.Thumb)
                    .scrollbarLayer(state)
                    .background(thumbColor, style.thumb.shape),
            )
            if (popup != null && !text.isNullOrEmpty()) {
                BasicText(
                    text = text,
                    modifier = Modifier
                        .layoutId(OverlayPart.Popup)
                        .graphicsLayer { alpha = state.popupAlpha.value }
                        .shadow(popup.elevation, popup.shape)
                        .background(popup.color, popup.shape)
                        .defaultMinSize(popup.minWidth, popup.minHeight)
                        .padding(popup.contentPadding)
                        .wrapContentSize(Alignment.Center),
                    style = TextStyle(
                        color = popup.textColor,
                        fontSize = with(density) { popup.textSize.toSp() },
                        textAlign = TextAlign.Center,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        },
    ) { measurables, constraints ->
        val viewWidth = constraints.maxWidth
        val viewHeight = constraints.maxHeight
        val rtl = layoutDirection == LayoutDirection.Rtl
        val paddingLeft = padding?.calculateStartPadding(LayoutDirection.Ltr)?.roundToPx() ?: 0
        val paddingRight = padding?.calculateEndPadding(LayoutDirection.Ltr)?.roundToPx() ?: 0
        val paddingTop = padding?.calculateTopPadding()?.roundToPx() ?: helper.contentPaddingTop
        val paddingBottom = padding?.calculateBottomPadding()?.roundToPx() ?: helper.contentPaddingBottom
        val trackWidth = style.track.width.roundToPx()
        val thumbWidth = style.thumb.width.roundToPx()
        val thumbHeight = style.thumb.height.roundToPx()
        val scrollOffsetRange = helper.scrollRange - viewHeight
        val enabled = scrollOffsetRange > 0
        val thumbOffsetRange = viewHeight - paddingTop - paddingBottom - thumbHeight
        val thumbOffset = if (enabled) (thumbOffsetRange.toLong() * helper.scrollOffset / scrollOffsetRange).toInt() else 0
        val trackLeft = if (rtl) paddingLeft else viewWidth - paddingRight - trackWidth
        val track = IntRect(trackLeft, paddingTop, trackLeft + trackWidth, max(viewHeight - paddingBottom, paddingTop))
        val thumbLeft = if (rtl) paddingLeft else viewWidth - paddingRight - thumbWidth
        val thumbTop = paddingTop + thumbOffset
        val thumb = IntRect(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight)
        state.geometry = ScrollbarGeometry(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            paddingTop = paddingTop,
            track = track,
            thumb = thumb,
            thumbOffset = thumbOffset,
            thumbOffsetRange = thumbOffsetRange,
            scrollOffsetRange = scrollOffsetRange,
            enabled = enabled,
            rtl = rtl,
        )
        val trackPlaceable = measurables.first { it.layoutId == OverlayPart.Track }
            .measure(Constraints.fixed(track.width, track.height))
        val thumbPlaceable = measurables.first { it.layoutId == OverlayPart.Thumb }
            .measure(Constraints.fixed(thumb.width, thumb.height))
        var popupPlaceable: Placeable? = null
        var popupLeft = 0
        var popupTop = 0
        val popupMeasurable = measurables.firstOrNull { it.layoutId == OverlayPart.Popup }
        if (popup != null && popupMeasurable != null) {
            val marginEnd = popup.marginEnd.roundToPx()
            val placeable = popupMeasurable.measure(
                Constraints(
                    maxWidth = (viewWidth - paddingLeft - paddingRight - thumbWidth - marginEnd).coerceAtLeast(0),
                    maxHeight = (viewHeight - paddingTop - paddingBottom).coerceAtLeast(0),
                ),
            )
            val anchorInset = style.thumb.anchorInset.roundToPx()
            val popupAnchorY = when (popup.popupAnchor) {
                PopupAnchor.Start -> 0
                PopupAnchor.Center -> placeable.height / 2
                PopupAnchor.End -> placeable.height
            }
            val thumbAnchorY = when (popup.thumbAnchor) {
                PopupAnchor.Start -> anchorInset
                PopupAnchor.Center -> anchorInset + (thumbHeight - anchorInset * 2) / 2
                PopupAnchor.End -> thumbHeight - anchorInset
            }
            popupLeft = if (rtl) {
                paddingLeft + thumbWidth + marginEnd
            } else {
                viewWidth - paddingRight - thumbWidth - marginEnd - placeable.width
            }
            popupTop = clamp(
                thumbTop + thumbAnchorY - popupAnchorY,
                paddingTop,
                viewHeight - paddingBottom - placeable.height,
            )
            popupPlaceable = placeable
        }
        layout(viewWidth, viewHeight) {
            if (!enabled) return@layout
            trackPlaceable.place(track.left, track.top)
            thumbPlaceable.place(thumb.left, thumb.top)
            popupPlaceable?.place(popupLeft, popupTop)
        }
    }
}
