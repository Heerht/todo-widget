package com.example.todowidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.todowidget.R
import com.example.todowidget.data.TodoRepository

/**
 * 待办小部件的“大脑”。
 *
 * 系统只会在这些时机回调它：
 *  - onUpdate：小部件被拖到桌面、系统按 updatePeriodMillis 定时刷新、App 主动触发更新；
 *  - onAppWidgetOptionsChanged：用户拖动边缘改变了小部件尺寸；
 *  - onReceive：收到了我们自己发的广播（勾选、刷新）。
 *
 * 这里刻意不做耗时操作，所有数据都来自本地的 [TodoRepository]。
 */
class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(
                widgetId,
                WidgetRenderer.build(context, appWidgetManager, widgetId)
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // 尺寸变了就重新排版：变窄隐藏刷新按钮，变矮隐藏进度条
        appWidgetManager.updateAppWidget(
            appWidgetId,
            WidgetRenderer.build(context, appWidgetManager, appWidgetId)
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TOGGLE -> {
                val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
                if (todoId > 0L) {
                    // toggle 内部会写盘并回调 notifyChanged，所以这里不用再刷新
                    TodoRepository.get(context).toggle(todoId)
                }
            }

            ACTION_CLEAR_DONE -> TodoRepository.get(context).clearDone()

            ACTION_REFRESH -> notifyChanged(context)
        }
    }

    companion object {
        /** 勾选/取消勾选某一条待办。 */
        const val ACTION_TOGGLE = "com.example.todowidget.action.TOGGLE"

        /** 清空已完成（预留给以后在桌面加按钮用）。 */
        const val ACTION_CLEAR_DONE = "com.example.todowidget.action.CLEAR_DONE"

        /** 手动刷新。 */
        const val ACTION_REFRESH = "com.example.todowidget.action.REFRESH"

        const val EXTRA_TODO_ID = "com.example.todowidget.extra.TODO_ID"

        /**
         * 把最新的数据推到桌面上的所有小部件实例。
         * App 内任何一次数据变更（增删改）都会走到这里。
         */
        fun notifyChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodoWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            ids.forEach { widgetId ->
                manager.updateAppWidget(
                    widgetId,
                    WidgetRenderer.build(context, manager, widgetId)
                )
            }

            // 外壳（标题、进度）重绘完了，但 ListView 的数据要单独通知工厂重新查询，
            // 少这一行就会出现“进度变了、列表还是旧的”。
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }
}
