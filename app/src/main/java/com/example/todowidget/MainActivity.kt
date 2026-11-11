package com.example.todowidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.todowidget.data.Todo
import com.example.todowidget.data.TodoRepository
import com.example.todowidget.databinding.ActivityMainBinding
import com.example.todowidget.databinding.DialogAddTodoBinding
import com.example.todowidget.ui.TodoAdapter
import com.example.todowidget.widget.TodoWidgetProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TodoRepository
    private lateinit var adapter: TodoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TodoRepository.get(this)
        adapter = TodoAdapter(
            onToggle = { todo ->
                repository.setDone(todo.id, !todo.done)
                render()
            },
            onDelete = { todo -> confirmDelete(todo) }
        )

        binding.list.adapter = adapter
        binding.list.emptyView = binding.empty

        binding.fabAdd.setOnClickListener { showAddDialog() }
        binding.empty.setOnClickListener { showAddDialog() }
        binding.btnPin.setOnClickListener { pinWidget() }
        binding.btnClear.setOnClickListener { clearDone() }

        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ------------------------------------------------------------ 渲染

    private fun render() {
        val todos = repository.all()
        val done = todos.count { it.done }

        adapter.submit(todos)
        binding.subtitle.text = getString(R.string.header_subtitle, today(), done, todos.size)
        binding.progress.max = todos.size.coerceAtLeast(1)
        binding.progress.progress = done
        binding.btnClear.isEnabled = done > 0
        binding.btnClear.alpha = if (done > 0) 1f else 0.35f
    }

    private fun today(): String =
        SimpleDateFormat("M 月 d 日 · EEEE", Locale.CHINA).format(Date())

    // ------------------------------------------------------------ 交互

    private fun showAddDialog() {
        val dialogBinding = DialogAddTodoBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_add_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()

        fun submit() {
            val text = dialogBinding.input.text?.toString().orEmpty().trim()
            if (text.isEmpty()) {
                dialogBinding.inputLayout.error = getString(R.string.error_empty)
                return
            }
            repository.add(text)
            dialog.dismiss()
            render()
        }

        dialog.setOnShowListener {
            dialogBinding.input.requestFocus()
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { submit() }
            dialogBinding.input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submit()
                    true
                } else {
                    false
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(todo: Todo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(todo.title)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                repository.remove(todo.id)
                render()
            }
            .show()
    }

    private fun clearDone() {
        val removed = repository.clearDone()
        if (removed == 0) {
            toast(getString(R.string.nothing_to_clear))
            return
        }
        render()
        toast(getString(R.string.cleared_done, removed))
    }

    /**
     * 把本 App 的小部件“钉”到桌面。
     * 这是 App 主动请求添加小部件的官方方式，比让用户自己去部件列表里翻更好找。
     * 注意：只能请求，用户仍然要在系统弹窗里点确认。
     */
    private fun pinWidget() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(getString(R.string.pin_unsupported))
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            toast(getString(R.string.pin_unsupported))
            return
        }
        val provider = ComponentName(this, TodoWidgetProvider::class.java)
        manager.requestPinAppWidget(provider, null, null)
    }

    private fun toast(text: CharSequence) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
