package com.food.opencook.ui.review

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** The same drag engine AppStow uses: ItemTouchHelper moves the actual card and scrolls
 * RecyclerView at its edges. Compose still renders the editable fields in each card. */
@Composable
internal fun <T> ReorderableEditorList(
    items: List<T>,
    modifier: Modifier = Modifier,
    onMove: (Int, Int) -> Unit,
    card: @Composable (item: T, position: Int, startDrag: () -> Unit) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    AndroidView(
        modifier = modifier.fillMaxSize().clipToBounds(),
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                clipToPadding = false
                val listAdapter = EditorAdapter(items, card, onMove) { content ->
                    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
                        content()
                    }
                }
                adapter = listAdapter
                listAdapter.touchHelper.attachToRecyclerView(this)
            }
        },
        update = { recycler ->
            @Suppress("UNCHECKED_CAST")
            val listAdapter = recycler.adapter as EditorAdapter<T>
            listAdapter.card = card
            listAdapter.onMove = onMove
            listAdapter.sync(items)
        },
    )
}

private class EditorAdapter<T>(
    initial: List<T>,
    var card: @Composable (T, Int, () -> Unit) -> Unit,
    var onMove: (Int, Int) -> Unit,
    private val wrapTheme: @Composable (@Composable () -> Unit) -> Unit,
) : RecyclerView.Adapter<EditorAdapter<T>.Holder>() {
    private val rows = mutableStateListOf<T>().apply { addAll(initial) }
    private var dragging = false
    private var from = RecyclerView.NO_POSITION
    private var to = RecyclerView.NO_POSITION

    inner class Holder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        var displayIndex by mutableIntStateOf(RecyclerView.NO_POSITION)
        init {
            composeView.setContent {
                val index = displayIndex
                if (index in rows.indices) {
                    wrapTheme { card(rows[index], index) { startDrag(this@Holder) } }
                }
            }
        }
    }

    val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
    ) {
        override fun isLongPressDragEnabled() = false

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView,
            viewSize: Int,
            viewSizeOutOfBounds: Int,
            totalSize: Int,
            msSinceStartScroll: Long,
        ): Int {
            val maxStep = (recyclerView.resources.displayMetrics.density * 4).toInt().coerceAtLeast(1)
            return super.interpolateOutOfBoundsScroll(
                recyclerView, viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll,
            ).coerceIn(-maxStep, maxStep)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            source: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val old = source.bindingAdapterPosition
            val new = target.bindingAdapterPosition
            if (old !in rows.indices || new !in rows.indices) return false
            rows.add(new, rows.removeAt(old))
            notifyItemMoved(old, new)
            to = new
            syncVisiblePositions(recyclerView)
            recyclerView.post { syncVisiblePositions(recyclerView) }
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            dragging = false
            if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION && from != to) {
                this@EditorAdapter.onMove(from, to)
            }
            from = RecyclerView.NO_POSITION
            to = RecyclerView.NO_POSITION
        }
    })

    private fun startDrag(holder: Holder) {
        val index = holder.bindingAdapterPosition
        if (dragging || index !in rows.indices) return
        dragging = true
        from = index
        to = index
        touchHelper.startDrag(holder)
    }

    fun sync(items: List<T>) {
        if (dragging || rows == items) return
        if (rows.size != items.size) {
            rows.clear()
            rows.addAll(items)
            notifyDataSetChanged()
        } else {
            items.forEachIndexed { i, item -> if (rows[i] != item) rows[i] = item }
        }
    }

    private fun syncVisiblePositions(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i)) as EditorAdapter<T>.Holder
            holder.displayIndex = holder.bindingAdapterPosition
        }
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        })

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.displayIndex = position
    }
}
