package com.example.todowidget

import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.todowidget.data.TodoRepository
import com.example.todowidget.databinding.ActivityQuickAddBinding

/**
 * 小部件上点“+”打开的轻量输入框。
 *
 * 为什么不在小部件里直接放 EditText？
 * RemoteViews 虽然从 API 28 起支持 EditText，但焦点、键盘、输入法的行为在桌面进程里
 * 非常不可控，各家启动器表现不一致。用透明的 Dialog Activity 体验稳定得多。
 */
class QuickAddActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuickAddBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAdd.setOnClickListener { submit() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        binding.input.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun submit() {
        val text = binding.input.text?.toString().orEmpty().trim()
        if (text.isEmpty()) {
            binding.inputLayout.error = getString(R.string.error_empty)
            return
        }
        TodoRepository.get(this).add(text)
        Toast.makeText(this, R.string.added, Toast.LENGTH_SHORT).show()
        finish()
    }
}
