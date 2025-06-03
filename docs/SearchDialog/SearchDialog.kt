package modder.hub.dexeditor.activity

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.textfield.TextInputLayout
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.ArrayList
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import modder.hub.dexeditor.R // 确保 R 文件已正确导入

// 引入 FileUtil 类
import com.itsaky.androidide.utils.FileUtil

/**
 * 中文注释: SearchDialog 类用于提供一个文件和文件夹的搜索功能对话框。
 * 它支持正则表达式、区分大小写、子目录搜索、文件/文件夹名称搜索、文件内容搜索以及文件大小过滤。
 * 搜索结果将显示在一个列表中，用户可以点击打开文件/文件夹或长按复制路径。
 * English annotation: The SearchDialog class provides a search functionality dialog for files and folders.
 * It supports regular expressions, case sensitivity, subdirectory search, file/folder name search,
 * file content search, and file size filtering. Search results will be displayed in a list,
 * allowing users to click to open files/folders or long-press to copy paths.
 */
class SearchDialog(context: Context) : Dialog(context) {

    // UI elements
    /**
     * 中文注释: 搜索路径输入框。
     * English annotation: Search path input field.
     */
    private lateinit var searchPathEditText: EditText
    /**
     * 中文注释: 文件/文件夹名称搜索输入框。
     * English annotation: File/folder name search input field.
     */
    private lateinit var fileNameFolderSearchEditText: EditText
    /**
     * 中文注释: 内容搜索输入框（高级搜索）。
     * English annotation: Content search input field (advanced search).
     */
    private lateinit var contentSearchEditText: EditText
    /**
     * 中文注释: 最小文件大小输入框。
     * English annotation: Minimum file size input field.
     */
    private lateinit var minSizeEditText: EditText
    /**
     * 中文注释: 最大文件大小输入框。
     * English annotation: Maximum file size input field.
     */
    private lateinit var maxSizeEditText: EditText
    /**
     * 中文注释: 正则表达式匹配复选框。
     * English annotation: Regular expression matching checkbox.
     */
    private lateinit var regexCheckBox: CheckBox
    /**
     * 中文注释: 区分大小写复选框。
     * English annotation: Case sensitivity checkbox.
     */
    private lateinit var caseSensitiveCheckBox: CheckBox
    /**
     * 中文注释: 搜索子目录复选框。
     * English annotation: Search subdirectories checkbox.
     */
    private lateinit var searchSubdirectoriesCheckBox: CheckBox
    /**
     * 中文注释: 高级搜索（内容搜索）复选框。
     * English annotation: Advanced search (content search) checkbox.
     */
    private lateinit var advancedSearchCheckBox: CheckBox
    /**
     * 中文注释: 高级搜索输入框的布局容器。
     * English annotation: Layout container for the advanced search input field.
     */
    private lateinit var contentSearchInputLayout: TextInputLayout

    // Search parameters
    /**
     * 中文注释: 最小文件大小（字节）。
     * English annotation: Minimum file size in bytes.
     */
    private var minFileSize: Long = 0
    /**
     * 中文注释: 最大文件大小（字节）。
     * English annotation: Maximum file size in bytes.
     */
    private var maxFileSize: Long = Long.MAX_VALUE
    /**
     * 中文注释: 最小文件大小单位。
     * English annotation: Unit for minimum file size.
     */
    private var minSizeUnit: String = "b"
    /**
     * 中文注释: 最大文件大小单位。
     * English annotation: Unit for maximum file size.
     */
    private var maxSizeUnit: String = "b"

    /**
     * 中文注释: SharedPreferences 用于存储搜索设置。
     * English annotation: SharedPreferences for storing search settings.
     */
    private val searchPrefs: SharedPreferences
    /**
     * 中文注释: SharedPreferences 的键，用于存储子目录搜索设置。
     * English annotation: SharedPreferences key for storing subdirectory search setting.
     */
    private val PREF_SEARCH_SUBDIRECTORIES = "search_subdirectories"
    /**
     * 中文注释: SharedPreferences 的键，用于存储搜索路径。
     * English annotation: SharedPreferences key for storing search path.
     */
    private val PREF_SEARCH_PATH = "search_path"
    /**
     * 中文注释: 默认搜索路径。
     * English annotation: Default search path.
     */
    private val DEFAULT_SEARCH_PATH = Environment.getExternalStorageDirectory().absolutePath + "/AndroidIDEProjects/"

