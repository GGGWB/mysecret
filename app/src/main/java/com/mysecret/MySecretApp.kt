package com.mysecret

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mysecret.data.SessionManager

/**
 * Application 入口：监听整个 app 进程的前后台切换。
 *
 * 安全策略：app 退到后台（ON_STOP）时立即锁定会话，
 * 用户从后台返回时必须重新输入主密码，防止密码库在后台被窥视。
 */
class MySecretApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // app 进入后台：立即锁定，清空内存中的主密码与密码库
                if (!SessionManager.locked) {
                    SessionManager.lock()
                }
            }
        })
    }
}
