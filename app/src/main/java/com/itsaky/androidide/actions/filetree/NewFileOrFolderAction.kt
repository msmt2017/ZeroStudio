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
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.R.layout
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

        val dialogView = LayoutInflater.from(activityContext).inflate(layout.layout_dialog_fodle_orfile, null)
        val builder = DialogUtils.newMaterialDialogBuilder(activityContext)

        // 获取UI元素引用
        val editText = dialogView.findViewById<TextInputEditText>(R.id.edit_text_name_input)
        val dropdownArrow = dialogView.findViewById<ImageButton>(R.id.dropdown_arrow)
        val checkboxRemoveSpaces = dialogView.findViewById<CheckBox>(R.id.checkbox_remove_spaces)
        // 获取新增的复选框引用
        val checkboxDotsToSlashes = dialogView.findViewById<CheckBox>(R.id.checkbox_dots_to_slashes)


        // 获取自定义按钮的引用
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnPaste = dialogView.findViewById<MaterialButton>(R.id.btn_paste)
        val btnFile = dialogView.findViewById<MaterialButton>(R.id.btn_file)
        val btnFolder = dialogView.findViewById<MaterialButton>(R.id.btn_folder)

        // --- Start of Robustness Check ---
        // 关键UI组件的空检查。如果缺少任何一个，对话框都无法正常工作。
        // if (editText == null || dropdownArrow == null || checkboxRemoveSpaces == null ||
        // checkboxDotsToSlashes == null || // 确保新增的复选框也被检查
        // btnCancel == null || btnPaste == null || btnFile == null || btnFolder == null) {
        // flashError(activityContext.getString(R.string.msg_ui_component_not_found))
        // // 在生产环境中，建议也记录此错误以了解缺少哪个组件
        // return // 如果没有主输入字段或关键按钮，则无法继续
        // }
        // --- End of Robustness Check ---

        // 设置输入框提示和对话框标题
        builder.setTitle(R.string.new_folder)
        builder.setMessage(R.string.msg_can_contain_slashesfile)
        builder.setView(dialogView) // 设置自定义布局
        builder.setCancelable(false) // 对话框不可取消关闭

        // ====================================================================
        // 1. 设置InputFilter限制非法字符输入，现在允许 '.' 字符
        // 允许的字符包括字母、数字、连字符、下划线、斜杠和点号
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
        // 3. 新增的“将点转换为斜杠”复选框功能
        // 默认不勾选，不记忆状态
        checkboxDotsToSlashes.isChecked = false
        // 无需设置 OnCheckedChangeListener，因为其状态不持久化
        // ====================================================================

        // ====================================================================
        // 4. 粘贴按钮功能
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
        // 5. 下拉箭头按钮和弹出列表功能
        dropdownArrow.setOnClickListener { view: View ->
            showSuffixHistoryPopup(activityContext, view, editText, prefs)
        }
        // ====================================================================

        // ====================================================================
        // 6. 对话框底部按钮的点击事件
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
            // 创建文件，此时忽略 checkboxDotsToSlashes 状态
            handleCreation(activityContext, currentDir, lastHeld, inputName, true, false)
        }

        btnFolder.setOnClickListener { _: View ->
            dialog.dismiss()
            var inputName = editText.text.toString().trim()
            if (checkboxRemoveSpaces.isChecked) {
                inputName = inputName.replace(" ", "") // 移除所有空格
            }
            // 将原始输入（处理空格后）添加到历史记录
            addHistoryEntry(prefs, inputName)
            // 创建文件夹，考虑 checkboxDotsToSlashes 状态
            handleCreation(activityContext, currentDir, lastHeld, inputName, false, checkboxDotsToSlashes.isChecked)
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
     * @param convertDotsToSlashes 如果为true且创建文件夹时，将点转换为斜杠。
     */
    private fun handleCreation(
        context: Context,
        currentDir: File,
        lastHeld: TreeNode?,
        inputName: String,
        isFileToCreate: Boolean,
        convertDotsToSlashes: Boolean // 新增参数
    ) {
        if (inputName.isEmpty()) {
            flashError(R.string.msg_invalid_name)
            return
        }

        val processedPath: String
        if (isFileToCreate) {
            // 创建文件时，只处理反斜杠，保留点号用于文件扩展名
            processedPath = inputName.replace("\\", "/").trimStart('/').trimEnd('/')
        } else {
            // 创建文件夹时，如果勾选了复选框，则将点号转换为斜杠
            var tempPath = inputName.replace("\\", "/")
            if (convertDotsToSlashes) {
                tempPath = tempPath.replace(".", "/")
            }
            processedPath = tempPath.trimStart('/').trimEnd('/')
        }


        if (processedPath.isEmpty()) {
            flashError(R.string.msg_invalid_name)
            return
        }

        // 验证处理后的路径名
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
            // 明确处理文件路径和文件名
            val lastSlashIndex = processedPath.lastIndexOf('/')
            val parentPathSegment = if (lastSlashIndex != -1) processedPath.substring(0, lastSlashIndex) else ""
            val fileName = if (lastSlashIndex != -1) processedPath.substring(lastSlashIndex + 1) else processedPath

            if (fileName.isEmpty()) {
                flashError(R.string.msg_invalid_name)
                return
            }
            // 如果文件名只包含点（例如 "." 或 "..")，或者以点开头（隐藏文件），这些在某些文件系统中是特殊的，需要额外的判断，但目前允许
            // 如果需要限制，可以在此处添加更多校验

            val finalParentDir = if (parentPathSegment.isNotEmpty()) File(currentDir, parentPathSegment) else currentDir
            targetFileOrFolder = File(finalParentDir, fileName)

            createFile(context, targetFileOrFolder, lastHeld)

        } else {
            // 创建文件夹
            targetFileOrFolder = File(currentDir, processedPath)
            createFolder(context, targetFileOrFolder, lastHeld)
        }
    }

    /**
     * 创建文件。
     */
    private fun createFile(context: Context, targetFile: File, lastHeld: TreeNode?) {
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
                updateFileTreeUI(context, lastHeld, targetFile) // Now passing targetFile directly
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
    private fun createFolder(context: Context, targetFolder: File, lastHeld: TreeNode?) {
        if (targetFolder.exists()) {
            flashError(R.string.msg_folder_exists)
            return
        }

        try {
            if (targetFolder.mkdirs()) {
                flashSuccess(R.string.msg_folder_created)
                updateFileTreeUI(context, lastHeld, targetFolder) // Now passing targetFolder directly
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
     * 尝试在父节点下添加新创建的节点并展开新节点。
     * 如果新节点不在当前选择的节点下（如多级创建），则刷新整个列表。
     */
    private fun updateFileTreeUI(context: Context, lastHeld: TreeNode?, newEntry: File) {
        val lastHeldFile = lastHeld?.value as? File

        // Check if newEntry's direct parent is the file represented by lastHeld
        // This ensures we only try to add a child if lastHeld is indeed its direct parent in the tree
        if (lastHeld != null && lastHeldFile != null && newEntry.parentFile?.absolutePath == lastHeldFile.absolutePath) {
            // Create a new TreeNode for the newly created file/folder
            val newNode = TreeNode(newEntry)
            newNode.viewHolder = FileTreeViewHolder(context) // Ensure this is correctly initialized

            // Add the new node as a child of the lastHeld (parent) node
            lastHeld.addChild(newNode)

            // Request to expand the *newly added node*. This should make it visible and potentially scroll to it.
            requestExpandNode(newNode)
        } else {
            // If lastHeld is null, or newEntry is not a direct child of lastHeld (e.g., multi-level folder creation like "a/b/c"),
            // or if the parent of newEntry is the project root (and lastHeld is not the root node),
            // then a full file listing refresh is necessary.
            requestFileListing()
        }
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

        // 弹出窗口组件的空检查
        if (radioGroup == null || radioSuffix == null || radioHistory == null ||
            recyclerViewSuffix == null || recyclerViewHistory == null) {
            flashError(context.getString(R.string.msg_ui_component_not_found))
            return
        }

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
     * 检查文件名是否有效，不包含文件系统不支持的特殊字符。
     * 现在允许文件路径包含点号 (.), 但不允许操作系统级别的非法字符.
     * @param fileName 要检查的文件名。
     * @return 如果文件名有效则返回true，否则返回false。
     */
    private fun isValidFileName(fileName: String): Boolean {
        // 常见的操作系统不支持的字符： \ : * ? " < > | NUL (空字符)
        // 注意：斜杠 '/' 在这里被认为是路径分隔符，而不是文件名中的非法字符，因为它在处理过程中会被标准化。
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