    /**
     * 中文注释: 搜索任务对象。
     * English annotation: Search task object.
     */
    private var currentSearchTask: SearchTask? = null
    /**
     * 中文注释: 搜索进度对话框。
     * English annotation: Search progress dialog.
     */
    private var searchProgressDialog: ProgressDialog? = null
    /**
     * 中文注释: 搜索进度文本视图。
     * English annotation: Search progress text view.
     */
    private var searchProgressTextView: TextView? = null

    /**
     * 中文注释: 搜索结果列表。
     * English annotation: List of search results.
     */
    private var searchResults: MutableList<String> = mutableListOf()

    init {
        searchPrefs = context.getSharedPreferences("SearchSettings", Context.MODE_PRIVATE)
    }

    /**
     * 中文注释: 在对话框创建时调用。
     * English annotation: Called when the dialog is created.
     * @param savedInstanceState 中文注释: 包含上次保存状态的 Bundle。 English annotation: Bundle containing the last saved state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_search)

        // Initialize UI elements
        // 中文注释: 初始化UI元素
        // English annotation: Initialize UI elements
        searchPathEditText = findViewById(R.id.search_path_edit_text)
        fileNameFolderSearchEditText = findViewById(R.id.file_name_folder_search_edit_text)
        contentSearchEditText = findViewById(R.id.content_search_edit_text)
        minSizeEditText = findViewById(R.id.min_size_edit_text)
        maxSizeEditText = findViewById(R.id.max_size_edit_text)
        regexCheckBox = findViewById(R.id.regex_checkbox)
        caseSensitiveCheckBox = findViewById(R.id.case_sensitive_checkbox)
        searchSubdirectoriesCheckBox = findViewById(R.id.search_subdirectories_checkbox)
        advancedSearchCheckBox = findViewById(R.id.advanced_search_checkbox)
        contentSearchInputLayout = findViewById(R.id.content_search_input_layout)

        val confirmButton: Button = findViewById(R.id.confirm_search_button)
        val cancelButton: Button = findViewById(R.id.cancel_search_button)

        // Set default values and load preferences
        // 中文注释: 设置默认值并加载偏好设置
        // English annotation: Set default values and load preferences
        val savedPath = searchPrefs.getString(PREF_SEARCH_PATH, DEFAULT_SEARCH_PATH)
        searchPathEditText.setText(savedPath)

        searchSubdirectoriesCheckBox.isChecked = searchPrefs.getBoolean(PREF_SEARCH_SUBDIRECTORIES, false)

        // Set up listeners
        // 中文注释: 设置监听器
        // English annotation: Set up listeners
        advancedSearchCheckBox.setOnCheckedChangeListener { _, isChecked ->
            contentSearchInputLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        minSizeEditText.setOnClickListener { showSizeUnitDialog(minSizeEditText, true) }
        maxSizeEditText.setOnClickListener { showSizeUnitDialog(maxSizeEditText, false) }

        confirmButton.setOnClickListener { startSearch() }
        cancelButton.setOnClickListener { dismiss() }

        // Save search path on text change (debounced for performance)
        // 中文注释: 在文本更改时保存搜索路径（为提高性能而进行防抖）
        // English annotation: Save search path on text change (debounced for performance)
        searchPathEditText.addTextChangedListener(object : TextWatcher {
            private val handler = Handler(Looper.getMainLooper())
            private var runnable: Runnable? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runnable?.let { handler.removeCallbacks(it) }
            }

            override fun afterTextChanged(s: Editable?) {
                runnable = Runnable {
                    searchPrefs.edit().putString(PREF_SEARCH_PATH, s.toString()).apply()
                }
                handler.postDelayed(runnable!!, 1000) // Save after 1 second of no changes
            }
        })

        // Initial state for advanced search
        // 中文注释: 高级搜索的初始状态
        // English annotation: Initial state for advanced search
        contentSearchInputLayout.visibility = if (advancedSearchCheckBox.isChecked) View.VISIBLE else View.GONE
    }

    /**
     * 中文注释: 显示文件大小单位选择对话框。
     * English annotation: Displays the file size unit selection dialog.
     * @param editText 中文注释: 关联的 EditText。 English annotation: The associated EditText.
     * @param isMin 中文注释: 是否为最小大小。 English annotation: True if for minimum size, false otherwise.
     */
    private fun showSizeUnitDialog(editText: EditText, isMin: Boolean) {
        val units = arrayOf("b", "kb", "mb", "gb", "无限制 / Unlimited")
        val builder = AlertDialog.Builder(context)
        builder.setTitle("选择单位 / Select Unit")
        builder.setItems(units) { dialog, which ->
            val selectedUnit = units[which]
            if (selectedUnit == "无限制 / Unlimited") {
                editText.setText("") // Clear text for unlimited
                if (isMin) {
                    minFileSize = 0
                    minSizeUnit = "b"
                } else {
                    maxFileSize = Long.MAX_VALUE
                    maxSizeUnit = "b"
                }
            } else {
                // Prompt for number input
                // 中文注释: 提示输入数字
                // English annotation: Prompt for number input
                val inputBuilder = AlertDialog.Builder(context)
                inputBuilder.setTitle("输入大小 / Enter Size ($selectedUnit)")
                val input = EditText(context)
                input.inputType = InputType.TYPE_CLASS_NUMBER
                inputBuilder.setView(input)
                inputBuilder.setPositiveButton("确定 / OK") { _, _ ->
                    val value = input.text.toString().trim()
                    if (value.isEmpty()) {
                        Toast.makeText(context, "值不能为空 / Value cannot be empty", Toast.LENGTH_SHORT).show()
                        // Reset to unlimited if input is empty after trying to set a value
                        // 中文注释: 如果输入为空，则在尝试设置值后重置为无限制
                        // English annotation: Reset to unlimited if input is empty after trying to set a value
                        if (isMin) {
                            minFileSize = 0
                            minSizeUnit = "b"
                        } else {
                            maxFileSize = Long.MAX_VALUE
                            maxSizeUnit = "b"
                        }
                        editText.setText("") // Keep it clear
                        return@setPositiveButton
                    }
                    try {
                        val size = value.toLong()
                        val sizeInBytes = convertToBytes(size, selectedUnit)
                        if (isMin) {
                            minFileSize = sizeInBytes
                            minSizeUnit = selectedUnit
                        } else {
                            maxFileSize = sizeInBytes
                            maxSizeUnit = selectedUnit
                        }
                        editText.setText("$size $selectedUnit")
                    } catch (e: NumberFormatException) {
                        Toast.makeText(context, "无效数字 / Invalid number", Toast.LENGTH_SHORT).show()
                    }
                }
                inputBuilder.setNegativeButton("取消 / Cancel", null)
                inputBuilder.show()
            }
        }
        builder.show()
    }

