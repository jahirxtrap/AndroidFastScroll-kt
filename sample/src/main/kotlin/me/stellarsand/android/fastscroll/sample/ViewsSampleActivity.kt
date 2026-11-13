package me.stellarsand.android.fastscroll.sample

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.stellarsand.android.fastscroll.FastScrollerBuilder
import me.stellarsand.android.fastscroll.PopupTextProvider

class ViewsSampleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = RecyclerView(this)
        list.setBackgroundColor(ContextCompat.getColor(this, R.color.sample_background))
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = TitleAdapter(SampleItems.titles(this))
        FastScrollerBuilder(list).build()
        setContentView(list)
    }
}

private class TitleAdapter(private val titles: List<String>) :
    RecyclerView.Adapter<TitleAdapter.Holder>(), PopupTextProvider {

    class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val resources = parent.resources
        val text = TextView(parent.context)
        text.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.row_height),
        )
        text.gravity = Gravity.CENTER_VERTICAL
        text.setPaddingRelative(resources.getDimensionPixelSize(R.dimen.row_padding), 0, 0, 0)
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.row_text_size))
        text.setTextColor(ContextCompat.getColor(parent.context, R.color.row_text))
        return Holder(text)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.text.text = titles[position]
    }

    override fun getItemCount(): Int = titles.size

    override fun getPopupText(view: View, position: Int): CharSequence = SampleItems.popupText(titles[position])
}
