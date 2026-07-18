package com.mysecret.ui.unlock

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mysecret.R
import com.mysecret.data.PrefsManager
import com.mysecret.data.SessionManager
import com.mysecret.data.VaultRepository
import com.mysecret.data.model.Vault
import com.mysecret.databinding.ActivityUnlockBinding
import com.mysecret.security.CryptoVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnlockBinding
    private var isSetupMode: Boolean = false
    private var progressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isSetupMode = !PrefsManager.isVaultInitialized(this)
        updateUiMode()

        binding.btnAction.setOnClickListener {
            val password = CharArray(binding.etPassword.text?.length ?: 0).also {
                binding.etPassword.text?.getChars(0, it.size, it, 0)
            }
            if (password.isEmpty()) {
                binding.tilPassword.error = getString(R.string.error_password_too_short)
                return@setOnClickListener
            }
            if (isSetupMode) handleSetup(password) else handleUnlock(password)
        }
    }

    private fun updateUiMode() {
        if (isSetupMode) {
            binding.tvTitle.text = getString(R.string.setup_title)
            binding.tilPassword.hint = getString(R.string.setup_hint)
            binding.tilConfirmPassword.visibility = View.VISIBLE
            binding.tvWarning.visibility = View.VISIBLE
            binding.btnAction.text = getString(R.string.setup_btn)
        } else {
            binding.tvTitle.text = getString(R.string.unlock_title)
            binding.tilPassword.hint = getString(R.string.unlock_hint)
            binding.tilConfirmPassword.visibility = View.GONE
            binding.tvWarning.visibility = View.GONE
            binding.btnAction.text = getString(R.string.unlock_btn)
        }
    }

    private fun handleSetup(password: CharArray) {
        if (password.size < 8) {
            binding.tilPassword.error = getString(R.string.error_password_too_short)
            password.fill(0.toChar())
            return
        }
        val confirmText = binding.etConfirmPassword.text.toString()
        if (!password.contentEquals(confirmText.toCharArray())) {
            binding.tilConfirmPassword.error = getString(R.string.error_password_mismatch)
            password.fill(0.toChar())
            return
        }

        lifecycleScope.launch {
            showLoading(true, getString(R.string.loading_setup))
            try {
                withContext(Dispatchers.Default) {
                    val token = CryptoVault.generateVerificationToken(password)
                    PrefsManager.saveVerificationToken(this@UnlockActivity, token)
                    PrefsManager.setVaultInitialized(this@UnlockActivity)
                    val repo = VaultRepository.get(this@UnlockActivity)
                    repo.save(Vault(), password)
                    val loaded = repo.load(password)
                    SessionManager.unlock(password, loaded)
                }
                password.fill(0.toChar())
                proceedToMain()
            } catch (e: Exception) {
                showLoading(false)
                Snackbar.make(binding.root, e.message ?: "初始化失败", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun handleUnlock(password: CharArray) {
        val token = PrefsManager.getVerificationToken(this)
        if (token == null) {
            isSetupMode = true
            updateUiMode()
            password.fill(0.toChar())
            return
        }

        lifecycleScope.launch {
            showLoading(true, getString(R.string.loading_unlock))
            val ok = withContext(Dispatchers.Default) {
                if (!CryptoVault.verify(password, token)) return@withContext false
                val repo = VaultRepository.get(this@UnlockActivity)
                val vault = try { repo.load(password) } catch (e: Exception) { Vault() }
                SessionManager.unlock(password, vault)
                true
            }
            password.fill(0.toChar())
            showLoading(false)
            if (ok) {
                proceedToMain()
            } else {
                binding.tilPassword.error = getString(R.string.error_wrong_password)
            }
        }
    }

    private fun proceedToMain() {
        startActivity(Intent(this, com.mysecret.ui.main.MainActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean, message: String = getString(R.string.loading_unlock)) {
        if (show) {
            if (progressDialog == null) {
                progressDialog = Dialog(this).apply {
                    setContentView(R.layout.dialog_loading)
                    setCancelable(false)
                    setCanceledOnTouchOutside(false)
                    // 让窗口背景透明，使卡片的圆角白底裸显示，外加 Dialog 自带半透明遮罩
                    window?.setBackgroundDrawableResource(android.R.color.transparent)
                    window?.setDimAmount(0.35f)
                }
            }
            progressDialog?.findViewById<TextView>(R.id.tvLoadingMessage)?.text = message
            progressDialog?.show()
            binding.btnAction.isEnabled = false
            binding.btnAction.text = message
        } else {
            progressDialog?.dismiss()
            binding.btnAction.isEnabled = true
            binding.btnAction.text = if (isSetupMode) getString(R.string.setup_btn)
                                    else getString(R.string.unlock_btn)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        binding.etPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
    }
}
