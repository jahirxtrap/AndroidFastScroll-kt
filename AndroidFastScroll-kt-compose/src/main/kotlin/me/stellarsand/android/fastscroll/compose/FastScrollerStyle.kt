package me.stellarsand.android.fastscroll.compose

import android.R as AndroidR
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sqrt

enum class PopupAnchor { Start, Center, End }

@Immutable
class TrackStyle(
    val width: Dp,
    val color: Color,
    val shape: Shape = RectangleShape,
)

@Immutable
class ThumbStyle(
    val width: Dp,
    val height: Dp,
    val color: Color,
    val pressedColor: Color = color,
    val shape: Shape = RectangleShape,
    val anchorInset: Dp = 0.dp,
)

@Immutable
class PopupStyle(
    val minWidth: Dp,
    val minHeight: Dp,
    val textSize: Dp,
    val textColor: Color,
    val color: Color,
    val shape: Shape,
    val contentPadding: PaddingValues,
    val marginEnd: Dp,
    val elevation: Dp,
    val popupAnchor: PopupAnchor,
    val thumbAnchor: PopupAnchor,
)

@Immutable
class FastScrollerStyle(
    val track: TrackStyle,
    val thumb: ThumbStyle,
    val popup: PopupStyle?,
)

class TeardropShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = size.height / 2f
        val diagonal = sqrt(2f)
        val width = max(size.width, radius + diagonal * radius)
        val tipRadius = radius / TIP_RADIUS_DIVISOR
        val shoulderX = width - diagonal * radius
        val tipX = width - diagonal * tipRadius
        val path = Path().apply {
            arcTo(circle(radius, radius, radius), LEFT_START_ANGLE, HALF_TURN, false)
            arcTo(circle(shoulderX, radius, radius), TOP_START_ANGLE, SHOULDER_SWEEP, false)
            arcTo(circle(tipX, radius, tipRadius), -SHOULDER_SWEEP, TIP_SWEEP, false)
            arcTo(circle(shoulderX, radius, radius), SHOULDER_SWEEP, SHOULDER_SWEEP, false)
            close()
        }
        if (layoutDirection == LayoutDirection.Rtl) {
            path.transform(Matrix().apply { translate(width, 0f); scale(-1f, 1f) })
        }
        return Outline.Generic(path)
    }

    private fun circle(centerX: Float, centerY: Float, radius: Float) =
        Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

    private companion object {
        const val TIP_RADIUS_DIVISOR = 5f
        const val LEFT_START_ANGLE = 90f
        const val TOP_START_ANGLE = -90f
        const val HALF_TURN = 180f
        const val SHOULDER_SWEEP = 45f
        const val TIP_SWEEP = 90f
    }
}

object FastScrollerDefaults {
    val MinTouchTargetSize = 48.dp
    const val AutoHideDelayMillis = 1500
    const val ShowDurationMillis = 150
    const val HideDurationMillis = 200

    private const val TRACK_ALPHA = 0x39 / 255f
    private val BarWidth = 8.dp
    private val BarCornerRadius = 8.dp

    private val DefaultThumbHeight = 52.dp
    private val DefaultThumbInset = 4.dp
    private val DefaultPopupMinWidth = 78.dp
    private val DefaultPopupMinHeight = 64.dp
    private val DefaultPopupMarginEnd = 14.dp
    private val DefaultPopupPaddingStart = 16.dp
    private val DefaultPopupPaddingEnd = 29.dp
    private val DefaultPopupElevation = 3.dp
    private val DefaultPopupTextSize = 34.dp

    private val Md1ThumbHeight = 48.dp
    private val Md1PopupMinSize = 88.dp
    private val Md1PopupMarginEnd = 16.dp
    private val Md1PopupCornerRadius = 44.dp
    private val Md1PopupPadding = 22.dp
    private val Md1PopupTextSize = 45.dp

    @Composable
    fun controlActivatedColor(): Color = themeColor(AndroidR.attr.colorControlActivated)

    @Composable
    fun controlNormalColor(): Color = themeColor(AndroidR.attr.colorControlNormal)

    @Composable
    fun popupTextColor(): Color = themeColor(AndroidR.attr.textColorPrimaryInverse)

    fun trackColor(controlNormal: Color): Color = controlNormal.copy(alpha = controlNormal.alpha * TRACK_ALPHA)

    @Composable
    fun defaultStyle(
        thumbColor: Color = controlActivatedColor(),
        trackColor: Color = trackColor(controlNormalColor()),
        popupTextColor: Color = popupTextColor(),
    ): FastScrollerStyle = FastScrollerStyle(
        track = TrackStyle(BarWidth, trackColor, RoundedCornerShape(BarCornerRadius)),
        thumb = ThumbStyle(
            width = BarWidth,
            height = DefaultThumbHeight,
            color = thumbColor,
            shape = RoundedCornerShape(BarCornerRadius),
            anchorInset = DefaultThumbInset,
        ),
        popup = PopupStyle(
            minWidth = DefaultPopupMinWidth,
            minHeight = DefaultPopupMinHeight,
            textSize = DefaultPopupTextSize,
            textColor = popupTextColor,
            color = thumbColor,
            shape = TeardropShape(),
            contentPadding = PaddingValues(start = DefaultPopupPaddingStart, end = DefaultPopupPaddingEnd),
            marginEnd = DefaultPopupMarginEnd,
            elevation = DefaultPopupElevation,
            popupAnchor = PopupAnchor.Center,
            thumbAnchor = PopupAnchor.Start,
        ),
    )

    @Composable
    fun md1Style(
        thumbColor: Color = controlActivatedColor(),
        trackColor: Color = trackColor(controlNormalColor()),
        popupTextColor: Color = popupTextColor(),
    ): FastScrollerStyle = FastScrollerStyle(
        track = TrackStyle(BarWidth, trackColor),
        thumb = ThumbStyle(width = BarWidth, height = Md1ThumbHeight, color = thumbColor),
        popup = PopupStyle(
            minWidth = Md1PopupMinSize,
            minHeight = Md1PopupMinSize,
            textSize = Md1PopupTextSize,
            textColor = popupTextColor,
            color = thumbColor,
            shape = RoundedCornerShape(
                topStart = Md1PopupCornerRadius,
                topEnd = Md1PopupCornerRadius,
                bottomStart = Md1PopupCornerRadius,
                bottomEnd = 0.dp,
            ),
            contentPadding = PaddingValues(horizontal = Md1PopupPadding),
            marginEnd = Md1PopupMarginEnd,
            elevation = 0.dp,
            popupAnchor = PopupAnchor.End,
            thumbAnchor = PopupAnchor.Center,
        ),
    )
}

@Composable
private fun themeColor(attr: Int): Color {
    val context = LocalContext.current
    return remember(context, attr) { Color(context.themeColor(attr)) }
}

private fun Context.themeColor(attr: Int): Int {
    val attributes = obtainStyledAttributes(intArrayOf(attr))
    try {
        return attributes.getColorStateList(0)?.defaultColor ?: attributes.getColor(0, 0)
    } finally {
        attributes.recycle()
    }
}
