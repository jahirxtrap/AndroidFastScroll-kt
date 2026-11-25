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
import java.util.TreeMap
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
    protected abstract val itemCount: Int
    protected abstract val firstIndex: Int
    protected abstract val lastIndex: Int

    override val popupText: String?
        get() {
            val provider = popupTextProvider.value ?: return null
            val position = popupTextPosition()
            return if (position == NO_POSITION) null else provider(position)
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
    private val extents = ItemExtents()
    private var measured: LazyListLayoutInfo? = null

    private val info: LazyListLayoutInfo
        get() = state.layoutInfo.also(::measure)

    private fun measure(layout: LazyListLayoutInfo) {
        if (layout === measured) return
        measured = layout
        extents.resize(layout.totalItemsCount)
        for (item in layout.visibleItemsInfo) extents[item.index] = item.size + layout.mainAxisItemSpacing
    }

    override val scrollRange: Int
        get() {
            val layout = info
            if (extents.isEmpty) return 0
            return layout.beforeContentPadding + extents.total - layout.mainAxisItemSpacing + layout.afterContentPadding
        }

    override val scrollOffset: Int
        get() {
            val first = info.visibleItemsInfo.firstOrNull() ?: return 0
            return extents.before(first.index) - first.offset
        }

    override val itemCount: Int
        get() = info.totalItemsCount

    override val firstIndex: Int
        get() = info.visibleItemsInfo.firstOrNull()?.index ?: NO_POSITION

    override val lastIndex: Int
        get() = info.visibleItemsInfo.lastOrNull()?.index ?: NO_POSITION

    override val contentPaddingTop: Int
        get() = info.beforeContentPadding

    override val contentPaddingBottom: Int
        get() = info.afterContentPadding

    override val viewportHeight: Int
        get() = info.viewportSize.height

    override suspend fun scrollTo(offset: Int) {
        measure(state.layoutInfo)
        if (extents.isEmpty) return
        val index = extents.indexAt(offset)
        state.scrollToItem(index, offset - extents.before(index))
    }
}

private class LazyGridFastScrollHelper(
    private val state: LazyGridState,
    popupTextProvider: State<((Int) -> String?)?>,
) : LazyFastScrollHelper(popupTextProvider) {
    private val info: LazyGridLayoutInfo
        get() = state.layoutInfo

    private val lineHeight: Int
        get() = info.visibleItemsInfo.firstOrNull()?.let { it.size.height + info.mainAxisItemSpacing } ?: 0

    private val spanCount: Int
        get() = max(info.maxSpan, 1)

    override val scrollRange: Int
        get() {
            val height = lineHeight
            val count = itemCount
            if (height == 0 || count == 0) return 0
            val lines = (count - 1) / spanCount + 1
            return contentPaddingTop + lines * height - info.mainAxisItemSpacing + contentPaddingBottom
        }

    override val scrollOffset: Int
        get() {
            val first = info.visibleItemsInfo.firstOrNull() ?: return 0
            return first.index / spanCount * lineHeight - first.offset.y
        }

    override val itemCount: Int
        get() = info.totalItemsCount

    override val firstIndex: Int
        get() = info.visibleItemsInfo.firstOrNull()?.index ?: NO_POSITION

    override val lastIndex: Int
        get() = info.visibleItemsInfo.lastOrNull()?.index ?: NO_POSITION

    override val contentPaddingTop: Int
        get() = info.beforeContentPadding

    override val contentPaddingBottom: Int
        get() = info.afterContentPadding

    override val viewportHeight: Int
        get() = info.viewportSize.height

    override suspend fun scrollTo(offset: Int) {
        val height = lineHeight
        if (height == 0) return
        val line = offset / height
        state.scrollToItem(line * spanCount, offset - line * height)
    }
}

private const val UNKNOWN = -1

/** Main axis extents of list items: the measured ones as seen, the rest at the measured median. */
private class ItemExtents {
    private var extents = IntArray(0)
    private var sums = IntArray(1)
    private var counts = IntArray(1)
    private val frequencies = TreeMap<Int, Int>()
    private var knownCount = 0
    private var median = UNKNOWN

    val isEmpty: Boolean
        get() = knownCount == 0

    val total: Int
        get() = before(extents.size)

    fun resize(size: Int) {
        if (size == extents.size) return
        extents = IntArray(size) { UNKNOWN }
        sums = IntArray(size + 1)
        counts = IntArray(size + 1)
        frequencies.clear()
        knownCount = 0
        median = UNKNOWN
    }

    operator fun set(index: Int, extent: Int) {
        val previous = extents[index]
        if (previous == extent) return
        extents[index] = extent
        if (previous == UNKNOWN) {
            add(counts, index, 1)
            knownCount++
            add(sums, index, extent)
        } else {
            add(sums, index, extent - previous)
            frequencies.computeIfPresent(previous) { _, count -> if (count > 1) count - 1 else null }
        }
        frequencies.merge(extent, 1, Int::plus)
        median = UNKNOWN
    }

    fun before(index: Int): Int = prefix(sums, index) + (index - prefix(counts, index)) * typical()

    fun indexAt(offset: Int): Int {
        var low = 0
        var high = extents.size - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (before(middle) <= offset) low = middle else high = middle - 1
        }
        return low
    }

    private fun typical(): Int {
        if (median != UNKNOWN || knownCount == 0) return max(median, 0)
        var remaining = (knownCount - 1) / 2
        for ((extent, count) in frequencies) {
            if (remaining < count) {
                median = extent
                break
            }
            remaining -= count
        }
        return median
    }

    private fun add(tree: IntArray, index: Int, delta: Int) {
        var node = index + 1
        while (node < tree.size) {
            tree[node] += delta
            node += node and -node
        }
    }

    private fun prefix(tree: IntArray, end: Int): Int {
        var node = end
        var total = 0
        while (node > 0) {
            total += tree[node]
            node -= node and -node
        }
        return total
    }
}
