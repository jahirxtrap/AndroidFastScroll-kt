package me.stellarsand.android.fastscroll.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import me.stellarsand.android.fastscroll.compose.FastScroller
import me.stellarsand.android.fastscroll.compose.rememberLazyListFastScrollHelper

class ComposeSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SampleList() }
    }
}

@Composable
private fun SampleList() {
    val context = LocalContext.current
    val titles = remember(context) { SampleItems.titles(context) }
    val state = rememberLazyListState()
    val helper = rememberLazyListFastScrollHelper(state) { SampleItems.popupText(titles[it]) }
    val textStyle = TextStyle(
        color = colorResource(R.color.row_text),
        fontSize = with(LocalDensity.current) { dimensionResource(R.dimen.row_text_size).toSp() },
    )
    FastScroller(helper = helper, modifier = Modifier.fillMaxSize().background(colorResource(R.color.sample_background))) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(titles) { title ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.row_height))
                        .padding(start = dimensionResource(R.dimen.row_padding)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(text = title, style = textStyle)
                }
            }
        }
    }
}
