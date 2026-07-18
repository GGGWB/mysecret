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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
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

            binding.fabAdd.setOnLongClickListener {
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
        // 创建对话框
        val dialog = AlertDialog.Builder(this)
            .setCancelable(true)
            .create()

        // 加载 Markdown 文件
        val helpText = try {
            resources.openRawResource(R.raw.help).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "暂无使用说明"
        }

        // 创建 WebView 展示内容
        val webView = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.textZoom = 100
            
            // 将 Markdown 转换为简单的 HTML
            val htmlContent = convertMarkdownToHtml(helpText)
            loadDataWithBaseURL(
                "file:///android_asset/",
                htmlContent,
                "text/html; charset=utf-8",
                "UTF-8",
                null
            )
        }

        dialog.setContentView(webView, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 设置底部按钮
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "知道了") { _, _ -> }

        dialog.show()
        
        // 设置对话框高度
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun convertMarkdownToHtml(markdown: String): String {
        var html = markdown
            // 标题
            .replaceRegex("^### (.+)$", "<h3>$1</h3>", true)
            .replaceRegex("^## (.+)$", "<h2>$1</h2>", true)
            .replaceRegex("^# (.+)$", "<h1>$1</h1>", true)
            // 分隔线
            .replaceRegex("^---$", "<hr/>", true)
            // 粗体
            .replace("**(.+?)**", "<b>$1</b>")
            // 斜体
            .replaceRegex("\\*(.+?)\\*", "<i>$1</i>")
            // 列表项
            .replaceRegex("^[-•] (.+)$", "<li>$1</li>", true)
            // 引用
            .replaceRegex("^> (.+)$", "<blockquote>$1</blockquote>", true)
            // 段落
            .replace("\n\n", "</p><p>")
            .replace("\n", "<br/>")
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { 
                        font-family: sans-serif; 
                        padding: 16px; 
                        line-height: 1.6;
                        color: #1C1B1F;
                        font-size: 14sp;
                    }
                    h1 { font-size: 20sp; margin: 12px 0 8px 0; color: #1F6FEB; }
                    h2 { font-size: 18sp; margin: 10px 0 6px 0; color: #1F6FEB; }
                    h3 { font-size: 16sp; margin: 8px 0 4px 0; color: #1F6FEB; }
                    hr { border: none; border-top: 1px solid #DDD; margin: 12px 0; }
                    li { margin-left: 20px; margin-bottom: 4px; }
                    blockquote { 
                        border-left: 3px solid #1F6FEB; 
                        padding-left: 12px; 
                        margin: 8px 0;
                        color: #79747E;
                    }
                    p { margin: 8px 0; }
                    b { color: #49454F; }
                </style>
            </head>
            <body>
                <p>$html</p>
            </body>
            </html>
        """.trimIndent()
    }

    private fun String.replaceRegex(pattern: String, replacement: String, multiline: Boolean = false): String {
        val regexPattern = pattern.replaceRegexSpecial()
        val options = if (multiline) setOf(RegexOption.MULTILINE) else emptySet()
        return this.replace(Regex(regexPattern, options), replacement)
    }

    private fun String.replaceRegexSpecial(): String {
        return this.replace(".", "\\.").replace("*", "\\*").replace("+", "\\+")
            .replace("?", "\\?").replace("[", "\\[").replace("]", "\\]")
            .replace("(", "\\(").replace(")", "\\)").replace("{", "\\{")
            .replace("}", "\\}").replace("^", "\\^").replace("$", "\\$")
            .replace("\\", "\\\\")
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
        cats.add(Category(id = "", name = "全部", color = 0xFF6750A4.toInt(), sortOrder = 0))
        for ((cat, count) in stats) {
            cats.add(cat)
        }

        // inflate 自定义布局
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_category_selector, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvCategoryChips)
        recyclerView.layoutManager = GridLayoutManager(this, 3) // 3列网格

        // 先创建对话框
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 显示动画
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.attributes?.windowAnimations = R.style.DialogAnimation
        }

        // 创建 Chip Adapter
        val chipAdapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            inner class ChipViewHolder(val chip: com.google.android.material.chip.Chip) :
                androidx.recyclerview.widget.RecyclerView.ViewHolder(chip)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ChipViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_category_chip, parent, false)
                    as com.google.android.material.chip.Chip
            )

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                if (holder !is ChipViewHolder) return
                val cat = cats[position]
                val chip = holder.chip

                chip.text = cat.name
                chip.isChecked = currentCategoryId == cat.id
                chip.tag = cat.id

                // 点击动画
                chip.setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            v.scaleX = 0.97f
                            v.scaleY = 0.97f
                            true
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            v.animate()?.scaleX(1f)?.scaleY(1f)?.duration = 100
                            true
                        }
                        else -> true
                    }
                }

                chip.setOnClickListener {
                    currentCategoryId = chip.tag as String
                    updateCategoryLabel()
                    refreshList()
                    dialog.dismiss()
                }
            }

            override fun getItemCount() = cats.size
        }
        recyclerView.adapter = chipAdapter

        dialog.show()

        // 设置"管理分类"按钮点击事件
        dialogView.findViewById<Button>(R.id.btnManageCategories).setOnClickListener {
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
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvCategoryList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 改用 BottomSheetDialog：从底部滑出，内容多时可自然滚动，底部按钮始终可见
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            inner class ManageCategoryViewHolder(val view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ManageCategoryViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_manage_category, parent, false)
            )
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                if (holder !is ManageCategoryViewHolder) return
                val (cat, count) = stats[position]
                val tvName = holder.view.findViewById<TextView>(R.id.tvCategoryName)
                val tvCount = holder.view.findViewById<TextView>(R.id.tvCategoryCount)
                val dotView = holder.view.findViewById<View>(R.id.categoryDot)
                val btnEdit = holder.view.findViewById<ImageButton>(R.id.btnEditCategory)
                val btnDelete = holder.view.findViewById<ImageButton>(R.id.btnDeleteCategory)

                tvName.text = cat.name
                tvCount.text = "$count 条密码"
                dotView.setBackgroundColor(cat.color)

                btnEdit.setOnClickListener {
                    showCategoryEditDialog(cat)
                }
                btnDelete.setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("删除分类")
                        .setMessage("确定删除分类「${cat.name}」吗？该分类下的 $count 条密码将变为未分类。")
                        .setPositiveButton("删除") { _, _ -> deleteCategory(cat.id) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            override fun getItemCount() = stats.size
        }
        recyclerView.adapter = adapter

        // 显示对话框
        dialog.show()

        // 让 BottomSheet 默认完全展开（而非只露出 peekHeight 高度）
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        // 设置"新建分类"按钮点击事件
        dialogView.findViewById<Button>(R.id.btnNewCategory).setOnClickListener {
            dialog.dismiss()
            showNewCategoryDialog()
        }
    }

    private fun showNewCategoryDialog() {
        val input = EditText(this).apply {
            hint = "分类名称"
            setPadding(32, 0, 32, 0)
        }
        AlertDialog.Builder(this)
            .setTitle("新建分类")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) createCategory(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCategoryEditDialog(category: Category) {
        val input = EditText(this).apply {
            setText(category.name)
            hint = "分类名称"
            setPadding(32, 0, 32, 0)
        }
        AlertDialog.Builder(this)
            .setTitle("编辑分类")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) updateCategoryName(category.id, newName)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createCategory(name: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pwd = SessionManager.getMasterPassword() ?: return@withContext
                    val vault = SessionManager.getVault() ?: return@withContext
                    vault.createCategory(name)
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

    private fun updateCategoryName(catId: String, newName: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pwd = SessionManager.getMasterPassword() ?: return@withContext
                    val vault = SessionManager.getVault() ?: return@withContext
                    vault.renameCategory(catId, newName)
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
