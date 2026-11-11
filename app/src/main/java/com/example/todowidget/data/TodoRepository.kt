package com.example.todowidget.data

import android.content.Context
import android.content.SharedPreferences
import com.example.todowidget.widget.TodoWidgetProvider
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 待办数据仓库（单例）。
 *
 * App 界面和小部件共用同一份数据，这是“小部件依附于 App”的关键：
 * 桌面上的小部件并不持有自己的数据，它只是 App 数据的一个投影。
 *
 * 存储：SharedPreferences + JSON。数据量小、无迁移成本、零依赖。
 * 如果后续要接 Room / 网络，只需要替换本类内部实现，UI 和小部件代码不用动。
 */
class TodoRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val todos = ArrayList<Todo>()

    init {
        load()
    }

    // ---------------------------------------------------------------- 读

    fun all(): List<Todo> = ArrayList(todos)

    fun doneCount(): Int = todos.count { it.done }

    // ---------------------------------------------------------------- 写

    /** 新增一条待办，空白内容会被忽略并返回 null。 */
    fun add(title: String): Todo? {
        val text = title.trim()
        if (text.isEmpty()) return null

        val todo = Todo(id = nextId(), title = text)
        todos.add(0, todo) // 新的排在最前面
        persistAndNotify()
        return todo
    }

    fun setDone(id: Long, done: Boolean) {
        val index = todos.indexOfFirst { it.id == id }
        if (index < 0 || todos[index].done == done) return

        todos[index] = todos[index].copy(done = done)
        persistAndNotify()
    }

    /** 小部件点击条目时用：在“已完成 / 未完成”之间切换。 */
    fun toggle(id: Long) {
        val index = todos.indexOfFirst { it.id == id }
        if (index < 0) return

        todos[index] = todos[index].copy(done = !todos[index].done)
        persistAndNotify()
    }

    fun remove(id: Long) {
        if (!todos.removeAll { it.id == id }) return
        persistAndNotify()
    }

    /** 清空已完成，返回被删掉的数量。 */
    fun clearDone(): Int {
        val removed = todos.count { it.done }
        if (removed == 0) return 0

        todos.removeAll { it.done }
        persistAndNotify()
        return removed
    }

    // ---------------------------------------------------------------- 存储

    private fun nextId(): Long {
        val next = prefs.getLong(KEY_NEXT_ID, 0L) + 1L
        prefs.edit().putLong(KEY_NEXT_ID, next).apply()
        return next
    }

    private fun load() {
        todos.clear()

        val raw = prefs.getString(KEY_TODOS, null)
        if (raw != null) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    todos.add(
                        Todo(
                            id = item.optLong("id", i + 1L),
                            title = item.optString("title").ifBlank { "未命名任务" },
                            done = item.optBoolean("done", false),
                            createdAt = item.optLong("createdAt", 0L)
                        )
                    )
                }
            } catch (error: JSONException) {
                // 本地数据损坏时当作空列表处理，不让 App 直接崩溃
                todos.clear()
            }
        }

        // 首次安装给几条示例，方便刚拖到桌面时就能看到效果（不需要可删掉这段）
        if (todos.isEmpty() && !prefs.getBoolean(KEY_SEEDED, false)) {
            seed()
        }
    }

    private fun seed() {
        val now = System.currentTimeMillis()
        listOf(
            "喝一杯水，开始新的一天",
            "回复客户的需求邮件",
            "晚上跑步 3 公里"
        ).forEachIndexed { index, title ->
            todos.add(Todo(id = nextId(), title = title, createdAt = now - index * 60_000L))
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        todos.forEach { todo ->
            array.put(
                JSONObject().apply {
                    put("id", todo.id)
                    put("title", todo.title)
                    put("done", todo.done)
                    put("createdAt", todo.createdAt)
                }
            )
        }
        prefs.edit().putString(KEY_TODOS, array.toString()).apply()
    }

    private fun persistAndNotify() {
        persist()
        // 数据变了就通知桌面小部件重绘，App 与桌面永远保持一致
        TodoWidgetProvider.notifyChanged(appContext)
    }

    companion object {
        private const val PREFS_NAME = "todo_store"
        private const val KEY_TODOS = "todos"
        private const val KEY_NEXT_ID = "next_id"
        private const val KEY_SEEDED = "seeded"

        @Volatile
        private var instance: TodoRepository? = null

        fun get(context: Context): TodoRepository =
            instance ?: synchronized(this) {
                instance ?: TodoRepository(context).also { instance = it }
            }
    }
}
