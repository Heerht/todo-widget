package com.example.todowidget.data

/**
 * 一条待办任务。
 *
 * 之所以用不可变 data class，是为了在列表刷新、小部件重绘时不会出现
 * 同一个对象被两处同时修改的状态错乱问题。
 */
data class Todo(
    val id: Long,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