    /**
     * 中文注释: 将给定大小和单位转换为字节。
     * English annotation: Converts the given size and unit to bytes.
     * @param size 中文注释: 大小值。 English annotation: Size value.
     * @param unit 中文注释: 单位 (b, kb, mb, gb)。 English annotation: Unit (b, kb, mb, gb).
     * @return 中文注释: 字节数。 English annotation: Size in bytes.
     */
    private fun convertToBytes(size: Long, unit: String): Long {
        return when (unit.toLowerCase(Locale.ROOT)) {
            "kb" -> size * 1024
            "mb" -> size * 1024 * 1024
            "gb" -> size * 1024 * 1024 * 1024
            "b" -> size
            else -> size
        }
    }

    /**
     * 中文注释: 开始文件搜索过程。
     * English annotation: Starts the file search process.
     */
    private fun startSearch() {
        val searchPath = searchPathEditText.text.toString().trim()
        val fileNameKeyword = fileNameFolderSearchEditText.text.toString().trim()
        val contentKeyword = contentSearchEditText.text.toString().trim()
        val useRegex = regexCheckBox.isChecked
        val caseSensitive = caseSensitiveCheckBox.isChecked
        val searchSubdirectories = searchSubdirectoriesCheckBox.isChecked
        val advancedSearchEnabled = advancedSearchCheckBox.isChecked

        // Validate search path
        // 中文注释: 验证搜索路径
        // English annotation: Validate search path
        val rootDir = File(searchPath)
        if (!rootDir.exists()) {
            Toast.makeText(context, "搜索路径不存在 / Search path does not exist", Toast.LENGTH_LONG).show()
            return
        }
        if (!rootDir.isDirectory) {
            Toast.makeText(context, "搜索路径不是一个目录 / Search path is not a directory", Toast.LENGTH_LONG).show()
            return
        }

        // Validate search keywords
        // 中文注释: 验证搜索关键词
        // English annotation: Validate search keywords
        if (fileNameKeyword.isEmpty() && (!advancedSearchEnabled || contentKeyword.isEmpty())) {
            Toast.makeText(context, "请输入文件/文件夹名称关键词或启用高级搜索并输入内容关键词 / Please enter file/folder name keyword OR enable advanced search and enter content keyword", Toast.LENGTH_LONG).show()
            return
        }

        // Validate regex pattern if enabled
        // 中文注释: 如果启用正则表达式，验证其模式
        // English annotation: Validate regex pattern if enabled
        if (useRegex) {
            try {
                if (fileNameKeyword.isNotEmpty()) {
                    Pattern.compile(fileNameKeyword)
                }
                if (advancedSearchEnabled && contentKeyword.isNotEmpty()) {
                    Pattern.compile(contentKeyword)
                }
            } catch (e: PatternSyntaxException) {
                Toast.makeText(context, "正则表达式无效: ${e.message} / Invalid Regex: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Save search subdirectories preference
        // 中文注释: 保存搜索子目录偏好设置
        // English annotation: Save search subdirectories preference
        searchPrefs.edit().putBoolean(PREF_SEARCH_SUBDIRECTORIES, searchSubdirectories).apply()

        dismiss() // Dismiss the search options dialog

        // Show searching dialog
        // 中文注释: 显示搜索中对话框
        // English annotation: Show searching dialog
        searchProgressDialog = ProgressDialog(context).apply {
            setTitle("正在搜索 / Searching...")
            setMessage("初始化 / Initializing...")
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setButton(DialogInterface.BUTTON_NEGATIVE, "停止 / Stop") { _, _ ->
                currentSearchTask?.cancel(true)
            }
            show()
        }

        // Customize the message area to show the progress text view
        // 中文注释: 自定义消息区域以显示进度文本视图
        // English annotation: Customize the message area to show the progress text view
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_progress_custom, null)
        searchProgressTextView = dialogView.findViewById(R.id.progress_text_view)
        // Replacing default message TextView with custom one
        // 中文注释: 用自定义的 TextView 替换默认的消息 TextView
        // English annotation: Replacing default message TextView with custom one
        (searchProgressDialog?.findViewById<TextView>(android.R.id.message)?.parent as? ViewGroup)?.apply {
            removeView(searchProgressDialog?.findViewById(android.R.id.message))
            addView(dialogView, 0)
        }


        // Start search task
        // 中文注释: 启动搜索任务
        // English annotation: Start search task
        searchResults.clear()
        currentSearchTask = SearchTask(
            rootDir,
            fileNameKeyword,
            contentKeyword,
            useRegex,
            caseSensitive,
            searchSubdirectories,
            advancedSearchEnabled,
            minFileSize,
            maxFileSize
        )
        currentSearchTask?.execute()
    }

    /**
     * 中文注释: 异步搜索任务。
     * English annotation: Asynchronous search task.
     */
    private inner class SearchTask(
        private val rootDirectory: File,
        private val fileNameKeyword: String,
        private val contentKeyword: String,
        private val useRegex: Boolean,
        private val caseSensitive: Boolean,
        private val searchSubdirectories: Boolean,
        private val advancedSearchEnabled: Boolean,
        private val minSize: Long,
        private val maxSize: Long
    ) : AsyncTask<Void, String, List<String>>() {

        override fun onPreExecute() {
            super.onPreExecute()
            // Optional: Any setup before search starts.
            // searchProgressDialog is already shown in startSearch()
        }

        override fun doInBackground(vararg voids: Void?): List<String> {
            val results = mutableListOf<String>()
            performSearch(rootDirectory, results)
            return results
        }

        /**
         * 中文注释: 递归执行文件搜索。
         * English annotation: Recursively performs file search.
         * @param directory 中文注释: 当前搜索的目录。 English annotation: The current directory being searched.
         * @param results 中文注释: 搜索结果列表。 English annotation: The list to add search results to.
         */
        private fun performSearch(directory: File, results: MutableList<String>) {
            if (isCancelled) {
                return
            }

            val files = directory.listFiles() ?: return

            for (file in files) {
                if (isCancelled) {
                    return
                }

                publishProgress(file.absolutePath) // Update progress with current file/folder

                var matches = false

                // 1. Check file/folder name match
                // 中文注释: 检查文件/文件夹名称匹配
                // English annotation: Check file/folder name match
                if (fileNameKeyword.isNotEmpty()) {
                    matches = checkNameMatch(file.name, fileNameKeyword, useRegex, caseSensitive)
                } else {
                    // If no file name keyword, it implicitly matches for name if content search is on
                    // 中文注释: 如果没有文件名关键词，则在内容搜索开启时隐式匹配名称
                    // English annotation: If no file name keyword, it implicitly matches for name if content search is on
                    matches = true
                }

                if (matches) { // Only proceed if name matches (or no name keyword provided)
                    // 2. Check content match if advanced search is enabled and it's a file
                    // 中文注释: 如果启用高级搜索且是文件，检查内容匹配
                    // English annotation: If advanced search is enabled and it's a file, check content match
                    var contentMatches = true // Assume true if advanced search is not enabled or no keyword
                    if (advancedSearchEnabled && file.isFile && contentKeyword.isNotEmpty()) {
                        contentMatches = checkContentMatch(file, contentKeyword, useRegex, caseSensitive)
                    } else if (advancedSearchEnabled && !file.isFile && contentKeyword.isNotEmpty()) {
                        // If advanced search is enabled and there's a content keyword,
                        // but it's a directory, it cannot match content, so it doesn't match.
                        // 中文注释: 如果启用高级搜索且有内容关键词，但它是目录，则无法匹配内容，因此不匹配。
                        // English annotation: If advanced search is enabled and there's a content keyword,
                        // but it's a directory, it cannot match content, so it doesn't match.
                        contentMatches = false
                    }
                    
                    if (matches && contentMatches) { // Only proceed if name and content match
                        // 3. Check file size filter (only for files)
                        // 中文注释: 检查文件大小过滤（仅限文件）
                        // English annotation: Check file size filter (files only)
                        var sizeMatches = true
                        if (file.isFile) {
                            val fileSize = FileUtil.getFileLength(file.absolutePath) // Using FileUtil
                            if (fileSize < minSize || fileSize > maxSize) {
                                sizeMatches = false
                            }
                        }

                        if (sizeMatches) {
                            results.add(file.absolutePath)
                        }
                    }
                }

                // Recurse into subdirectories if allowed
                // 中文注释: 如果允许，递归进入子目录
                // English annotation: Recurse into subdirectories if allowed
                if (file.isDirectory && searchSubdirectories) {
                    performSearch(file, results)
                }
            }
        }

        /**
         * 中文注释: 检查名称是否匹配。
         * English annotation: Checks if the name matches.
         */
        private fun checkNameMatch(name: String, keyword: String, useRegex: Boolean, caseSensitive: Boolean): Boolean {
            return if (useRegex) {
                val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
                val pattern = Pattern.compile(keyword, flags)
                pattern.matcher(name).find() // Use find() for partial match
            } else {
                if (caseSensitive) {
                    name.contains(keyword)
                } else {
                    name.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))
                }
            }
        }

        /**
         * 中文注释: 检查文件内容是否匹配。
         * English annotation: Checks if file content matches.
         */
        private fun checkContentMatch(file: File, keyword: String, useRegex: Boolean, caseSensitive: Boolean): Boolean {
            if (!FileUtil.isFile(file.absolutePath) || !file.canRead()) { // Using FileUtil.isFile
                return false // Cannot read file content or not a file
            }
            try {
                // Using FileUtil.readFile to get content
                val content = FileUtil.readFile(file.absolutePath)
                if (useRegex) {
                    val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
                    val pattern = Pattern.compile(keyword, flags)
                    return pattern.matcher(content).find()
                } else {
                    return if (caseSensitive) {
                        content.contains(keyword)
                    } else {
                        content.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))
                    }
                }
            } catch (e: Exception) { // Catching generic Exception for FileUtil's readFile
                e.printStackTrace()
            }
            return false
        }

        override fun onProgressUpdate(vararg values: String?) {
            super.onProgressUpdate(*values)
            values[0]?.let {
                searchProgressTextView?.text = "正在搜索: $it / Searching: $it"
            }
        }

        override fun onPostExecute(result: List<String>?) {
            super.onPostExecute(result)
            searchProgressDialog?.dismiss()

            if (result.isNullOrEmpty()) {
                Toast.makeText(context, "未找到结果 / No results found", Toast.LENGTH_LONG).show()
            } else {
                searchResults.clear()
                searchResults.addAll(result)
                showSearchResultsDialog()
            }
        }

        override fun onCancelled(result: List<String>?) {
            super.onCancelled(result)
            searchProgressDialog?.dismiss()
            Toast.makeText(context, "搜索已取消 / Search cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 中文注释: 显示搜索结果对话框。
     * English annotation: Displays the search results dialog.
     */
    private fun showSearchResultsDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("搜索结果 (${searchResults.size}) / Search Results (${searchResults.size})")

        val listView = ListView(context)
        val adapter = ArrayAdapter(context,
                android.R.layout.simple_list_item_1, searchResults)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val path = searchResults[position]
            val file = File(path)
            openFileOrFolder(file)
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val path = searchResults[position]
            copyPathToClipboard(path)
            true // Consume the long click
        }

        builder.setView(listView)
        builder.setPositiveButton("确定 / OK", null)
        builder.show()
    }

    /**
     * 中文注释: 打开文件或文件夹。
     * English annotation: Opens a file or folder.
     * @param file 中文注释: 要打开的文件或文件夹。 English annotation: The file or folder to open.
     */
    private fun openFileOrFolder(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri: Uri

        try {
            uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, "无法获取文件URI: ${e.message} / Cannot get file URI: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }

        if (file.isDirectory) {
            // For directories, ACTION_VIEW with a generic MIME type might open a file manager.
            // There's no standard MIME type for "folder".
            intent.setDataAndType(uri, "resource/folder")
        } else {
            val mimeType = getMimeType(file.absolutePath)
            if (mimeType == null) {
                intent.setDataAndType(uri, "*/*") // Fallback to all types if MIME type not found
            } else {
                intent.setDataAndType(uri, mimeType)
            }
        }

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Recommended for intents outside of an Activity

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开文件/文件夹: ${e.message} / Could not open file/folder: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    /**
     * 中文注释: 获取文件MIME类型。
     * English annotation: Gets the MIME type of a file.
     * @param url 中文注释: 文件路径。 English annotation: File path.
     * @return 中文注释: MIME类型字符串。 English annotation: MIME type string.
     */
    private fun getMimeType(url: String): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
    }

    /**
     * 中文注释: 将路径复制到剪贴板。
     * English annotation: Copies the path to the clipboard.
     * @param path 中文注释: 要复制的路径。 English annotation: The path to copy.
     */
    private fun copyPathToClipboard(path: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("File Path", path)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "路径已复制到剪贴板: $path / Path copied to clipboard: $path", Toast.LENGTH_SHORT).show()
    }
}
