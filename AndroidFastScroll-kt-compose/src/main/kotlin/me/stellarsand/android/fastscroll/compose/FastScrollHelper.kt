package me.stellarsand.android.fastscroll.compose

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.max
import kotlin.math.min

@Stable
interface FastScrollHelper {
    val scrollRange: Int
    val scrollOffset: Int
    val viewportHeight: Int
    val contentPaddingTop: Int
    val contentPaddingBottom: Int
    val popupText: String?
    suspend fun scrollTo(offset: Int)
}

@Composable
fun rememberLazyListFastScrollHelper(
    state: LazyListState,
    popupText: ((index: Int) -> String?)? = null,
): FastScrollHelper {
    val provider = rememberUpdatedState(popupText)
    return remember(state) { LazyListFastScrollHelper(state, provider) }
}

private const val NO_POSITION = -1

private class LazyListFastScrollHelper(
    private val state: LazyListState,
    private val popupTextProvider: State<((Int) -> String?)?>,
) : FastScrollHelper {
    private val info: LazyListLayoutInfo
        get() = state.layoutInfo

    private val itemHeight: Int
        get() = info.visibleItemsInfo.firstOrNull()?.let { it.size + info.mainAxisItemSpacing } ?: 0

    private val itemCount: Int
        get() = info.totalItemsCount

    private val firstIndex: Int
        get() = info.visibleItemsInfo.firstOrNull()?.index ?: NO_POSITION

    private val firstOffset: Int
        get() = info.visibleItemsInfo.firstOrNull()?.offset ?: 0

    override val contentPaddingTop: Int
        get() = info.beforeContentPadding

    override val contentPaddingBottom: Int
        get() = info.afterContentPadding

    override val viewportHeight: Int
        get() = info.viewportSize.height

    override val scrollRange: Int
        get() {
            val height = itemHeight
            if (height == 0 || itemCount == 0) return 0
            return contentPaddingTop + itemCount * height - info.mainAxisItemSpacing + contentPaddingBottom
        }

    override val scrollOffset: Int
        get() {
            val index = firstIndex
            return if (index == NO_POSITION) 0 else index * itemHeight - firstOffset
        }

    override val popupText: String?
        get() {
            val provider = popupTextProvider.value ?: return null
            val position = popupTextPosition()
            return if (position == NO_POSITION) null else provider(position)
        }

    override suspend fun scrollTo(offset: Int) {
        val height = itemHeight
        if (height == 0) return
        val contentOffset = offset - contentPaddingTop
        val index = max(0, contentOffset / height)
        state.scrollToItem(index, contentOffset - index * height)
    }

    private fun popupTextPosition(): Int {
        val position = firstIndex
        if (position == NO_POSITION) return NO_POSITION
        val visible = info.visibleItemsInfo
        val range = max(scrollRange - viewportHeight, 1)
        val offset = min(scrollOffset, range)
        val positionOffset = (visible.last().index - visible.first().index + 1) * offset / range
        return min(position + positionOffset, itemCount - 1)
    }
}
