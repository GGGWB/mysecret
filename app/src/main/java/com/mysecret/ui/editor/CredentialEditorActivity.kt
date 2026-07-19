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

    override fun onResume() {
        super.onResume()
        // app 从后台返回时若已锁定，直接回解锁页
        if (SessionManager.locked || SessionManager.getMasterPassword() == null) {
            finish()
            return
        }
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
        binding.tvCategoryLabel.text = cat?.name ?: getString(R.string.category_not_classified)
        binding.tvCategoryEmoji.text = cat?.emoji ?: "📁"
    }

    private fun showCategoryPicker() {
        val vault = SessionManager.getVault() ?: return
        val stats = vault.getCategoryStats()
        val cats = mutableListOf<Category>()

        // 添加"未分类"选项（与筛选弹窗的"全部"对应，这里语义是不归属任何分类）
        cats.add(Category(id = "", name = getString(R.string.category_not_classified), emoji = "📁", color = 0xFF6750A4.toInt(), sortOrder = 0))
        for ((cat, _) in stats) {
            cats.add(cat)
        }

        // 复用筛选分类的 ChipGroup 弹窗布局
        val dialogView = layoutInflater.inflate(R.layout.dialog_category_selector, null)
        val chipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupCategories)

        // 底部按钮改为"新建分类"
        val btnBottom = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnManageCategories)
        btnBottom.text = getString(R.string.category_new)
        btnBottom.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_add)

        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setGravity(android.view.Gravity.CENTER)
            window.attributes?.windowAnimations = R.style.DialogAnimation
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            window.setLayout(
                (metrics.widthPixels * 0.9f).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setDimAmount(0.35f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.setDimAmount(0.45f)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes?.setBlurBehindRadius(12)
            }
        }

        // 动态生成 Chip 胶囊
        for (cat in cats) {
            val selected = selectedCategoryId == cat.id
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = "${cat.emoji}  ${cat.name}"
                isCheckable = true
                isChecked = selected
                isClickable = true
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (selected) androidx.core.content.ContextCompat.getColor(this@CredentialEditorActivity, R.color.md_primary)
                    else androidx.core.content.ContextCompat.getColor(this@CredentialEditorActivity, R.color.chip_off_color)
                )
                setTextColor(
                    if (selected) androidx.core.content.ContextCompat.getColor(this@CredentialEditorActivity, R.color.md_on_primary)
                    else androidx.core.content.ContextCompat.getColor(this@CredentialEditorActivity, R.color.md_on_surface)
                )
                chipMinHeight = resources.getDimension(R.dimen.chip_min_height_default)
                textStartPadding = resources.getDimension(R.dimen.chip_text_padding_default)
                textEndPadding = resources.getDimension(R.dimen.chip_text_padding_default)
                chipCornerRadius = resources.getDimension(R.dimen.chip_radius_default)
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            chip.setOnClickListener {
                selectedCategoryId = cat.id
                updateCategoryLabel()
                dialog.dismiss()
            }
            chipGroup.addView(chip)
        }

        dialog.show()

        // 底部"新建分类"按钮
        btnBottom.setOnClickListener {
            dialog.dismiss()
            showNewCategory { newCat ->
                selectedCategoryId = newCat.id
                updateCategoryLabel()
            }
        }
    }

    /**
     * 新建分类弹窗：复用主页的卡片式 emoji 选择弹窗（dialog_category_input.xml）。
     * emoji 网格 + 分类名输入框 + 取消/创建按钮。
     */
    private fun showNewCategory(onCreated: (Category) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_category_input, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvTitle)
        val ivIcon = dialogView.findViewById<android.widget.TextView>(R.id.ivTitleIcon)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCategoryName)
        val tilName = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCategoryName)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)
        val gridEmoji = dialogView.findViewById<androidx.gridlayout.widget.GridLayout>(R.id.gridEmoji)

        tvTitle.text = getString(R.string.category_new)
        btnConfirm.text = getString(R.string.category_create)

        // 当前选中的 emoji（默认 📁）
        val selectedEmoji = arrayOf("📁")

        // 预设 emoji 列表（与主页一致）
        val presetEmojis = listOf(
            "📁", "💼", "💬", "🏦", "📧", "💻", "🎮", "🛒", "🎵", "📷",
            "✈", "🏠", "📚", "🎓", "🏥", "⚽", "🍔", "🐶", "🐱", "⚡",
            "🌙", "🔥", "⭐", "🔑", "💰"
        )
        val emojiWidth = resources.getDimension(R.dimen.emoji_chip_width)
        val emojiHeight = resources.getDimension(R.dimen.emoji_chip_height)
        val emojiTextSizeSp = resources.getDimension(R.dimen.emoji_chip_text_size) /
            resources.displayMetrics.scaledDensity

        val emojiTextColor = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.md_on_primary),
                androidx.core.content.ContextCompat.getColor(this, R.color.md_on_surface)
            )
        )

        fun refreshEmojiSelection(picked: String) {
            for (i in 0 until gridEmoji.childCount) {
                val tv = gridEmoji.getChildAt(i) as android.widget.TextView
                tv.isSelected = tv.text.toString() == picked
            }
        }

        for (emoji in presetEmojis) {
            val tv = android.widget.TextView(this).apply {
                text = emoji
                isSelected = emoji == selectedEmoji[0]
                isClickable = true
                background = androidx.core.content.ContextCompat.getDrawable(this@CredentialEditorActivity, R.drawable.bg_emoji_item_selector)
                gravity = android.view.Gravity.CENTER
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                setTextColor(emojiTextColor)
                textSize = emojiTextSizeSp
            }
            tv.setOnClickListener {
                selectedEmoji[0] = emoji
                ivIcon.text = emoji
                refreshEmojiSelection(emoji)
            }
            val params = androidx.gridlayout.widget.GridLayout.LayoutParams().apply {
                width = emojiWidth.toInt()
                height = emojiHeight.toInt()
                columnSpec = androidx.gridlayout.widget.GridLayout.spec(
                    androidx.gridlayout.widget.GridLayout.UNDEFINED, 1, 1f
                )
                rowSpec = androidx.gridlayout.widget.GridLayout.spec(
                    androidx.gridlayout.widget.GridLayout.UNDEFINED, 1
                )
                setGravity(android.view.Gravity.CENTER)
                topMargin = (resources.getDimension(R.dimen.emoji_row_spacing)).toInt()
                bottomMargin = (resources.getDimension(R.dimen.emoji_row_spacing)).toInt()
            }
            tv.layoutParams = params
            gridEmoji.addView(tv)
        }
        ivIcon.text = selectedEmoji[0]

        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setGravity(android.view.Gravity.CENTER)
            window.attributes?.windowAnimations = R.style.DialogAnimation
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            window.setLayout(
                (metrics.widthPixels * 0.9f).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setDimAmount(0.35f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.setDimAmount(0.45f)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes?.setBlurBehindRadius(12)
            }
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
            .setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                tilName.error = getString(R.string.category_empty_name)
                return@setOnClickListener
            }
            dialog.dismiss()
            // 创建分类（带选中的 emoji）
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
                    vault.createCategory(name, emoji = selectedEmoji[0]).also {
                        val repo = VaultRepository.get(this@CredentialEditorActivity)
                        repo.save(vault, pwd)
                        val updated = repo.load(pwd)
                        SessionManager.updateVault(updated)
                    }
                }
                onCreated(createdCat)
            }
        }

        etName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { tilName.error = null }
            override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        dialog.show()
        etName.requestFocus()
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

        // 卡片式弹窗：裸 Dialog + 圆角背景 + 居中 + 蒙层
        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setGravity(android.view.Gravity.CENTER)
            window.attributes?.windowAnimations = R.style.DialogAnimation
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            window.setLayout(
                (metrics.widthPixels * 0.9f).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setDimAmount(0.35f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.setDimAmount(0.45f)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes?.setBlurBehindRadius(12)
            }
        }

        fun currentOptions() = PasswordGenerator.Options(
            length = dialogBinding.seekLength.progress,
            useUppercase = dialogBinding.cbUppercase.isChecked,
            useLowercase = dialogBinding.cbLowercase.isChecked,
            useDigits = dialogBinding.cbDigits.isChecked,
            useSymbols = dialogBinding.cbSymbols.isChecked
        )

        fun regenerate() {
            dialogBinding.tvGeneratedPassword.text = PasswordGenerator.generate(currentOptions())
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

        // 取消
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        // 应用此密码：填入密码框并关闭
        dialogBinding.btnApply.setOnClickListener {
            binding.etPassword.setText(PasswordGenerator.generate(currentOptions()))
            dialog.dismiss()
        }

        regenerate()
        dialog.show()
    }
}
