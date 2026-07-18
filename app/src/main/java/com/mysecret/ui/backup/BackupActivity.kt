package com.mysecret.ui.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mysecret.R
import com.mysecret.data.BackupConfigManager
import com.mysecret.data.SessionManager
import com.mysecret.data.model.BackupConfig
import com.mysecret.data.model.BackupType
import com.mysecret.backup.BackupManager
import com.mysecret.databinding.ActivityBackupBinding
import com.mysecret.databinding.DialogRestoreBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private lateinit var backupManager: BackupManager
    private var selectedBackupUri: Uri? = null

    // 下拉选项与枚举的映射
    private val typeLabels = arrayOf(
        "不使用远程备份",
        "WebDAV",
        "SMB / Samba"
    )
    private val typeValues = arrayOf(BackupType.NONE, BackupType.WEBDAV, BackupType.SMB)

    companion object {
        private const val REQ_PICK_BACKUP_FILE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        backupManager = BackupManager(this)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupTypeSpinner()
        loadConfig()
        setupListeners()
        refreshStatus()
    }

    private fun setupTypeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerType.adapter = adapter

        binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                updateServerHint(typeValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateServerHint(type: BackupType) {
        binding.tilServer.hint = when (type) {
            BackupType.WEBDAV -> "https://example.com/dav/"
            BackupType.SMB -> "smb://192.168.1.100/share/"
            BackupType.NONE -> "服务器地址"
        }
    }

    private fun loadConfig() {
        val config = BackupConfigManager.load(this)
        val idx = typeValues.indexOf(config.type).coerceAtLeast(0)
        binding.spinnerType.setSelection(idx)
        binding.etServer.setText(config.server)
        binding.etUsername.setText(config.username)
        binding.etPassword.setText(config.password)
        binding.etRemotePath.setText(config.remotePath)
        updateServerHint(config.type)
    }

    private fun setupListeners() {
        binding.btnExportLocal.setOnClickListener { exportLocal() }
        binding.btnSaveConfig.setOnClickListener { saveConfig() }
        binding.btnTest.setOnClickListener { testConnection() }
        binding.btnBackupNow.setOnClickListener { backupNow() }
        binding.btnRestore.setOnClickListener { showRestoreDialog() }
    }

    private fun getCurrentConfig(): BackupConfig {
        val pos = binding.spinnerType.selectedItemPosition
        val type = typeValues.getOrElse(pos) { BackupType.NONE }
        return BackupConfig(
            type = type,
            server = binding.etServer.text.toString().trim(),
            username = binding.etUsername.text.toString().trim(),
            password = binding.etPassword.text.toString(),
            remotePath = binding.etRemotePath.text.toString().trim()
                .ifEmpty { "mysecret_backup.mysecret" },
            lastBackupTime = BackupConfigManager.load(this).lastBackupTime
        )
    }

    private fun saveConfig() {
        val config = getCurrentConfig()
        if (config.type != BackupType.NONE && config.server.isBlank()) {
            Snackbar.make(binding.root, R.string.backup_field_server_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        BackupConfigManager.save(this, config)
        Snackbar.make(binding.root, R.string.backup_config_saved, Snackbar.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun testConnection() {
        val config = getCurrentConfig()
        if (config.type == BackupType.NONE) {
            Snackbar.make(binding.root, R.string.backup_no_config, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (config.server.isBlank()) {
            Snackbar.make(binding.root, R.string.backup_field_server_empty, Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.btnTest.isEnabled = false
        binding.btnTest.text = getString(R.string.backup_testing)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                backupManager.testConnection(config)
            }
            binding.btnTest.isEnabled = true
            binding.btnTest.text = getString(R.string.backup_btn_test)
            result.onSuccess {
                Snackbar.make(binding.root, R.string.backup_test_success, Snackbar.LENGTH_SHORT).show()
            }.onFailure { e ->
                Snackbar.make(
                    binding.root,
                    getString(R.string.backup_test_failed, e.message ?: "未知错误"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun backupNow() {
        var config = BackupConfigManager.load(this)
        if (!config.isConfigured()) {
            val currentConfig = getCurrentConfig()
            if (currentConfig.isConfigured()) {
                BackupConfigManager.save(this, currentConfig)
                config = currentConfig
            } else {
                Snackbar.make(binding.root, R.string.backup_no_config, Snackbar.LENGTH_SHORT).show()
                return
            }
        }

        if (SessionManager.getMasterPassword() == null) {
            finish()
            return
        }

        binding.btnBackupNow.isEnabled = false
        binding.btnBackupNow.text = getString(R.string.backup_backing_up)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                backupManager.backupToRemote(SessionManager.getMasterPassword())
            }
            binding.btnBackupNow.isEnabled = true
            binding.btnBackupNow.text = getString(R.string.backup_btn_backup_now)
            result.onSuccess {
                Snackbar.make(binding.root, R.string.backup_success, Snackbar.LENGTH_SHORT).show()
                refreshStatus()
            }.onFailure { e ->
                Snackbar.make(
                    binding.root,
                    getString(R.string.backup_failed, e.message ?: "未知错误"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun exportLocal() {
        if (SessionManager.getMasterPassword() == null) {
            finish()
            return
        }

        binding.btnExportLocal.isEnabled = false
        binding.btnExportLocal.text = getString(R.string.backup_exporting)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                backupManager.exportLocalBackup(SessionManager.getMasterPassword())
            }
            binding.btnExportLocal.isEnabled = true
            binding.btnExportLocal.text = getString(R.string.backup_export_local)
            result.onSuccess { file ->
                Snackbar.make(
                    binding.root,
                    getString(R.string.backup_local_path, file.absolutePath),
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure { e ->
                Snackbar.make(
                    binding.root,
                    getString(R.string.backup_failed, e.message ?: "未知错误"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showRestoreDialog() {
        val dialogBinding = DialogRestoreBinding.inflate(LayoutInflater.from(this))

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.backup_restore_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.backup_restore_btn, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialogBinding.btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, REQ_PICK_BACKUP_FILE)
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val uri = selectedBackupUri
                if (uri == null) {
                    Snackbar.make(dialogBinding.root, R.string.backup_import_no_file, Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val pwdStr = dialogBinding.etRestorePassword.text.toString()
                if (pwdStr.isEmpty()) {
                    dialogBinding.tilRestorePassword.error = getString(R.string.error_password_too_short)
                    return@setOnClickListener
                }

                AlertDialog.Builder(this)
                    .setMessage(R.string.backup_restore_confirm)
                    .setPositiveButton(R.string.backup_restore_confirm_yes) { _, _ ->
                        doRestore(uri, pwdStr.toCharArray(), dialog)
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        }

        dialog.show()
    }

    private fun doRestore(uri: Uri, password: CharArray, parentDialog: AlertDialog) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                backupManager.restoreFromBackup(uri, password, SessionManager.getMasterPassword())
            }
            password.fill(0.toChar())
            result.onSuccess { count: Int ->
                parentDialog.dismiss()
                Snackbar.make(
                    binding.root,
                    getString(R.string.backup_restore_success, count),
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure { e: Throwable ->
                Snackbar.make(
                    binding.root,
                    getString(R.string.backup_restore_failed, e.message ?: "未知错误"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_BACKUP_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedBackupUri = uri
                val fileName = uri.lastPathSegment ?: uri.toString()
                Snackbar.make(binding.root, fileName, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshStatus() {
        val config = BackupConfigManager.load(this)
        binding.tvStatus.text = if (config.isConfigured()) {
            val typeName = when (config.type) {
                BackupType.WEBDAV -> "WebDAV"
                BackupType.SMB -> "SMB"
                else -> "未知"
            }
            "已配置：$typeName"
        } else {
            "未配置远程备份"
        }

        binding.tvLastBackup.text = if (config.lastBackupTime > 0) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(config.lastBackupTime))
            "上次备份：$dateStr"
        } else {
            "从未备份"
        }
    }
}
