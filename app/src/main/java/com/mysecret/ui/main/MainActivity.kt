package com.mysecret.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.mysecret.R
import com.mysecret.data.PrefsManager
import com.mysecret.data.SessionManager
import com.mysecret.data.VaultRepository
import com.mysecret.data.model.Category
import com.mysecret.data.model.Credential
import com.mysecret.data.model.Vault
import com.mysecret.databinding.ActivityMainBinding
import com.mysecret.databinding.DialogGeneratorBinding
import com.mysecret.databinding.DialogSortBinding
import com.mysecret.databinding.ItemCredentialBinding
import com.mysecret.security.PasswordGenerator
import com.mysecret.security.SecureClipboard
import com.mysecret.ui.backup.BackupActivity
import com.mysecret.ui.editor.CredentialEditorActivity
import com.mysecret.ui.unlock.UnlockActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CredentialAdapter
    private var currentSort = 0
    private var currentCategoryId: String = ""

    companion object {
        private const val TAG = "MainActivity"
    }

    /**
     * 返回屏幕宽度的指定比例（像素），用于弹窗宽度。
     * 默认 0.9（屏幕 90%）：选择分类需保证一行 3 个 Chip，emoji 选择需保证一行 4 个。
     * @param fraction 比例
     */
    private fun screenWidthFraction(fraction: Float = 0.9f): Int {
        val metrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        return (metrics.widthPixels * fraction).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 第一步：先检查会话状态，失败则直接返回，不做任何 UI 操作
        if (SessionManager.locked || SessionManager.getMasterPassword() == null) {
            Log.e(TAG, "onCreate: locked or no master password, aborting")
            backToUnlock()
            return
        }

        val vault = SessionManager.getVault()
        if (vault == null) {
            Log.e(TAG, "onCreate: vault is null, aborting")
            Snackbar.make(
                android.widget.FrameLayout(this),
                "密码库未加载，请重新启动",
                Snackbar.LENGTH_LONG
            ).show()
            backToUnlock()
            return
        }

        Log.d(TAG, "onCreate: session OK, starting inflate")

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            currentSort = PrefsManager.getSortMode(this)
            Log.d(TAG, "onCreate: got sort mode=$currentSort")

            adapter = CredentialAdapter(
                onEdit = { credential ->
                    startActivity(Intent(this, CredentialEditorActivity::class.java).apply {
                        putExtra("credential_id", credential.id)
                    })
                },
                onCopyUsername = { username ->
                    SecureClipboard.copy(this, "username", username, getString(R.string.copied_username))
                },
                onCopyPassword = { password ->
                    SecureClipboard.copy(this, "password", password, getString(R.string.copied_password))
                }
            )
            Log.d(TAG, "onCreate: adapter created")

            binding.rvCredentials.layoutManager = LinearLayoutManager(this)
            binding.rvCredentials.adapter = adapter
            Log.d(TAG, "onCreate: rv set up")

            binding.fabAdd.setOnClickListener {
                startActivity(Intent(this, CredentialEditorActivity::class.java))
            }

            // 长按标题区域弹出分类选择弹窗
            binding.toolbar.setOnLongClickListener {
                showCategoryFilterMenu()
                true
            }

            binding.toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_lock -> { lock(); true }
                    R.id.action_sort -> { showSortDialog(); true }
                    R.id.action_backup -> {
                        startActivity(Intent(this, BackupActivity::class.java))
                        true
                    }
                    R.id.action_help -> { showHelpDialog(); true }
                    else -> false
                }
            }

            binding.etSearch.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val v = SessionManager.getVault()
                    if (v != null) applyFilter(s.toString(), v)
                }
                override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })

            updateCategoryLabel()
            Log.d(TAG, "onCreate: updating label done")
            refreshList()
            Log.d(TAG, "onCreate: ALL DONE, showing screen")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate EXCEPTION", e)
            e.printStackTrace()
            try {
                Snackbar.make(
                    findViewById(android.R.id.content) ?: binding.root,
                    "发生错误: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            } catch (e2: Exception) {
                Log.e(TAG, "onCreate: snackbar also failed", e2)
            }
            backToUnlock()
        }
    }

    override fun onResume() {
        super.onResume()
        if (SessionManager.locked) {
            backToUnlock()
            return
        }
        refreshList()
    }

    private fun refreshList() {
        val vault = SessionManager.getVault()
        if (vault == null) {
            backToUnlock()
            return
        }
        applyFilter(binding.etSearch.text.toString(), vault)
    }

    private fun applyFilter(query: String, vault: Vault) {
        try {
            Log.d(TAG, "applyFilter: categoryId='$currentCategoryId', query='$query'")
            val list = vault.filteredByCategoryAndSearch(currentCategoryId, query)
            Log.d(TAG, "applyFilter: result size=${list.size}")
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.rvCredentials.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(binding.root, "刷新列表失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showHelpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help, null)
        val contentLayout = dialogView.findViewById<LinearLayout>(R.id.layoutHelpContent)

        // 读取 help.md 并解析成原生 TextView
        val helpText = try {
            resources.openRawResource(R.raw.help).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "暂无使用说明"
        }
        renderHelpMarkdown(helpText, contentLayout)

        // 卡片式弹窗（与其他弹窗统一的 window 配置）
        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setGravity(android.view.Gravity.CENTER)
            window.attributes?.windowAnimations = R.style.DialogAnimation
            window.setLayout(
                screenWidthFraction(0.88f),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setDimAmount(0.35f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.setDimAmount(0.45f)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes?.setBlurBehindRadius(12)
            }
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHelpClose)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * 轻量 Markdown 渲染：逐行解析 help.md，转成原生 TextView。
     * 支持：# 标题、- 列表、> 引用、--- 分隔线、**粗体**、普通段落。
     * 比 WebView + 正则替换 HTML 更可靠（避免 sp 单位、正则捕获组被转义等问题）。
     */
    private fun renderHelpMarkdown(markdown: String, container: LinearLayout) {
        val primaryColor = ContextCompat.getColor(this, R.color.md_primary)
        val textColor = ContextCompat.getColor(this, R.color.md_on_surface)
        val secondaryColor = ContextCompat.getColor(this, R.color.md_hint)
        val dividerColor = ContextCompat.getColor(this, R.color.md_divider)

        // 解析行内 **粗体**：用 StyleSpan 标记粗体部分（同时移除 ** 标记）
        fun spannable(text: String): android.text.SpannableStringBuilder {
            val clean = text.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            val ssb = android.text.SpannableStringBuilder(clean)
            Regex("\\*\\*(.+?)\\*\\*").findAll(text).forEach { m ->
                // m.groupValues[1] 是粗体内容，在 clean 里找到对应位置加粗
                val content = m.groupValues[1]
                val idx = clean.indexOf(content)
                if (idx >= 0) {
                    ssb.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        idx, idx + content.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            return ssb
        }

        for (rawLine in markdown.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                // 分隔线
                line == "---" -> {
                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        ).apply { setMargins(0, 12, 0, 12) }
                        setBackgroundColor(dividerColor)
                    }
                    container.addView(divider)
                }
                // 一级标题
                line.startsWith("# ") -> {
                    container.addView(titleTextView(line.removePrefix("# "), 20f, primaryColor, bold = true, top = 4, bottom = 6))
                }
                // 二级标题
                line.startsWith("## ") -> {
                    container.addView(titleTextView(line.removePrefix("## "), 16f, primaryColor, bold = true, top = 10, bottom = 4))
                }
                // 引用
                line.startsWith("> ") -> {
                    val tv = android.widget.TextView(this).apply {
                        text = spannable(line.removePrefix("> "))
                        textSize = 13f
                        setTextColor(secondaryColor)
                        setPadding(16, 10, 0, 10)
                        background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_help_quote)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 8, 0, 8) }
                    }
                    container.addView(tv)
                }
                // 有序列表（1. 2. 3.）
                line.matches(Regex("^\\d+\\. .+")) -> {
                    container.addView(bulletTextView(line, textColor, indent = true))
                }
                // 无序列表（- 或 •）
                line.startsWith("- ") || line.startsWith("• ") -> {
                    val content = line.removePrefix("- ").removePrefix("• ")
                    container.addView(bulletTextView("•  $content", textColor, indent = true))
                }
                // 普通段落
                else -> {
                    container.addView(bulletTextView(spannable(line), textColor, indent = false))
                }
            }
        }
    }

    /** 标题 TextView */
    private fun titleTextView(text: CharSequence, sizeSp: Float, color: Int, bold: Boolean, top: Int, bottom: Int): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, top, 0, bottom) }
        }
    }

    /** 列表/段落 TextView */
    private fun bulletTextView(text: CharSequence, color: Int, indent: Boolean): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(color)
            setLineSpacing(2f, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 3, 0, 3)
                if (indent) marginStart = 12
            }
        }
    }

    private fun updateCategoryLabel() {
        try {
            val vault = SessionManager.getVault()
            if (currentCategoryId.isEmpty()) {
                binding.toolbar.title = getString(R.string.app_name)
            } else {
                val label = vault?.getCategoryById(currentCategoryId)?.name ?: "全部"
                binding.toolbar.title = "📂 $label"
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateCategoryLabel failed", e)
            e.printStackTrace()
        }
    }

    private fun showCategoryFilterMenu() {
        val vault = SessionManager.getVault() ?: return
        val stats = vault.getCategoryStats()
        val cats = mutableListOf<Category>()

        // 添加"全部"选项
        cats.add(Category(id = "", name = getString(R.string.category_all), emoji = "📂", color = 0xFF6750A4.toInt(), sortOrder = 0))
        for ((cat, _) in stats) {
            cats.add(cat)
        }

        // inflate 自定义布局
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_category_selector, null)
        val chipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupCategories)

        // 裸 Dialog，居中显示
        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            // 居中
            window.setGravity(android.view.Gravity.CENTER)
            window.attributes?.windowAnimations = R.style.DialogAnimation
            // 宽度约为屏幕的 90%：保证 Chip 每行能并排放下 3 个分类
            window.setLayout(
                screenWidthFraction(0.9f),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            // 蒙层：半透明 + API 31+ 模糊
            window.setDimAmount(0.35f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.setDimAmount(0.45f)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes?.setBlurBehindRadius(12)
            }
        }

        // 动态生成 Chip 胶囊
        for (cat in cats) {
            val selected = currentCategoryId == cat.id
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = "${cat.emoji}  ${cat.name}"
                isCheckable = true
                isChecked = selected
                isClickable = true
                // 选中态直接变蓝底，不加对勾
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (selected) ContextCompat.getColor(this@MainActivity, R.color.md_primary)
                    else ContextCompat.getColor(this@MainActivity, R.color.chip_off_color)
                )
                setTextColor(
                    if (selected) ContextCompat.getColor(this@MainActivity, R.color.md_on_primary)
                    else ContextCompat.getColor(this@MainActivity, R.color.md_on_surface)
                )
                chipMinHeight = resources.getDimension(R.dimen.chip_min_height_default)
                textStartPadding = resources.getDimension(R.dimen.chip_text_padding_default)
                textEndPadding = resources.getDimension(R.dimen.chip_text_padding_default)
                chipCornerRadius = resources.getDimension(R.dimen.chip_radius_default)
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            chip.setOnClickListener {
                currentCategoryId = cat.id
                updateCategoryLabel()
                refreshList()
                dialog.dismiss()
            }
            chipGroup.addView(chip)
        }

        dialog.show()

        // 设置"管理分类"按钮点击事件
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnManageCategories).setOnClickListener {
            dialog.dismiss()
            showManageCategoriesDialog()
        }
    }

    // 对话框动画样式
    private val dialogAnimation = android.view.animation.DecelerateInterpolator()

    private fun showManageCategoriesDialog() {
        val vault = SessionManager.getVault() ?: return
        val stats = vault.getCategoryStats()

        // 使用自定义列表布局
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_categories, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvCategoryList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 裸 Dialog，居中显示（与选择分类弹窗完全一致的 window 配置）
        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setGravity(android.view.Gravity.CENTER)
            window.attributes?.windowAnimations = R.style.DialogAnimation
            window.setLayout(
                screenWidthFraction(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setDimAmount(0.35f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.setDimAmount(0.45f)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes?.setBlurBehindRadius(12)
            }
        }

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class ManageCategoryViewHolder(val view: View) : RecyclerView.ViewHolder(view)
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ManageCategoryViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_manage_category, parent, false)
            )
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                if (holder !is ManageCategoryViewHolder) return
                val (cat, count) = stats[position]
                val tvName = holder.view.findViewById<TextView>(R.id.tvCategoryName)
                val tvCount = holder.view.findViewById<TextView>(R.id.tvCategoryCount)
                val tvEmoji = holder.view.findViewById<TextView>(R.id.tvCategoryEmoji)
                val btnEdit = holder.view.findViewById<ImageButton>(R.id.btnEditCategory)
                val btnDelete = holder.view.findViewById<ImageButton>(R.id.btnDeleteCategory)

                tvName.text = cat.name
                tvCount.text = getString(R.string.category_count_format, count)
                tvEmoji.text = cat.emoji

                btnEdit.setOnClickListener {
                    dialog.dismiss()
                    showCategoryEditDialog(cat)
                }
                btnDelete.setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.category_delete_title)
                        .setMessage(getString(R.string.category_delete_message, cat.name, count))
                        .setPositiveButton(R.string.confirm_delete_yes) { _, _ -> deleteCategory(cat.id) }
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
                }
            }
            override fun getItemCount() = stats.size
        }
        recyclerView.adapter = adapter

        dialog.show()

        // 设置"新建分类"按钮点击事件
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNewCategory).setOnClickListener {
            dialog.dismiss()
            showNewCategoryDialog()
        }
    }

    private fun showNewCategoryDialog() {
        showCategoryInputDialog(
            titleRes = R.string.category_new,
            confirmRes = R.string.category_create,
            initialName = "",
            initialEmoji = "📁",
            onConfirm = { name, emoji -> createCategory(name, emoji) }
        )
    }

    private fun showCategoryEditDialog(category: Category) {
        showCategoryInputDialog(
            titleRes = R.string.category_edit,
            confirmRes = R.string.category_save,
            initialName = category.name,
            initialEmoji = category.emoji,
            onConfirm = { name, emoji -> updateCategoryName(category.id, name, emoji) }
        )
    }

    /**
     * 卡片式分类输入弹窗（新建/编辑共用）。
     * 圆角卡片 + 标题 + 分类名输入框 + emoji 图标选择网格 + 取消/确认按钮。
     */
    private fun showCategoryInputDialog(
        titleRes: Int,
        confirmRes: Int,
        initialName: String,
        initialEmoji: String,
        onConfirm: (name: String, emoji: String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_category_input, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val ivIcon = dialogView.findViewById<TextView>(R.id.ivTitleIcon)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCategoryName)
        val tilName = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilCategoryName)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)
        val gridEmoji = dialogView.findViewById<androidx.gridlayout.widget.GridLayout>(R.id.gridEmoji)

        tvTitle.text = getString(titleRes)
        btnConfirm.text = getString(confirmRes)
        etName.setText(initialName)
        if (initialName.isNotEmpty()) etName.setSelection(initialName.length)

        // 当前选中的 emoji（可变，供确认时读取）
        val selectedEmoji = arrayOf(if (initialEmoji.isNotEmpty()) initialEmoji else "📁")

        // 预设 emoji 列表
        val presetEmojis = listOf(
            "📁", "💼", "💬", "🏦", "📧", "💻", "🎮", "🛒", "🎵", "📷",
            "✈", "🏠", "📚", "🎓", "🏥", "⚽", "🍔", "🐶", "🐱", "⚡",
            "🌙", "🔥", "⭐", "🔑", "💰"
        )
        // 填充 emoji 网格：用 TextView 替代 Chip。
        // 原因：Material Chip 的蓝色背景（ChipDrawable）宽度跟随文字内容，不填满 View，
        // 导致无法精确控制背景框宽度。TextView 的背景精确等于 View 宽度，完全可控。
        val emojiWidth = resources.getDimension(R.dimen.emoji_chip_width)
        val emojiHeight = resources.getDimension(R.dimen.emoji_chip_height)
        val emojiTextSizeSp = resources.getDimension(R.dimen.emoji_chip_text_size) /
            resources.displayMetrics.scaledDensity

        // 文字颜色 selector：选中白、未选深色（emoji 彩色字形受 textColor 影响有限，但保持一致）
        val emojiTextColor = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(this@MainActivity, R.color.md_on_primary),
                ContextCompat.getColor(this@MainActivity, R.color.md_on_surface)
            )
        )

        // 刷新选中态
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
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_emoji_item_selector)
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
            // GridLayout 参数：固定扁矩形 + 列权重 1（5 列等分宽度）+ 格内居中
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
            window.setLayout(
                screenWidthFraction(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setDimAmount(0.35f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.setDimAmount(0.45f)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes?.setBlurBehindRadius(12)
            }
        }

        // 取消按钮
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
            .setOnClickListener { dialog.dismiss() }

        // 确认按钮：校验非空后回调
        btnConfirm.setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                tilName.error = getString(R.string.category_empty_name)
                return@setOnClickListener
            }
            dialog.dismiss()
            onConfirm(name, selectedEmoji[0])
        }

        // 输入时清除错误
        etName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { tilName.error = null }
            override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        dialog.show()
        etName.requestFocus()
    }

    private fun createCategory(name: String, emoji: String = "📁") {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pwd = SessionManager.getMasterPassword() ?: return@withContext
                    val vault = SessionManager.getVault() ?: return@withContext
                    vault.createCategory(name, emoji = emoji)
                    VaultRepository.get(this@MainActivity).save(vault, pwd)
                    SessionManager.updateVault(VaultRepository.get(this@MainActivity).load(pwd))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "创建分类失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateCategoryName(catId: String, newName: String, newEmoji: String? = null) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pwd = SessionManager.getMasterPassword() ?: return@withContext
                    val vault = SessionManager.getVault() ?: return@withContext
                    vault.renameCategory(catId, newName)
                    if (newEmoji != null) vault.updateCategoryEmoji(catId, newEmoji)
                    VaultRepository.get(this@MainActivity).save(vault, pwd)
                    SessionManager.updateVault(VaultRepository.get(this@MainActivity).load(pwd))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "更新分类失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteCategory(catId: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pwd = SessionManager.getMasterPassword() ?: return@withContext
                    val vault = SessionManager.getVault() ?: return@withContext
                    vault.deleteCategory(catId)
                    VaultRepository.get(this@MainActivity).save(vault, pwd)
                    SessionManager.updateVault(VaultRepository.get(this@MainActivity).load(pwd))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "删除分类失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSortDialog() {
        val dialogBinding = DialogSortBinding.inflate(LayoutInflater.from(this))
        when (currentSort) {
            1 -> dialogBinding.rbNameDesc.isChecked = true
            2 -> dialogBinding.rbUpdated.isChecked = true
            else -> dialogBinding.rbNameAsc.isChecked = true
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_sort)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                currentSort = when {
                    dialogBinding.rbNameDesc.isChecked -> 1
                    dialogBinding.rbUpdated.isChecked -> 2
                    else -> 0
                }
                PrefsManager.saveSortMode(this, currentSort)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun lock() {
        SessionManager.lock()
        backToUnlock()
    }

    private fun backToUnlock() {
        try {
            Log.d(TAG, "backToUnlock")
            val intent = Intent(this, UnlockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "backToUnlock failed", e)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}

// ──────────── Adapter ────────────

class CredentialAdapter(
    private val onEdit: (Credential) -> Unit,
    private val onCopyUsername: (String) -> Unit,
    private val onCopyPassword: (String) -> Unit
) : RecyclerView.Adapter<CredentialAdapter.ViewHolder>() {

    private val items = mutableListOf<Credential>()

    inner class ViewHolder(val binding: ItemCredentialBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCredentialBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val vault = SessionManager.getVault()
        val category = if (item.categoryId.isNotEmpty()) vault?.getCategoryById(item.categoryId) else null

        holder.binding.apply {
            tvName.text = item.name

            tvUsername.text = item.username.takeIf { it.isNotEmpty() } ?: "—"
            tvUsername.setOnClickListener {
                onCopyUsername(item.username)
            }

            var passwordVisible = false
            tvPassword.text = "••••••••"
            tvPassword.tag = item.password

            ivLock.setOnClickListener {
                passwordVisible = !passwordVisible
                if (passwordVisible) {
                    tvPassword.text = item.password
                    ivLock.alpha = 0.8f
                } else {
                    tvPassword.text = "••••••••"
                    ivLock.alpha = 0.5f
                }
            }

            tvPassword.setOnClickListener {
                if (passwordVisible) {
                    onCopyPassword(item.password)
                } else {
                    passwordVisible = true
                    tvPassword.text = item.password
                    ivLock.alpha = 0.8f
                }
            }

            if (category != null) {
                categoryBar.visibility = View.VISIBLE
                categoryBar.setBackgroundColor(category.color)
            } else {
                categoryBar.visibility = View.GONE
            }

            ivArrow.setOnClickListener { onEdit(item) }
        }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<Credential>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
