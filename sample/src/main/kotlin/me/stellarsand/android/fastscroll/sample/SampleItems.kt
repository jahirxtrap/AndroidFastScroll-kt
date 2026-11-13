package me.stellarsand.android.fastscroll.sample

import android.content.Context

object SampleItems {
    fun titles(context: Context): List<String> {
        val count = context.resources.getInteger(R.integer.item_count)
        return List(count) { context.getString(R.string.item_title, it + 1) }
    }

    fun popupText(title: String): String = title.takeLast(POPUP_LENGTH)

    private const val POPUP_LENGTH = 2
}
