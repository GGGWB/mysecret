package com.mysecret.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mysecret.R
import com.mysecret.data.SessionManager
import com.mysecret.data.VaultRepository
import com.mysecret.security.SecureClipboard
import com.mysecret.databinding.ActivityDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CredentialDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private var credentialId: String? = null
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialId = intent.getStringExtra("credential_id")
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnEdit.setOnClickListener {
            startActivity(Intent(this, com.mysecret.ui.editor.CredentialEditorActivity::class.java).apply {
                putExtra("credential_id", credentialId)
            })
        }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    override fun onResume() {
        super.onResume()
        displayCredential()
    }

    private fun displayCredential() {
        val vault = SessionManager.getVault()
        if (vault == null) {
            finish()
            return
        }
        val found = credentialId?.let {
            vault.entries.find { c -> c.id == it }
        }
        
        if (found == null) {
            Snackbar.make(binding.root, "未找到该条目", Snackbar.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val credential = found

        binding.tvName.text = credential.name

        // 用户名
        binding.tvUsername.text = credential.username
        binding.btnCopyUsername.setOnClickListener {
            SecureClipboard.copy(this, "username", credential.username, getString(R.string.copied_username))
        }

        // 密码
        binding.tvPassword.text = "••••••••••••"
        binding.tvPassword.tag = credential.password
        binding.btnToggleVisibility.setOnClickListener {
            passwordVisible = !passwordVisible
            binding.tvPassword.text = if (passwordVisible) credential.password else "••••••••••••"
            binding.btnToggleVisibility.setImageResource(
                if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
        }
        binding.btnCopyPassword.setOnClickListener {
            SecureClipboard.copy(this, "password", credential.password, getString(R.string.copied_password))
        }

        // 网址
        if (credential.url.isNotBlank()) {
            binding.cardUrl.visibility = View.VISIBLE
            binding.tvUrl.text = credential.url
            binding.btnCopyUrl.setOnClickListener {
                SecureClipboard.copy(this, "url", credential.url, "已复制网址")
            }
        } else {
            binding.cardUrl.visibility = View.GONE
        }

        // 备注
        if (credential.notes.isNotBlank()) {
            binding.cardNotes.visibility = View.VISIBLE
            binding.tvNotes.text = credential.notes
        } else {
            binding.cardNotes.visibility = View.GONE
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.confirm_delete_yes) { _, _ -> delete() }
            .setNegativeButton(R.string.confirm_delete_no, null)
            .show()
    }

    private fun delete() {
        val id = credentialId ?: return
        val masterPwd = SessionManager.getMasterPassword() ?: run { finish(); return }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val repo = VaultRepository.get(this@CredentialDetailActivity)
                    repo.delete(id, masterPwd)
                    val updatedVault = repo.load(masterPwd)
                    SessionManager.updateVault(updatedVault)
                }
                Snackbar.make(binding.root, R.string.deleted_success, Snackbar.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Snackbar.make(binding.root, e.message ?: "删除失败", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
