package com.example.todowidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.example.todowidget.R
import com.example.todowidget.data.Todo
import com.example.todowidget.data.TodoRepository

private val PAINT_NORMAL = Paint.ANTI_ALIAS_FLAG
private val PAINT_DONE = Paint.ANTI_ALIAS_FLAG or Paint.STRIKE_THRU_TEXT_FLAG

/**
 * 集合型小部件的数据源。
 * 桌面进程会通过 Binder 调用这里拿每一条的 RemoteViews。
 */
class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodoViewsFactory(applicationContext)
}

private class TodoViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var items: List<Todo> = emptyList()

    override fun onCreate() {
        // 没有需要提前初始化的东西
    }

    override fun onDataSetChanged() {
        // 每次 notifyAppWidgetViewDataChanged 之后系统都会回到这里重新取数
        items = TodoRepository.get(context).all()
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val todo = items.getOrNull(position) ?: return null

        val views = RemoteViews(context.packageName, R.layout.widget_todo_item)
        views.setTextViewText(R.id.item_title, todo.title)
        views.setTextColor(
            R.id.item_title,
            ContextCompat.getColor(
                context,
                if (todo.done) R.color.widget_text_done else R.color.widget_text_primary
            )
        )
        views.setImageViewResource(
            R.id.item_check,
            if (todo.done) R.drawable.ic_check_circle else R.drawable.ic_circle_outline
        )
        // 已完成加删除线
        views.setInt(
            R.id.item_title,
            "setPaintFlags",
            if (todo.done) PAINT_DONE else PAINT_NORMAL
        )

        // 条目自己只负责带上 todoId，具体跳到哪个广播由 Provider 上的模板决定
        views.setOnClickFillInIntent(
            R.id.item_root,
            Intent().apply { putExtra(TodoWidgetProvider.EXTRA_TODO_ID, todo.id) }
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
