package com.mysecret.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.mysecret.R

/**
 * 安全剪贴板工具：复制后在指定时间后自动清除，防止密码残留在剪贴板。
 */
object SecureClipboard {

    private const val CLEAR_DELAY_MILLIS = 30_000L  // 30 秒后自动清除

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    fun copy(context: Context, label: String, text: String, toastMsg: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()

        // 取消上一次的清除任务，重新计时
        pendingClear?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (clipboard.hasPrimaryClip()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            Toast.makeText(context, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
        }
        pendingClear = runnable
        handler.postDelayed(runnable, CLEAR_DELAY_MILLIS)
    }
}
