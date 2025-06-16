// NewFileOrFolderAction.kt
package com.itsaky.androidide.actions.filetree

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.text.InputFilter
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.actions.getContext
import com.itsaky.androidide.adapters.viewholders.FileTreeViewHolder
// 检查这一行！如果使用 View Binding，通常不再需要直接导入 R
// import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import com.unnamed.b.atv.model.TreeNode
import java.io.File
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.app.Activity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

// 导入生成的 View Binding 类
import com.itsaky.androidide.databinding.LayoutDialogTextInputBinding // <-- 根据你的包名和布局文件生成

import kotlin.collections.filter
import kotlin.collections.distinct
import kotlin.collections.joinToString
import kotlin.collections.toSet

/**
 * 文件树操作，用于创建新的文件夹或文件。
 * 支持创建多级文件夹、文件，剪贴板粘贴，文件后缀/历史记录列表功能，
 * 移除空格选项，以及自定义的四按钮对话框布局。
 *
 * @author android_zero (零丶) github：msmt2017
 */
class NewFileOrFolderAction(context: Context, override val order: Int) :
    BaseDirNodeAction(
        context = context,
        labelRes = R.string.action_create_file_folder,
        iconRes = R.drawable.ic_new_folder
    ) {

    // SharedPreferences 用于存储用户偏好（复选框状态、RadioGroup选择、历史记录）
    private val PREFS_NAME = "NewFileOrFolderActionPrefs"
    private val PREF_REMOVE_SPACES_CHECKED = "remove_spaces_checked"
    private val PREF_SELECTED_LIST_TYPE = "selected_list_type" // 0 for suffix, 1 for history
    private val PREF_HISTORY_LIST = "history_list_json"
    private val MAX_HISTORY_SIZE = 10
    private val gson = Gson() // Gson 实例用于序列化/反序列化历史记录

    override val id: String = "ide.editor.fileTree.newFolderOrFile"

    override suspend fun execAction(data: ActionData) {
        val activityContext: Activity = data.getContext() as? Activity ?: run {
            flashError(R.string.msg_activity_not_found)
            return
        }
        val currentDir = data.requireFile()
        val lastHeld: TreeNode? = data.get(TreeNode::class.java) // 从 ActionData 获取 TreeNode
        val prefs: SharedPreferences = activityContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 使用 View Binding 替代 LayoutInflater.from().inflate() 和 findViewById
        val binding = LayoutDialogTextInputBinding.inflate(LayoutInflater.from(activityContext))
        val dialogView = binding.root // 获取根视图

        val builder = DialogUtils.newMaterialDialogBuilder(activityContext)

        // 通过 binding 对象直接访问 UI 元素
        val editText = binding.editTextNameInput
        val dropdownArrow = binding.dropdownArrow
        val checkboxRemoveSpaces = binding.checkboxRemoveSpaces

        val btnCancel = binding.btnCancel
        val btnPaste = binding.btnPaste
        val btnFile = binding.btnFile
        val btnFolder = binding.btnFolder

        // 设置输入框提示和对话框标题
        editText.setHint(R.string.folder_name)
        builder.setTitle(R.string.new_folder)
        builder.setMessage(R.string.msg_can_contain_slashes)
        builder.setView(dialogView) // 设置自定义布局
        builder.setCancelable(false) // 对话框不可取消关闭

        // ====================================================================
        // 1. 设置InputFilter限制非法字符输入
        val allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_./"
        editText.filters = arrayOf(
            InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int ->
                for (i in start until end) {
                    val char = source[i]
                    if (!allowedChars.contains(char)) {
                        flashError(activityContext.getString(R.string.msg_unsupported_characters, "'$char'"))
                        return@InputFilter "" // 阻止输入该字符
                    }
                }
                null // 允许输入
            }
        )
        // ====================================================================

        // ====================================================================
        // 2. 移除空格复选框功能
        checkboxRemoveSpaces.isChecked = prefs.getBoolean(PREF_REMOVE_SPACES_CHECKED, false)
        checkboxRemoveSpaces.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            prefs.edit().putBoolean(PREF_REMOVE_SPACES_CHECKED, isChecked).apply()
        }
        // ====================================================================

        // ====================================================================
        // 3. 粘贴按钮功能
        btnPaste.setOnClickListener { _: View ->
            val clipboard = activityContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0) {
                val pasteData = clipboard.primaryClip?.getItemAt(0)?.text
                if (pasteData != null) {
                    editText.setText(pasteData)
                    editText.text?.length?.let { editText.setSelection(it) }
                    flashSuccess(R.string.msg_paste_successful)
                }
            } else {
                flashError(R.string.msg_clipboard_empty)
            }
        }
        // ====================================================================

        // ====================================================================
        // 4. 下拉箭头按钮和弹出列表功能
        dropdownArrow.setOnClickListener { view: View ->
            showSuffixHistoryPopup(activityContext, view, editText, prefs)
        }
        // ====================================================================

        // ====================================================================
        // 5. 对话框底部按钮的点击事件
        val dialog = builder.create() // 创建对话框实例

        btnCancel.setOnClickListener { _: View ->
            dialog.dismiss() // 关闭对话框
        }

        btnFile.setOnClickListener { _: View ->
            dialog.dismiss()
            var inputName = editText.text.toString().trim()
            if (checkboxRemoveSpaces.isChecked) {
                inputName = inputName.replace(" ", "") // 移除所有空格
            }
            // 将原始输入（处理空格后）添加到历史记录
            addHistoryEntry(prefs, inputName)
            handleCreation(activityContext, currentDir, lastHeld, inputName, true) // true 表示创建文件
        }

        btnFolder.setOnClickListener { _: View ->
            dialog.dismiss()
            var inputName = editText.text.toString().trim()
            if (checkboxRemoveSpaces.isChecked) {
                inputName = inputName.replace(" ", "") // 移除所有空格
            }
            // 将原始输入（处理空格后）添加到历史记录
            addHistoryEntry(prefs, inputName)
            handleCreation(activityContext, currentDir, lastHeld, inputName, false) // false 表示创建文件夹
        }
        // ====================================================================

        dialog.show()
    }

    /**
     * 处理文件或文件夹的创建逻辑。
     *
     * @param context 上下文。
     * @param currentDir 当前根目录。
     * @param lastHeld 树节点，用于更新UI。
     * @param inputName 用户输入的名称（已处理空格，未进行路径分隔符替换）。
     * @param isFileToCreate 如果为true则创建文件，否则创建文件夹。
     */
    private fun handleCreation(
        context: Context,
        currentDir: File,
        lastHeld: TreeNode?,
        inputName: String,
        isFileToCreate: Boolean
    ) {
        if (inputName.isEmpty()) {
            flashError(R.string.msg_invalid_name)
            return
        }

        val processedPath = inputName.replace("\\", "/").replace(".", "/")
            .trimStart('/').trimEnd('/')

        if (processedPath.isEmpty()) {
            flashError(R.string.msg_invalid_name)
            return
        }

        if (!isValidFileName(processedPath)) {
            flashError(context.getString(R.string.msg_unsupported_characters, getUnsupportedCharacters(processedPath)))
            return
        }

        if (!currentDir.exists() || !currentDir.isDirectory) {
            flashError(context.getString(R.string.msg_root_folder_not_found, currentDir.absolutePath))
            return
        }

        val targetFileOrFolder: File
        if (isFileToCreate) {
            val lastSlashIndex = processedPath.lastIndexOf('/')
            val parentPathSegment = if (lastSlashIndex != -1) processedPath.substring(0, lastSlashIndex) else ""
            val fileName = if (lastSlashIndex != -1) processedPath.substring(lastSlashIndex + 1) else processedPath

            if (fileName.isEmpty()) {
                flashError(R.string.msg_invalid_name)
                return
            }

            val finalParentDir = if (parentPathSegment.isNotEmpty()) File(currentDir, parentPathSegment) else currentDir
            targetFileOrFolder = File(finalParentDir, fileName)

            createFile(context, targetFileOrFolder, lastHeld, currentDir)

        } else {
            targetFileOrFolder = File(currentDir, processedPath)
            createFolder(context, targetFileOrFolder, lastHeld, currentDir)
        }
    }

    /**
     * 创建文件。
     */
    private fun createFile(context: Context, targetFile: File, lastHeld: TreeNode?, currentDir: File) {
        try {
            val parentDir = targetFile.parentFile
            if (parentDir != null) {
                if (!parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        flashError(context.getString(R.string.msg_folder_creation_failed_for_parent, parentDir.absolutePath))
                        return
                    }
                }
            }

            if (targetFile.exists()) {
                flashError(R.string.msg_file_exists)
                return
            }

            if (targetFile.createNewFile()) {
                flashSuccess(R.string.msg_file_created)
                updateFileTreeUI(context, lastHeld, currentDir, targetFile) // 传递 context
            } else {
                flashError(R.string.msg_file_creation_failed)
            }
        } catch (e: IOException) {
            flashError(context.getString(R.string.msg_file_creation_exception, e.localizedMessage ?: "未知错误"))
            e.printStackTrace()
        }
    }

    /**
     * 创建文件夹。
     */
    private fun createFolder(context: Context, targetFolder: File, lastHeld: TreeNode?, currentDir: File) {
        if (targetFolder.exists()) {
            flashError(R.string.msg_folder_exists)
            return
        }

        try {
            if (targetFolder.mkdirs()) {
                flashSuccess(R.string.msg_folder_created)
                updateFileTreeUI(context, lastHeld, currentDir, targetFolder) // 传递 context
            } else {
                flashError(R.string.msg_folder_creation_failed)
            }
        } catch (e: IOException) {
            flashError(context.getString(R.string.msg_folder_creation_exception, e.localizedMessage ?: "未知错误"))
            e.printStackTrace()
        }
    }

    /**
     * 更新文件树UI。
     * 现在直接调用 BaseFileTreeAction 中的 protected 方法
     */
    private fun updateFileTreeUI(context: Context, lastHeld: TreeNode?, currentDir: File, newEntry: File) { // 接收 context
        if (lastHeld != null) {
            val parentFileOfNewEntry = newEntry.parentFile
            if (parentFileOfNewEntry != null && parentFileOfNewEntry == currentDir) {
                val node = TreeNode(newEntry)
                node.viewHolder = FileTreeViewHolder(context)
                requestExpandNode(lastHeld)
            } else {
                requestFileListing()
            }
        } else {
            requestFileListing()
        }
    }

    /**
     * 显示文件后缀和历史记录的弹出窗口。
     */
    private fun showSuffixHistoryPopup(
        context: Context, // 传递 context 参数
        anchorView: View,
        editText: TextInputEditText, // 将类型明确为 TextInputEditText
        prefs: SharedPreferences
    ) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.layout_suffix_history_popup, null)
        val popupWindow = PopupWindow(
            popupView,
            anchorView.width * 2,
            RecyclerView.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true
        popupWindow.setBackgroundDrawable(null)

        val radioGroup = popupView.findViewById<RadioGroup>(R.id.radio_group_list_type)
        val radioSuffix = popupView.findViewById<RadioButton>(R.id.radio_suffix)
        val radioHistory = popupView.findViewById<RadioButton>(R.id.radio_history)
        val recyclerViewSuffix = popupView.findViewById<RecyclerView>(R.id.recycler_view_suffix)
        val recyclerViewHistory = popupView.findViewById<RecyclerView>(R.id.recycler_view_history)

        recyclerViewSuffix.layoutManager = LinearLayoutManager(context)
        recyclerViewHistory.layoutManager = LinearLayoutManager(context)

        val suffixList = listOf(
            ".txt", ".java", ".kt", ".xml", ".gradle",".gradle.kts", ".md", ".html", ".css", ".js", ".json", ".md", ".py",
            ".c", ".cpp", ".h", ".sh", ".go", ".rs", ".rb", ".php", ".swift", ".dart",
            ".yml", ".gitignore", ".properties", ".png", ".jpg", ".jpeg", ".gif",
            ".bmp", ".mp3", ".mp4", ".zip", ".tar", ".gz", ".apk", ".jar", ".class", ".gitignore"
        )
        val suffixAdapter = ItemAdapter(suffixList) { item: String ->
            editText.append(item)
            popupWindow.dismiss()
        }
        recyclerViewSuffix.adapter = suffixAdapter

        val historyList = getHistoryEntries(prefs).toMutableList()
        val historyAdapter = ItemAdapter(historyList) { item: String ->
            editText.setText(item)
            editText.text?.length?.let { editText.setSelection(it) }
            popupWindow.dismiss()
        }
        recyclerViewHistory.adapter = historyAdapter

        val lastSelectedType = prefs.getInt(PREF_SELECTED_LIST_TYPE, 0)
        if (lastSelectedType == 0) {
            radioSuffix.isChecked = true
            recyclerViewSuffix.visibility = View.VISIBLE
            recyclerViewHistory.visibility = View.GONE
        } else {
            radioHistory.isChecked = true
            recyclerViewSuffix.visibility = View.GONE
            recyclerViewHistory.visibility = View.VISIBLE
            if (historyList.isEmpty()) {
                flashError(R.string.empty_history)
            }
        }

        radioGroup.setOnCheckedChangeListener { _: RadioGroup, checkedId: Int ->
            when (checkedId) {
                R.id.radio_suffix -> {
                    recyclerViewSuffix.visibility = View.VISIBLE
                    recyclerViewHistory.visibility = View.GONE
                    prefs.edit().putInt(PREF_SELECTED_LIST_TYPE, 0).apply()
                }
                R.id.radio_history -> {
                    (historyAdapter as ItemAdapter).updateData(getHistoryEntries(prefs))
                    recyclerViewSuffix.visibility = View.GONE
                    recyclerViewHistory.visibility = View.VISIBLE
                    prefs.edit().putInt(PREF_SELECTED_LIST_TYPE, 1).apply()
                    if (getHistoryEntries(prefs).isEmpty()) {
                        flashError(R.string.empty_history)
                    }
                }
            }
        }

        popupWindow.showAsDropDown(anchorView)
    }

    /**
     * RecyclerView适配器，用于文件后缀和历史记录列表。
     */
    private class ItemAdapter(
        private var items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

        class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: android.widget.TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val textView = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false) as android.widget.TextView
            return ItemViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = items[position]
            holder.textView.text = item
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size

        fun updateData(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    /**
     * 将新的条目添加到历史记录中，并保持最大大小。
     * 使用 Gson 序列化 List<String> 以保持顺序。
     *
     * @param prefs SharedPreferences 实例。
     * @param entry 要添加的历史记录条目。
     */
    private fun addHistoryEntry(prefs: SharedPreferences, entry: String) {
        if (entry.isBlank()) return

        val historyList = getHistoryEntries(prefs).toMutableList()

        historyList.remove(entry)
        historyList.add(0, entry)

        while (historyList.size > MAX_HISTORY_SIZE) {
            historyList.removeAt(historyList.size - 1)
        }

        val jsonString = gson.toJson(historyList)
        prefs.edit().putString(PREF_HISTORY_LIST, jsonString).apply()
    }

    /**
     * 获取历史记录列表。
     * 使用 Gson 反序列化 JSON 字符串为 List<String>。
     *
     * @param prefs SharedPreferences 实例。
     * @return 历史记录列表。
     */
    private fun getHistoryEntries(prefs: SharedPreferences): List<String> {
        val jsonString = prefs.getString(PREF_HISTORY_LIST, null)
        return if (jsonString != null) {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            emptyList()
        }
    }

    /**
     * 检查文件名是否有效，不包含文件系统不支持的特殊字符。
     * @param fileName 要检查的文件名。
     * @return 如果文件名有效则返回true，否则返回false。
     */
    private fun isValidFileName(fileName: String): Boolean {
        val commonInvalidCharsForFilesystems = charArrayOf('\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
        return fileName.none { it in commonInvalidCharsForFilesystems }
    }

    /**
     * 获取文件名中不支持的特殊字符。
     * 仅用于生成错误消息。
     * @param fileName 要检查的文件名。
     * @return 包含不支持字符的字符串。
     */
    private fun getUnsupportedCharacters(fileName: String): String {
        val commonInvalidChars = charArrayOf('\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
        return fileName.filter { char -> char in commonInvalidChars }.toSet().joinToString(" ") { char -> "'$char'" }
    }
}