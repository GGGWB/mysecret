package com.mysecret.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mysecret.R
import com.mysecret.data.SessionManager
import com.mysecret.data.VaultRepository
import com.mysecret.data.model.Category
import com.mysecret.data.model.Credential
import com.mysecret.databinding.ActivityEditorBinding
import com.mysecret.databinding.DialogGeneratorBinding
import com.mysecret.security.PasswordGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class CredentialEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private var editingId: String? = null
    private var selectedCategoryId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingId = intent.getStringExtra("credential_id")
        binding.toolbar.title = if (editingId != null) getString(R.string.edit_title) else getString(R.string.add_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (editingId != null) {
            loadForEdit()
        }

        binding.btnCategory.setOnClickListener { showCategoryPicker() }
        binding.btnGenerate.setOnClickListener { showGeneratorDialog() }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun loadForEdit() {
        val credential = SessionManager.getVault()?.entries?.find { it.id == editingId } ?: return
        binding.apply {
            etName.setText(credential.name)
            etUsername.setText(credential.username)
            etPassword.setText(credential.password)
            etUrl.setText(credential.url)
            etNotes.setText(credential.notes)
            selectedCategoryId = credential.categoryId
            updateCategoryLabel()
        }
    }

    private fun updateCategoryLabel() {
        val cat = SessionManager.getVault()?.getCategoryById(selectedCategoryId)
        binding.btnCategory.text = cat?.name ?: "未分类"
    }

    private fun showCategoryPicker() {
        val vault = SessionManager.getVault() ?: return
        val stats = vault.getCategoryStats()
        val items = mutableListOf("未分类") + stats.map { it.first.name }

        AlertDialog.Builder(this)
            .setTitle("选择分类")
            .setSingleChoiceItems(items.toTypedArray(), items.indexOf(if (selectedCategoryId.isEmpty()) "未分类" else stats.find { it.first.id == selectedCategoryId }?.first?.name ?: "未分类")) { dialog, which ->
                selectedCategoryId = if (which == 0) "" else stats[which - 1].first.id
                updateCategoryLabel()
                dialog.dismiss()
            }
            .setNeutralButton("新建分类") { _, _ ->
                showNewCategory { newCat ->
                    selectedCategoryId = newCat.id
                    updateCategoryLabel()
                }
            }
            .show()
    }

    private fun showNewCategory(onCreated: (Category) -> Unit) {
        val input = android.widget.EditText(this)
        input.hint = "分类名称"
        AlertDialog.Builder(this)
            .setTitle("新建分类")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val pwd = SessionManager.getMasterPassword()
                        if (pwd == null) {
                            Snackbar.make(binding.root, "会话已过期，请重新解锁", Snackbar.LENGTH_LONG).show()
                            return@launch
                        }
                        val vault = SessionManager.getVault()
                        if (vault == null) {
                            Snackbar.make(binding.root, "密码库未加载", Snackbar.LENGTH_LONG).show()
                            return@launch
                        }
                        val createdCat = withContext(Dispatchers.IO) {
                            vault.createCategory(name).also {
                                val repo = VaultRepository.get(this@CredentialEditorActivity)
                                repo.save(vault, pwd)
                                val updated = repo.load(pwd)
                                SessionManager.updateVault(updated)
                            }
                        }
                        onCreated(createdCat)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (name.isEmpty()) {
            binding.tilName.error = getString(R.string.field_name_empty)
            return
        }
        if (username.isEmpty()) {
            binding.tilUsername.error = getString(R.string.field_username_empty)
            return
        }

        val masterPwd = SessionManager.getMasterPassword() ?: run { finish(); return }
        val now = System.currentTimeMillis()
        val existing = editingId?.let {
            SessionManager.getVault()?.entries?.find { c -> c.id == it }
        }
        val credential = Credential(
            id = editingId ?: UUID.randomUUID().toString(),
            categoryId = selectedCategoryId,
            name = name,
            username = username,
            password = password,
            url = binding.etUrl.text.toString().trim(),
            notes = binding.etNotes.text.toString().trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val repo = VaultRepository.get(this@CredentialEditorActivity)
                    repo.upsert(credential, masterPwd)
                    val updatedVault = repo.load(masterPwd)
                    SessionManager.updateVault(updatedVault)
                }
                Snackbar.make(binding.root, R.string.saved_success, Snackbar.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                binding.btnSave.isEnabled = true
                Snackbar.make(binding.root, e.message ?: "保存失败", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showGeneratorDialog() {
        val dialogBinding = DialogGeneratorBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvLength.text = getString(R.string.gen_length, 16)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.generator_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.gen_apply) { _, _ ->
                val options = PasswordGenerator.Options(
                    length = dialogBinding.seekLength.progress,
                    useUppercase = dialogBinding.cbUppercase.isChecked,
                    useLowercase = dialogBinding.cbLowercase.isChecked,
                    useDigits = dialogBinding.cbDigits.isChecked,
                    useSymbols = dialogBinding.cbSymbols.isChecked
                )
                binding.etPassword.setText(PasswordGenerator.generate(options))
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        fun regenerate() {
            val options = PasswordGenerator.Options(
                length = dialogBinding.seekLength.progress,
                useUppercase = dialogBinding.cbUppercase.isChecked,
                useLowercase = dialogBinding.cbLowercase.isChecked,
                useDigits = dialogBinding.cbDigits.isChecked,
                useSymbols = dialogBinding.cbSymbols.isChecked
            )
            dialogBinding.tvGeneratedPassword.text = PasswordGenerator.generate(options)
        }

        dialogBinding.seekLength.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                dialogBinding.tvLength.text = getString(R.string.gen_length, p)
                if (fromUser) regenerate()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        dialogBinding.cbUppercase.setOnCheckedChangeListener { _, _ -> regenerate() }
        dialogBinding.cbLowercase.setOnCheckedChangeListener { _, _ -> regenerate() }
        dialogBinding.cbDigits.setOnCheckedChangeListener { _, _ -> regenerate() }
        dialogBinding.cbSymbols.setOnCheckedChangeListener { _, _ -> regenerate() }
        dialogBinding.btnRegenerate.setOnClickListener { regenerate() }

        regenerate()
        dialog.show()
    }
}
