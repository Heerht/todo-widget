package com.example.todowidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.todowidget.MainActivity
import com.example.todowidget.QuickAddActivity
import com.example.todowidget.R
import com.example.todowidget.data.TodoRepository

/**
 * 负责把“数据 + 尺寸”翻译成一份 [RemoteViews]。
 *
 * 小部件跑在桌面的进程里，不能直接 findViewById，也不能随便用自定义 View，
 * 只能通过 RemoteViews 这套受限的 API 描述界面。
 */
internal object WidgetRenderer {

    private const val REQ_OPEN_APP = 100
    private const val REQ_QUICK_ADD = 101
    private const val REQ_REFRESH_BASE = 200
    private const val REQ_ITEM_TEMPLATE_BASE = 300

    fun build(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
        val todos = TodoRepository.get(context).all()
        val done = todos.count { it.done }

        // 桌面给的尺寸（单位 dp），用来做简单自适应
        val options = manager.getAppWidgetOptions(widgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val compact = minHeight in 1..129   // 只有 2 格高：藏掉进度条
        val narrow = minWidth in 1..219     // 只有 2~3 格宽：藏掉刷新按钮

        val views = RemoteViews(context.packageName, R.layout.widget_todo)

        views.setTextViewText(
            R.id.widget_count,
            context.getString(R.string.widget_count, done, todos.size)
        )
        views.setViewVisibility(R.id.widget_progress, if (compact) View.GONE else View.VISIBLE)
        views.setProgressBar(R.id.widget_progress, todos.size.coerceAtLeast(1), done, false)
        views.setViewVisibility(R.id.widget_refresh, if (narrow) View.GONE else View.VISIBLE)

        // 点击事件：所有 PendingIntent 都必须是显式 Intent（Android 12+ 的安全要求）
        views.setOnClickPendingIntent(R.id.widget_header, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_add, quickAdd(context))
        views.setOnClickPendingIntent(R.id.widget_empty, quickAdd(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh(context, widgetId))

        // 可滚动列表：交给 RemoteViewsService 提供数据
        val adapterIntent = Intent(context, TodoWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // data 必须每个实例唯一，否则桌面上放两个小部件会共用同一个工厂
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, adapterIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // 列表条目的点击模板：条目自己只带 todoId，动作由模板补齐
        val template = Intent(context, TodoWidgetProvider::class.java).apply {
            action = TodoWidgetProvider.ACTION_TOGGLE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getBroadcast(
                context,
                REQ_ITEM_TEMPLATE_BASE + widgetId,
                template,
                mutableFlags()
            )
        )

        return views
    }

    // ------------------------------------------------------------ 点击行为

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, REQ_OPEN_APP, intent, immutableFlags())
    }

    /** 桌面直接输入文字体验很差，所以“+”只负责拉起 App 内的快速添加弹窗。 */
    private fun quickAdd(context: Context): PendingIntent {
        val intent = Intent(context, QuickAddActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(context, REQ_QUICK_ADD, intent, immutableFlags())
    }

    private fun refresh(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, TodoWidgetProvider::class.java).apply {
            action = TodoWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_REFRESH_BASE + widgetId,
            intent,
            immutableFlags()
        )
    }

    // ------------------------------------------------------------ flags

    /**
     * 集合型小部件的条目模板必须是 MUTABLE，系统才能把 fillInIntent 的 extras 填进去。
     * Android 12 起必须显式指定可变性，否则直接抛异常。
     */
    private fun mutableFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun immutableFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
