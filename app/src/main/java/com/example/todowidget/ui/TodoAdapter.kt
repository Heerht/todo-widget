package com.example.todowidget.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.example.todowidget.data.Todo
import com.example.todowidget.databinding.ItemTodoBinding

/**
 * App 内的列表适配器。用 BaseAdapter 而不是 RecyclerView，
 * 是为了让整个工程不引额外依赖，功能上完全够用。
 */
class TodoAdapter(
    private val onToggle: (Todo) -> Unit,
    private val onDelete: (Todo) -> Unit
) : BaseAdapter() {

    private val items = ArrayList<Todo>()

    fun submit(list: List<Todo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Todo = items[position]

    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemTodoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                .also { it.root.tag = it }
        } else {
            @Suppress("UNCHECKED_CAST")
            convertView.tag as ItemTodoBinding
        }

        val todo = items[position]

        // 复用 convertView 时必须先摘掉监听器，否则滚动时会误触发别的条目
        binding.check.setOnCheckedChangeListener(null)
        binding.check.isChecked = todo.done

        binding.title.text = todo.title
        binding.title.paintFlags = if (todo.done) {
            binding.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            binding.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        binding.title.alpha = if (todo.done) 0.45f else 1f

        binding.check.setOnCheckedChangeListener { _, _ -> onToggle(todo) }
        binding.root.setOnClickListener { onToggle(todo) }
        binding.delete.setOnClickListener { onDelete(todo) }

        return binding.root
    }
}
