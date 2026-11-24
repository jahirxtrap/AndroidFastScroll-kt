package me.stellarsand.android.fastscroll.compose

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
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

@Composable
fun rememberLazyGridFastScrollHelper(
    state: LazyGridState,
    popupText: ((index: Int) -> String?)? = null,
): FastScrollHelper {
    val provider = rememberUpdatedState(popupText)
    return remember(state) { LazyGridFastScrollHelper(state, provider) }
}

private const val NO_POSITION = -1

private abstract class LazyFastScrollHelper(
    private val popupTextProvider: State<((Int) -> String?)?>,
) : FastScrollHelper {
    protected abstract val itemHeight: Int
    protected abstract val itemSpacing: Int
    protected abstract val itemCount: Int
    protected abstract val spanCount: Int
    protected abstract val firstIndex: Int
    protected abstract val lastIndex: Int
    protected abstract val firstOffset: Int

    protected abstract suspend fun scrollToItem(index: Int, offset: Int)

    private val lineCount: Int
        get() = if (itemCount == 0) 0 else (itemCount - 1) / spanCount + 1

    override val scrollRange: Int
        get() {
            val height = itemHeight
            if (height == 0 || itemCount == 0) return 0
            return contentPaddingTop + lineCount * height - itemSpacing + contentPaddingBottom
        }

    override val scrollOffset: Int
        get() {
            val index = firstIndex
            return if (index == NO_POSITION) 0 else index / spanCount * itemHeight - firstOffset
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
        val line = max(0, contentOffset / height)
        scrollToItem(line * spanCount, contentOffset - line * height)
    }

    private fun popupTextPosition(): Int {
        val position = firstIndex
        if (position == NO_POSITION) return NO_POSITION
        val range = max(scrollRange - viewportHeight, 1)
        val offset = min(scrollOffset, range)
        val positionOffset = (lastIndex - position + 1) * offset / range
        return min(position + positionOffset, itemCount - 1)
    }
}

private class LazyListFastScrollHelper(
    private val state: LazyListState,
    popupTextProvider: State<((Int) -> String?)?>,
) : LazyFastScrollHelper(popupTextProvider) {
    private val info: LazyListLayoutInfo
        get() = state.layoutInfo

    override val itemHeight: Int
        get() = info.visibleItemsInfo.firstOrNull()?.let { it.size + info.mainAxisItemSpacing } ?: 0

    override val itemSpacing: Int
        get() = info.mainAxisItemSpacing

    override val itemCount: Int
        get() = info.totalItemsCount

    override val spanCount: Int
        get() = 1

    override val firstIndex: Int
        get() = info.visibleItemsInfo.firstOrNull()?.index ?: NO_POSITION

    override val lastIndex: Int
        get() = info.visibleItemsInfo.lastOrNull()?.index ?: NO_POSITION

    override val firstOffset: Int
        get() = info.visibleItemsInfo.firstOrNull()?.offset ?: 0

    override val contentPaddingTop: Int
        get() = info.beforeContentPadding

    override val contentPaddingBottom: Int
        get() = info.afterContentPadding

    override val viewportHeight: Int
        get() = info.viewportSize.height

    override suspend fun scrollToItem(index: Int, offset: Int) = state.scrollToItem(index, offset)
}

private class LazyGridFastScrollHelper(
    private val state: LazyGridState,
    popupTextProvider: State<((Int) -> String?)?>,
) : LazyFastScrollHelper(popupTextProvider) {
    private val info: LazyGridLayoutInfo
        get() = state.layoutInfo

    override val itemHeight: Int
        get() = info.visibleItemsInfo.firstOrNull()?.let { it.size.height + info.mainAxisItemSpacing } ?: 0

    override val itemSpacing: Int
        get() = info.mainAxisItemSpacing

    override val itemCount: Int
        get() = info.totalItemsCount

    override val spanCount: Int
        get() = max(info.maxSpan, 1)

    override val firstIndex: Int
        get() = info.visibleItemsInfo.firstOrNull()?.index ?: NO_POSITION

    override val lastIndex: Int
        get() = info.visibleItemsInfo.lastOrNull()?.index ?: NO_POSITION

    override val firstOffset: Int
        get() = info.visibleItemsInfo.firstOrNull()?.offset?.y ?: 0

    override val contentPaddingTop: Int
        get() = info.beforeContentPadding

    override val contentPaddingBottom: Int
        get() = info.afterContentPadding

    override val viewportHeight: Int
        get() = info.viewportSize.height

    override suspend fun scrollToItem(index: Int, offset: Int) = state.scrollToItem(index, offset)
}
