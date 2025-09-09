package com.itsaky.androidide.fragments.sidebar

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.blankj.utilcode.util.SizeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.itsaky.androidide.fragments.sidebar.datatree.FileTreeViewHolder
import com.itsaky.androidide.databinding.LayoutDataFileTreeBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.events.ExpandTreeNodeRequestEvent
import com.itsaky.androidide.events.ListProjectFilesRequestEvent
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R.drawable
import com.itsaky.androidide.tasks.TaskExecutor
import com.itsaky.androidide.tasks.callables.FileTreeCallable
import com.itsaky.androidide.tasks.callables.FileTreeCallable.SortFileName
import com.itsaky.androidide.tasks.callables.FileTreeCallable.SortFolder
import com.itsaky.androidide.utils.doOnApplyWindowInsets
import com.itsaky.androidide.viewmodel.FileTreeViewModel
import com.unnamed.b.atv.model.TreeNode
import com.unnamed.b.atv.model.TreeNode.TreeNodeClickListener
import com.unnamed.b.atv.model.TreeNode.TreeNodeLongClickListener
import com.unnamed.b.atv.view.AndroidTreeView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import java.io.File
import java.util.Arrays
import java.util.HashSet
import java.util.concurrent.Callable

/**
 * 中文注释: DataFileTreeFragment 是一个底部弹窗 Fragment，用于显示文件树结构，
 * 支持本地文件系统和通过 DocumentsProvider（特别是 MTDataFilesProvider）暴露的私有数据目录。
 * 它处理用户权限请求，与 DocumentsUI 交互以选择目录，并使用 AndroidTreeView 展示文件层级。
 * English annotation: DataFileTreeFragment is a BottomSheetDialogFragment used to display a file tree structure,
 * supporting both local file system and private data directories exposed via DocumentsProvider (specifically MTDataFilesProvider).
 * It handles user permission requests, interacts with DocumentsUI to select directories, and uses AndroidTreeView to present the file hierarchy.
 *作者：android_zero（零丶） github：msmt2017
 */
class DataFileTreeFragment : BottomSheetDialogFragment(), TreeNodeClickListener,
    TreeNodeLongClickListener {

    /**
     * 中文注释: ViewBinding 实例，用于访问布局文件中的视图。
     * English annotation: ViewBinding instance for accessing views in the layout file.
     */
    private var binding: LayoutDataFileTreeBinding? = null
    /**
     * 中文注释: AndroidTreeView 实例，用于显示可折叠的文件树。
     * English annotation: AndroidTreeView instance for displaying the collapsible file tree.
     */
    private var fileTreeView: AndroidTreeView? = null
    /**
     * 中文注释: FileTreeViewModel 的委托实例，用于在配置更改后保留文件树的状态。
     * English annotation: Delegated instance of FileTreeViewModel, used to preserve the file tree state across configuration changes.
     */
    private val viewModel by viewModels<FileTreeViewModel>(ownerProducer = { requireActivity() })

    /**
     * 中文注释: 存储权限请求码。
     * English annotation: Request code for storage permission.
     */
    private val REQUEST_CODE_STORAGE_PERMISSION = 100
    /**
     * 中文注释: 选择目录的请求码，用于 DocumentsUI 的返回结果。
     * English annotation: Request code for directory selection, used for results from DocumentsUI.
     */
    private val REQUEST_CODE_SELECT_DIRECTORY = 101
    /**
     * 中文注释: 用于保存挂载目录路径的 SharedPreferences 键。
     * English annotation: SharedPreferences key for saving the mounted directory path.
     */
    private val PREF_MOUNTED_DIR = "mounted_directory"
    /**
     * 中文注释: SharedPreferences 实例，用于存储和检索持久化数据，例如挂载的目录路径。
     * English annotation: SharedPreferences instance for storing and retrieving persistent data, such as the mounted directory path.
     */
    private lateinit var sharedPreferences: SharedPreferences

    /**
     * 中文注释: onCreateView 方法在 Fragment 创建视图时调用。
     * 它负责初始化视图绑定、注册 EventBus，并设置窗口内边距。
     * English annotation: The onCreateView method is called when the Fragment is creating its view.
     * It is responsible for inflating the view binding, registering EventBus, and setting window insets.
     * @param inflater 中文注释: 用于膨胀布局的 LayoutInflater。 English annotation: The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container 中文注释: 如果非空，这是父视图，Fragment 的 UI 将被附加到其中。 English annotation: If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState 中文注释: 如果 Fragment 正在重新创建，这是一个非空 Bundle，包含上次保存的状态。 English annotation: If non-null, this fragment is being re-constructed from a previous saved state.
     * @return 中文注释: Fragment 的根视图。 English annotation: The root view of the fragment.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }

        binding = LayoutDataFileTreeBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("FileTreePrefs", Context.MODE_PRIVATE)

        binding?.root?.doOnApplyWindowInsets { view, insets, _, _ ->
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).apply { view.updatePadding(top = top + SizeUtils.dp2px(8f)) }
        }
        return binding!!.root
    }

    /**
     * 中文注释: onViewCreated 方法在视图创建后调用。它初始化底部栏并尝试加载文件列表。
     * English annotation: The onViewCreated method is called after the view has been created. It initializes the bottom bar and attempts to list project files.
     * @param view 中文注释: Fragment 的视图。 English annotation: The view of the fragment.
     * @param savedInstanceState 中文注释: 如果 Fragment 正在重新创建，这是一个非空 Bundle，包含上次保存的状态。 English annotation: If non-null, this fragment is being re-constructed from a previous saved state.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initBottomBar()
        listProjectFiles()
    }

    /**
     * 中文注释: onDestroyView 方法在 Fragment 视图销毁时调用。
     * 它负责注销 EventBus、保存文件树状态并清理绑定和文件树视图实例。
     * English annotation: The onDestroyView method is called when the Fragment's view is being destroyed.
     * It is responsible for unregistering EventBus, saving the file tree state, and nullifying binding and fileTreeView instances.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        saveTreeState()
        binding = null
        fileTreeView = null
    }

    /**
     * 中文注释: initBottomBar 方法初始化底部栏的 UI 元素，特别是“选择挂载目录”按钮的点击监听器。
     * English annotation: The initBottomBar method initializes UI elements in the bottom bar, specifically the click listener for the "Select Mounted Directory" button.
     */
    private fun initBottomBar() {
        binding?.selectMountDirectoryButton?.setOnClickListener {
            checkStoragePermission()
        }
    }

    /**
     * 中文注释: checkStoragePermission 方法检查应用程序是否拥有读取外部存储的权限。
     * 如果没有，它会请求该权限；否则，它会直接打开 DocumentsUI 进行目录选择。
     * English annotation: The checkStoragePermission method checks if the application has READ_EXTERNAL_STORAGE permission.
     * If not, it requests the permission; otherwise, it directly opens DocumentsUI for directory selection.
     */
    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_STORAGE_PERMISSION
            )
        } else {
            openDocumentsUISelection()
        }
    }

    /**
     * 中文注释: onRequestPermissionsResult 方法处理权限请求的结果。
     * 如果读取外部存储权限被授予，它会打开 DocumentsUI 进行目录选择。
     * English annotation: The onRequestPermissionsResult method handles the result of permission requests.
     * If READ_EXTERNAL_STORAGE permission is granted, it proceeds to open DocumentsUI for directory selection.
     * @param requestCode 中文注释: 请求的整数代码，在此处是 REQUEST_CODE_STORAGE_PERMISSION。 English annotation: The integer request code originally supplied, here it's REQUEST_CODE_STORAGE_PERMISSION.
     * @param permissions 中文注释: 请求的权限数组。 English annotation: The requested permissions array.
     * @param grantResults 中文注释: 相应权限的授予结果（PERMISSION_GRANTED 或 PERMISSION_DENIED）。 English annotation: The grant results for the corresponding permissions (PERMISSION_GRANTED or PERMISSION_DENIED).
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openDocumentsUISelection()
            }
        }
    }

    /**
     * 中文注释: openDocumentsUISelection 方法启动 DocumentsUI 以允许用户选择一个目录。
     * 它设置 Intent.ACTION_OPEN_DOCUMENT_TREE，并添加必要的权限标志以获取持久的 URI 访问权限。
     * English annotation: The openDocumentsUISelection method launches DocumentsUI to allow the user to select a directory.
     * It sets Intent.ACTION_OPEN_DOCUMENT_TREE and adds necessary flag for persistable URI permissions.
     */
    private fun openDocumentsUISelection() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQUEST_CODE_SELECT_DIRECTORY)
    }

    /**
     * 中文注释: onActivityResult 方法处理从 DocumentsUI 返回的结果。
     * 它获取用户选择的 URI，请求持久权限，保存挂载路径，并根据 URI 类型加载文件。
     * English annotation: The onActivityResult method handles the result returned from DocumentsUI.
     * It retrieves the URI selected by the user, requests persistable permissions, saves the mount path, and loads files based on the URI type.
     * @param requestCode 中文注释: 启动 Activity 时传入的请求码。 English annotation: The request code passed to startActivityForResult.
     * @param resultCode 中文注释: 子 Activity 返回的结果码（RESULT_OK, RESULT_CANCELED 等）。 English annotation: The result code returned by the child activity (RESULT_OK, RESULT_CANCELED, etc.).
     * @param data 中文注释: 包含结果数据的 Intent。 English annotation: The Intent containing the result data.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SELECT_DIRECTORY && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                persistUriPermission(uri)
                val path = uri.toString()
                saveMountPath(path)
                Log.d(TAG, "Selected URI: $uri")
                if (isMTDataProviderUri(uri)) {
                    Log.d(TAG, "Loading from MT Data Provider URI: $uri")
                    loadFilesFromMTProvider(uri)
                    updateMountStatus("MT Data: " + getDocumentDisplayName(uri))
                } else {
                    val filePath = uriToFilePath(uri)
                    if (!filePath.isNullOrEmpty()) {
                        // For SAF uris, we save the URI string itself, not the converted file path
                        saveMountPath(uri.toString())
                        Log.d(TAG, "Loading from file path: $filePath")
                        loadFilesFromDirectory(filePath)
                        updateMountStatus(filePath)
                    } else {
                        // Fallback for URIs that don't map to a direct file path
                        Log.d(TAG, "Falling back to MT Data Provider for URI: $uri")
                        loadFilesFromMTProvider(uri)
                        updateMountStatus("URI: " + getDocumentDisplayName(uri))
                    }
                }
            }
        }
    }
    
    /**
     * 中文注释: getDocumentDisplayName 方法通过 ContentResolver 查询给定 URI 的 COLUMN_DISPLAY_NAME。
     * 如果查询失败，则返回 URI 的最后一个路径段或“Unknown”。
     * English annotation: The getDocumentDisplayName method queries the COLUMN_DISPLAY_NAME for a given URI via ContentResolver.
     * If the query fails, it returns the last path segment of the URI or "Unknown".
     * @param uri 中文注释: 要查询的文档 URI。 English annotation: The URI of the document to query.
     * @return 中文注释: 文档的显示名称。 English annotation: The display name of the document.
     */
    private fun getDocumentDisplayName(uri: Uri): String {
        try {
            requireContext().contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) { // 捕获更具体的异常并打印日志
            Log.e(TAG, "Error getting document display name for URI: $uri", e)
        }
        return uri.lastPathSegment ?: "Unknown"
    }

    /**
     * 中文注释: persistUriPermission 方法请求并获取给定 URI 的持久读写权限。
     * 这样即使应用程序重启，也能保持对该 URI 的访问。
     * English annotation: The persistUriPermission method requests and takes persistable read and write permissions for the given URI.
     * This allows the application to retain access to the URI even after restarts.
     * @param uri 中文注释: 要获取持久权限的 URI。 English annotation: The URI for which to take persistable permissions.
     */
    private fun persistUriPermission(uri: Uri) {
        val contentResolver = requireContext().contentResolver
        // 确保同时获取读写权限
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Persistable URI permission taken for: $uri with flags: $flags")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to take persistable URI permission for: $uri", e)
        }
    }

    /**
     * 中文注释: uriToFilePath 方法尝试将给定的 URI 转换为文件系统路径。
     * 此函数主要用于旧版兼容性，现代访问应优先使用 Content URI。
     * English annotation: The uriToFilePath method attempts to convert a given URI to a file system path.
     * This function is primarily for legacy support, as modern access should prefer Content URIs.
     * @param uri 中文注释: 要转换的 URI。 English annotation: The URI to convert.
     * @return 中文注释: 对应的文件路径字符串，如果无法转换则为 null。 English annotation: The corresponding file path string, or null if it cannot be converted.
     */
    private fun uriToFilePath(uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(requireContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                if (id.startsWith("raw:")) {
                    return id.substring(4)
                }
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())
                return queryUriToPath(contentUri)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return contentUri?.let { queryUriToPath(it, selection, selectionArgs) }
            }
        }
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            return queryUriToPath(uri)
        }
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    /**
     * 中文注释: isMTDataProviderUri 方法检查给定的 URI 是否来自您的 MTDataFilesProvider。
     * 它通过检查 URI 的 Authority 是否以 ".MTDataFilesProvider" 结尾来判断。
     * English annotation: The isMTDataProviderUri method checks if the given URI originates from your MTDataFilesProvider.
     * It does this by checking if the URI's Authority ends with ".MTDataFilesProvider".
     * @param uri 中文注释: 要检查的 URI。 English annotation: The URI to check.
     * @return 中文注释: 如果 URI 来自 MTDataFilesProvider，则为 true；否则为 false。 English annotation: True if the URI is from MTDataFilesProvider, false otherwise.
     */
    private fun isMTDataProviderUri(uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        return authority.endsWith(".MTDataFilesProvider")
    }

    /**
     * 中文注释: queryUriToPath 方法通过 ContentResolver 查询给定 URI 的实际文件路径（通常是 DATA 列）。
     * English annotation: The queryUriToPath method queries the actual file path (typically the DATA column) for a given URI via ContentResolver.
     * @param uri 中文注释: 要查询的 URI。 English annotation: The URI to query.
     * @param selection 中文注释: 查询的选择子句。 English annotation: The selection clause for the query.
     * @param selectionArgs 中文注释: 查询的选择参数。 English annotation: The selection arguments for the query.
     * @return 中文注释: 对应的文件路径字符串，如果查询失败则为 null。 English annotation: The corresponding file path string, or null if the query fails.
     */
    private fun queryUriToPath(
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): String? {
        try {
            requireContext().contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                }
            }
        } catch (e: Exception) {
            // Ignore, this fails for many modern URIs
            Log.e(TAG, "Error querying URI to path: $uri", e)
        }
        return null
    }

    /**
     * 中文注释: isExternalStorageDocument 方法检查给定的 URI 是否是外部存储文档。
     * English annotation: The isExternalStorageDocument method checks if the given URI is an external storage document.
     * @param uri 中文注释: 要检查的 URI。 English annotation: The URI to check.
     * @return 中文注释: 如果是外部存储文档，则为 true；否则为 false。 English annotation: True if it's an external storage document, false otherwise.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean = "com.android.externalstorage.documents" == uri.authority
    /**
     * 中文注释: isDownloadsDocument 方法检查给定的 URI 是否是下载文档。
     * English annotation: The isDownloadsDocument method checks if the given URI is a downloads document.
     * @param uri 中文注释: 要检查的 URI。 English annotation: The URI to check.
     * @return 中文注释: 如果是下载文档，则为 true；否则为 false。 English annotation: True if it's a downloads document, false otherwise.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean = "com.android.providers.downloads.documents" == uri.authority
    /**
     * 中文注释: isMediaDocument 方法检查给定的 URI 是否是媒体文档。
     * English annotation: The isMediaDocument method checks if the given URI is a media document.
     * @param uri 中文注释: 要检查的 URI。 English annotation: True if it's a media document, false otherwise.
     * @return 中文注释: 如果是媒体文档，则为 true；否则为 false。 English annotation: The URI to check.
     */
    private fun isMediaDocument(uri: Uri): Boolean = "com.android.providers.media.documents" == uri.authority

    /**
     * 中文注释: saveMountPath 方法将挂载的目录路径保存到 SharedPreferences。
     * English annotation: The saveMountPath method saves the mounted directory path to SharedPreferences.
     * @param path 中文注释: 要保存的目录路径。 English annotation: The directory path to save.
     */
    private fun saveMountPath(path: String) {
        sharedPreferences.edit().putString(PREF_MOUNTED_DIR, path).apply()
        Log.d(TAG, "Mounted path saved: $path")
    }

    /**
     * 中文注释: getSavedMountPath 方法从 SharedPreferences 获取已保存的挂载目录路径。
     * English annotation: The getSavedMountPath method retrieves the saved mounted directory path from SharedPreferences.
     * @return 中文注释: 已保存的目录路径字符串，如果没有则为 null。 English annotation: The saved directory path string, or null if none is found.
     */
    private fun getSavedMountPath(): String? {
        val path = sharedPreferences.getString(PREF_MOUNTED_DIR, null)
        Log.d(TAG, "Retrieved mounted path: $path")
        return path
    }

    /**
     * 中文注释: updateMountStatus 方法更新 UI 上挂载目录的状态文本。
     * English annotation: The updateMountStatus method updates the mount status text on the UI.
     * @param path 中文注释: 要显示的挂载路径。 English annotation: The mounted path to display.
     */
    private fun updateMountStatus(path: String) {
        binding?.mountStatus?.apply {
            text = "Mounted: $path"
            visibility = View.VISIBLE
            Log.d(TAG, "Mount status updated: Mounted: $path")
        }
    }

    /**
     * 中文注释: saveTreeState 方法将当前文件树的展开状态保存到 ViewModel 中。
     * English annotation: The saveTreeState method saves the current expanded state of the file tree to the ViewModel.
     */
    fun saveTreeState() {
        viewModel.saveState(fileTreeView)
        Log.d(TAG, "Tree state saved.")
    }

    /**
     * 中文注释: onClick 方法处理文件树节点点击事件。
     * 如果点击的是目录，它会展开或折叠该目录；否则，它会分派 FileClickEvent。
     * English annotation: The onClick method handles file tree node click events.
     * If a directory is clicked, it expands or collapses it; otherwise, it dispatches a FileClickEvent.
     * @param node 中文注释: 被点击的 TreeNode。 English annotation: The clicked TreeNode.
     * @param p2 中文注释: 节点的值（通常是 File 对象）。 English annotation: The value of the node (typically a File object).
     */
    override fun onClick(node: TreeNode, p2: Any) {
        val file = p2 as File
        val isVirtual = isMountedMTFile(file)
        
        Log.d(TAG, "Node clicked: ${file.name}, isDirectory: ${file.isDirectory}, isVirtual: $isVirtual")

        if (!isVirtual && !file.exists()) {
            Log.w(TAG, "Clicked file does not exist: ${file.path}")
            return
        }

        if (file.isDirectory) { // For virtual files, this property is set during creation
            if (node.isExpanded) {
                Log.d(TAG, "Collapsing node: ${file.name}")
                collapseNode(node)
            } else {
                Log.d(TAG, "Expanding node: ${file.name}")
                setLoading(node)
                listNode(node) { expandNode(node) }
            }
        }
        val event = FileClickEvent(file)
        event.put(Context::class.java, requireContext())
        EventBus.getDefault().post(event)
        Log.d(TAG, "FileClickEvent posted for: ${file.name}")
    }
    
    /**
     * 中文注释: isMountedMTFile 方法检查给定文件是否是通过 Content URI 挂载的虚拟文件。
     * 这通过检查保存的挂载路径是否以 "content://" 开头来判断。
     * English annotation: The isMountedMTFile method checks if the given file is a virtual file mounted via a Content URI.
     * This is determined by checking if the saved mount path starts with "content://".
     * @param file 中文注释: 要检查的文件。 English annotation: The file to check.
     * @return 中文注释: 如果是虚拟挂载文件，则为 true；否则为 false。 English annotation: True if it's a virtual mounted file, false otherwise.
     */
    private fun isMountedMTFile(file: File): Boolean {
        val mountedPath = getSavedMountPath() ?: return false
        // A simple check to see if we're operating under a content URI
        return mountedPath.startsWith("content://")
    }

    /**
     * 中文注释: updateChevron 方法更新节点视图中展开/折叠指示图标（chevron）的状态。
     * English annotation: The updateChevron method updates the state of the expansion/collapse indicator icon (chevron) in the node's view.
     * @param node 中文注释: 要更新的 TreeNode。 English annotation: The TreeNode to update.
     */
    private fun updateChevron(node: TreeNode) {
        (node.viewHolder as? FileTreeViewHolder)?.updateChevron(node.isExpanded)
    }

    /**
     * 中文注释: expandNode 方法展开文件树中的给定节点。
     * 它会触发 UI 过渡动画并更新节点的展开状态和图标。
     * English annotation: The expandNode method expands the given node in the file tree.
     * It triggers UI transition animations and updates the node's expanded state and icon.
     * @param node 中文注释: 要展开的 TreeNode。 English annotation: The TreeNode to expand.
     * @param animate 中文注释: 是否使用动画展开节点。 English annotation: Whether to use animation when expanding the node.
     */
    private fun expandNode(node: TreeNode, animate: Boolean = true) {
        if (fileTreeView == null) return
        if (animate) {
            TransitionManager.beginDelayedTransition(binding!!.root, ChangeBounds())
        }
        fileTreeView!!.expandNode(node)
        updateChevron(node)
        Log.d(TAG, "Node expanded: ${(node.value as File).name}")
    }

    /**
     * 中文注释: collapseNode 方法折叠文件树中的给定节点。
     * 它会触发 UI 过渡动画并更新节点的折叠状态和图标。
     * English annotation: The collapseNode method collapses the given node in the file tree.
     * It triggers UI transition animations and updates the node's collapsed state and icon.
     * @param node 中文注释: 要折叠的 TreeNode。 English annotation: The TreeNode to collapse.
     * @param animate 中文注释: 是否使用动画折叠节点。 English annotation: Whether to use animation when collapsing the node.
     */
    private fun collapseNode(node: TreeNode, animate: Boolean = true) {
        if (fileTreeView == null) return
        if (animate) {
            TransitionManager.beginDelayedTransition(binding!!.root, ChangeBounds())
        }
        fileTreeView!!.collapseNode(node)
        updateChevron(node)
        Log.d(TAG, "Node collapsed: ${(node.value as File).name}")
    }

    /**
     * 中文注释: setLoading 方法在节点加载内容时显示加载指示器。
     * English annotation: The setLoading method shows a loading indicator when a node is loading its content.
     * @param node 中文注释: 要设置加载状态的 TreeNode。 English annotation: The TreeNode to set loading state for.
     */
    private fun setLoading(node: TreeNode) {
        (node.viewHolder as? FileTreeViewHolder)?.setLoading(true)
        Log.d(TAG, "Setting loading state for node: ${(node.value as File).name}")
    }

    /**
     * 中文注释: listNode 方法根据挂载类型（本地文件系统或 Content URI）列出节点的子文件。
     * English annotation: The listNode method lists child files of a node based on the mount type (local file system or Content URI).
     * @param node 中文注释: 要列出其子项的 TreeNode。 English annotation: The TreeNode whose children are to be listed.
     * @param whenDone 中文注释: 完成列出操作后执行的 Runnable。 English annotation: The Runnable to execute when the listing operation is complete.
     */
    private fun listNode(node: TreeNode, whenDone: Runnable) {
        Log.d(TAG, "listNode called for: ${(node.value as File).name}")
        ArrayList(node.children).forEach { child -> node.deleteChild(child) } // 清空现有子节点
        node.isExpanded = false
        
        val mountedPath = getSavedMountPath()
        val parentFile = node.value as File // 这里的 parentFile 可能是 VirtualFile 或普通 File

        // Decide listing strategy based on mount type
        if (mountedPath?.startsWith("content://") == true) {
            Log.d(TAG, "Using virtual node listing for content URI.")
            val rootUri = Uri.parse(mountedPath)
            listVirtualNode(rootUri, node, whenDone)
        } else {
            Log.d(TAG, "Using standard node listing for local file system.")
            listStandardNode(parentFile, node, whenDone)
        }
    }

    /**
     * 中文注释: listStandardNode 方法列出本地文件系统目录中的文件和文件夹。
     * 它在后台线程执行文件列表操作，并在完成后通知 UI。
     * English annotation: The listStandardNode method lists files and folders in a local file system directory.
     * It executes the file listing operation on a background thread and notifies the UI upon completion.
     * @param parentFile 中文注释: 要列出其内容的父 File 对象。 English annotation: The parent File object whose contents are to be listed.
     * @param parentNode 中文注释: 对应的 TreeNode。 English annotation: The corresponding TreeNode.
     * @param whenDone 中文注释: 列出操作完成后执行的 Runnable。 English annotation: The Runnable to execute when the listing operation is complete.
     */
    private fun listStandardNode(parentFile: File, parentNode: TreeNode, whenDone: Runnable) {
        TaskExecutor.executeAsync({
            val files = parentFile.listFiles()
            files?.let {
                listFilesForNode(it, parentNode)
            }
        }) {
            whenDone.run()
        }
    }
    
    /**
     * 中文注释: listVirtualNode 方法列出 ContentProvider 暴露的虚拟目录中的文件和文件夹。
     * 它通过 ContentResolver 查询子文档，并创建 VirtualFile 对象来表示它们。
     * English annotation: The listVirtualNode method lists files and folders in a virtual directory exposed by a ContentProvider.
     * It queries child documents via ContentResolver and creates VirtualFile objects to represent them.
     * @param rootUri 中文注释: 最初选择的根 URI (例如来自 DocumentsUI 的 treeUri)。 English annotation: The initial root URI selected (e.g., the treeUri from DocumentsUI).
     * @param parentNode 中文注释: 当前要列出其子项的 TreeNode。 English annotation: The current TreeNode whose children are to be listed.
     * @param whenDone 中文注释: 列出操作完成后执行的 Runnable。 English annotation: The Runnable to execute when the listing operation is complete.
     */
    private fun listVirtualNode(rootUri: Uri, parentNode: TreeNode, whenDone: Runnable) {
        TaskExecutor.executeAsync(Callable {
            val contentResolver = requireContext().contentResolver
            val currentVirtualFile = parentNode.value as VirtualFile
            val parentDocId = currentVirtualFile.documentId // 直接从 VirtualFile 获取 documentId
            
            Log.d(TAG, "listVirtualNode: Querying children for parentDocId: $parentDocId under rootUri: $rootUri")

            if (parentDocId != null) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentDocId)
                Log.d(TAG, "listVirtualNode: Children URI: $childrenUri")

                try {
                    contentResolver.query(childrenUri, DOCUMENT_PROJECTION_WITH_PATH, null, null, null)?.use { cursor ->
                        if (cursor.getColumnIndex(COLUMN_MT_PATH) == -1) {
                            Log.e(TAG, "COLUMN_MT_PATH not found in cursor for URI: $childrenUri. Check MTDataFilesProvider implementation.")
                            // 返回null，表示加载失败
                            return@Callable null
                        }
                        Log.d(TAG, "listVirtualNode: Found ${cursor.count} children.")
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                            val path = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MT_PATH))
                            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                            val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                            
                            Log.d(TAG, "  Child: DisplayName=${cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))}, Path=$path, DocId=$docId, Mime=$mimeType")

                            // 修改这里，将 documentId 也传入 VirtualFile
                            val file = createVirtualFile(path, isDirectory, docId)
                            val childNode = TreeNode(file)
                            childNode.viewHolder = FileTreeViewHolder(context)
                            parentNode.addChild(childNode, false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying children from MTDataFilesProvider for parentDocId: $parentDocId", e)
                    // 确保这里的异常被捕获并处理，避免崩溃或无限加载
                    return@Callable null
                }
            } else {
                Log.w(TAG, "listVirtualNode: Parent document ID is null for node: ${(parentNode.value as File).name}")
            }
            return@Callable parentNode // 返回父节点，以在 UI 线程中处理其子节点
        }) { resultNode ->
            if (resultNode == null) {
                Log.e(TAG, "Failed to load virtual node contents. Result node is null.")
            }
            whenDone.run()
        }
    }
    
    // Recursive helper to find a documentId for a given path. This can be slow.
    // ⚠️ 废弃此方法，应通过 VirtualFile 直接获取 documentId
    @Deprecated("Avoid using this for performance. Store documentId directly in VirtualFile.")
    private fun findDocumentIdRecursive(rootUri: Uri, parentDocId: String, targetPath: String, onFound: (String) -> Unit) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentDocId)
        requireContext().contentResolver.query(childrenUri, DOCUMENT_PROJECTION_WITH_PATH, null, null, null)?.use { cursor ->
            while(cursor.moveToNext()) {
                val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MT_PATH))
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                if (path == targetPath) {
                    onFound(docId)
                    return
                }
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR && targetPath.startsWith(path)) {
                    findDocumentIdRecursive(rootUri, docId, targetPath, onFound)
                }
            }
        }
    }

    /**
     * 中文注释: listFilesForNode 方法将文件数组添加到文件树的给定父节点。
     * 它会对文件进行排序，并为每个文件创建一个 TreeNode 和 FileTreeViewHolder。
     * English annotation: The listFilesForNode method adds an array of files to a given parent node in the file tree.
     * It sorts the files and creates a TreeNode and FileTreeViewHolder for each file.
     * @param files 中文注释: 要添加的文件数组。 English annotation: The array of files to add.
     * @param parent 中文注释: 要添加子节点的父 TreeNode。 English annotation: The parent TreeNode to which children will be added.
     */
    private fun listFilesForNode(files: Array<File>, parent: TreeNode) {
        Arrays.sort(files, SortFileName())
        Arrays.sort(files, SortFolder())
        for (file in files) {
            val node = TreeNode(file)
            node.viewHolder = FileTreeViewHolder(context) // 使用原始的 FileTreeViewHolder 构造函数
            parent.addChild(node, false)
        }
    }

    /**
     * 中文注释: onLongClick 方法处理文件树节点长按事件。
     * 它会分派 FileLongClickEvent。
     * English annotation: The onLongClick method handles file tree node long click events.
     * It dispatches a FileLongClickEvent.
     * @param node 中文注释: 被长按的 TreeNode。 English annotation: The TreeNode that was long clicked.
     * @param value 中文注释: 节点的值（通常是 File 对象）。 English annotation: The value of the node (typically a File object).
     * @return 中文注释: 如果事件被消费，则为 true。 English annotation: True if the event was consumed.
     */
    override fun onLongClick(node: TreeNode, value: Any): Boolean {
        val event = FileLongClickEvent((value as File))
        event.put(Context::class.java, requireContext())
        event.put(TreeNode::class.java, node)
        EventBus.getDefault().post(event)
        Log.d(TAG, "FileLongClickEvent posted for: ${(value as File).name}")
        return true
    }

    /**
     * 中文注释: onGetListFilesRequested 方法是一个 EventBus 订阅者，用于响应文件列表请求。
     * 如果 Fragment 可见且上下文可用，它会重新加载文件列表。
     * English annotation: The onGetListFilesRequested method is an EventBus subscriber that responds to file list requests.
     * If the Fragment is visible and context is available, it reloads the file list.
     * @param event 中文注释: ListProjectFilesRequestEvent 事件。 English annotation: The ListProjectFilesRequestEvent event.
     */
    @Subscribe(threadMode = MAIN)
    fun onGetListFilesRequested(event: ListProjectFilesRequestEvent?) {
        if (!isVisible || context == null) return
        Log.d(TAG, "Received ListProjectFilesRequestEvent, reloading files.")
        listProjectFiles()
    }

    /**
     * 中文注释: onGetExpandTreeNodeRequest 方法是一个 EventBus 订阅者，用于响应展开树节点的请求。
     * 如果 Fragment 可见且上下文可用，它会展开指定的节点。
     * English annotation: The onGetExpandTreeNodeRequest method is an EventBus subscriber that responds to requests to expand a tree node.
     * If the Fragment is visible and context is available, it expands the specified node.
     * @param event 中文注释: ExpandTreeNodeRequestEvent 事件。 English annotation: The ExpandTreeNodeRequestEvent event.
     */
    @Subscribe(threadMode = MAIN)
    fun onGetExpandTreeNodeRequest(event: ExpandTreeNodeRequestEvent) {
        if (!isVisible || context == null) return
        Log.d(TAG, "Received ExpandTreeNodeRequestEvent for node: ${(event.node.value as File).name}")
        expandNode(event.node)
    }

    /**
     * 中文注释: listProjectFiles 方法是加载文件树的入口点。
     * 它首先尝试从 SharedPreferences 加载上次挂载的路径。
     * 如果路径是 Content URI，则调用 loadFilesFromMTProvider；如果是本地文件系统路径，则调用 loadFilesFromDirectory。
     * 如果没有挂载路径，它会加载默认的项目目录。
     * English annotation: The listProjectFiles method is the entry point for loading the file tree.
     * It first attempts to load the last mounted path from SharedPreferences.
     * If the path is a Content URI, it calls loadFilesFromMTProvider; if it's a local file system path, it calls loadFilesFromDirectory.
     * If no mounted path is found, it loads the default project directory.
     */
    fun listProjectFiles() {
        if (binding == null) {
            Log.w(TAG, "listProjectFiles called when binding is null.")
            return
        }

        val mountedPath = getSavedMountPath()
        Log.d(TAG, "listProjectFiles: Current mounted path: $mountedPath")

        if (mountedPath != null) {
            val uri = Uri.parse(mountedPath)
            if (mountedPath.startsWith("content://")) {
                Log.d(TAG, "listProjectFiles: Identified as content URI.")
                loadFilesFromMTProvider(uri)
                updateMountStatus("MT Data: " + getDocumentDisplayName(uri))
                return
            } else if (File(mountedPath).exists()) {
                Log.d(TAG, "listProjectFiles: Identified as local file system path.")
                loadFilesFromDirectory(mountedPath)
                updateMountStatus(mountedPath)
                return
            }
        }
        
        Log.d(TAG, "listProjectFiles: No valid mounted path, loading default project directory.")
        val projectDirPath = IProjectManager.getInstance().projectDirPath
        loadFilesFromDirectory(projectDirPath)
        binding?.mountStatus?.visibility = View.GONE
    }


    /**
     * 中文注释: loadFilesFromDirectory 方法用于加载本地文件系统中的文件和文件夹，并构建 AndroidTreeView。
     * English annotation: The loadFilesFromDirectory method loads files and folders from a local file system and constructs the AndroidTreeView.
     * @param directoryPath 中文注释: 要加载的目录的路径。 English annotation: The path of the directory to load.
     */
    private fun loadFilesFromDirectory(directoryPath: String) {
        if (binding == null) return
        
        binding!!.horizontalCroll.removeAllViews()
        binding!!.horizontalCroll.visibility = View.GONE
        binding!!.loading.visibility = View.VISIBLE
        Log.d(TAG, "Starting loadFilesFromDirectory for: $directoryPath")

        val directory = File(directoryPath)
        val rootNode = TreeNode(File("")) // Virtual root, not visible
        rootNode.viewHolder = FileTreeViewHolder(requireContext())

        val projectRoot = TreeNode(directory)
        projectRoot.viewHolder = FileTreeViewHolder(context)
        rootNode.addChild(projectRoot, false)

        TaskExecutor.executeAsync(FileTreeCallable(context, projectRoot, directory)) {
            if (binding == null) {
                Log.w(TAG, "loadFilesFromDirectory callback, binding is null.")
                return@executeAsync
            }

            binding!!.loading.visibility = View.GONE
            binding!!.horizontalCroll.visibility = View.VISIBLE
            Log.d(TAG, "loadFilesFromDirectory completed, creating tree view.")

            val tree = createTreeView(rootNode)
            if (tree != null) {
                tree.setUseAutoToggle(false)
                tree.setDefaultNodeClickListener(this)
                tree.setDefaultNodeLongClickListener(this)
                val view = tree.view
                binding!!.horizontalCroll.addView(view)
                view.post { tryRestoreState(rootNode) }
                Log.d(TAG, "Tree view added to UI for local path.")
            } else {
                 Log.e(TAG, "Failed to create tree view, context is null.")
            }
        }
    }

    /**
     * 中文注释: VirtualFile 是一个特殊的 File 类，用于表示通过 DocumentsProvider 访问的虚拟文件或目录。
     * 它重写了 isDirectory() 和 isFile() 方法，以反映虚拟路径的实际类型，并且现在包含 `documentId`。
     * English annotation: VirtualFile is a special File class used to represent virtual files or directories accessed via DocumentsProvider.
     * It overrides isDirectory() and isFile() to reflect the actual type of the virtual path, and now includes `documentId`.
     * @property path 文件的路径。 English annotation: The path of the file.
     * @property isDir 指示文件是否为目录。 English annotation: Indicates whether the file is a directory.
     * @property documentId DocumentsProvider 提供的文档ID。 English annotation: The document ID provided by the DocumentsProvider.
     */
    private class VirtualFile(path: String, private val isDir: Boolean, val documentId: String) : File(path) {
        override fun isDirectory(): Boolean = isDir
        override fun isFile(): Boolean = !isDir
    }

    /**
     * 中文注释: createVirtualFile 方法创建 VirtualFile 实例，用于表示来自 DocumentsProvider 的文件或目录。
     * English annotation: The createVirtualFile method creates an instance of VirtualFile to represent a file or directory from a DocumentsProvider.
     * @param path 文件的路径。 English annotation: The path of the file.
     * @param isDirectory 指示文件是否为目录。 English annotation: Indicates whether the file is a directory.
     * @param documentId DocumentsProvider 提供的文档ID。 English annotation: The document ID provided by the DocumentsProvider.
     * @return 中文注释: 新创建的 VirtualFile 实例。 English annotation: The newly created VirtualFile instance.
     */
    private fun createVirtualFile(path: String, isDirectory: Boolean, documentId: String): File {
        return VirtualFile(path, isDirectory, documentId)
    }

    /**
     * 中文注释: loadFilesFromMTProvider 方法用于加载通过 MTDataFilesProvider 暴露的虚拟文件和文件夹，并构建 AndroidTreeView。
     * 它通过 ContentResolver 查询 DocumentsContract 构建的 URI。
     * English annotation: The loadFilesFromMTProvider method loads virtual files and folders exposed by MTDataFilesProvider and constructs the AndroidTreeView.
     * It queries URIs built with DocumentsContract via ContentResolver.
     * @param uri 中文注释: 从 DocumentsUI 选择的目录的根 URI。 English annotation: The root URI of the directory selected from DocumentsUI.
     */
    private fun loadFilesFromMTProvider(uri: Uri) {
        if (binding == null) return

        binding!!.horizontalCroll.removeAllViews()
        binding!!.horizontalCroll.visibility = View.GONE
        binding!!.loading.visibility = View.VISIBLE
        Log.d(TAG, "Starting loadFilesFromMTProvider for URI: $uri")

        TaskExecutor.executeAsync(Callable<TreeNode?> {
            val contentResolver = requireContext().contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(uri) // 这就是根目录的 documentId
            Log.d(TAG, "loadFilesFromMTProvider: treeDocId: $treeDocId")

            if (treeDocId == null) {
                Log.e(TAG, "Failed to get tree document ID from URI: $uri")
                return@Callable null
            }
            
            // 查询根目录本身的元数据，以便创建 projectRoot 节点
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
            var projectRoot: TreeNode? = null
            try {
                contentResolver.query(rootDocUri, DOCUMENT_PROJECTION_WITH_PATH, null, null, null)?.use {
                    if (it.getColumnIndex(COLUMN_MT_PATH) == -1) {
                        Log.e(TAG, "COLUMN_MT_PATH not found for rootDocUri: $rootDocUri. Check MTDataFilesProvider.")
                        return@use // 提前返回，避免崩溃
                    }
                    if (it.moveToFirst()) {
                        val path = it.getString(it.getColumnIndexOrThrow(COLUMN_MT_PATH))
                        val mimeType = it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                        val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        
                        // 创建包含 documentId 的 VirtualFile
                        val file = createVirtualFile(path, isDirectory, treeDocId)
                        projectRoot = TreeNode(file)
                        projectRoot?.viewHolder = FileTreeViewHolder(context)
                        Log.d(TAG, "loadFilesFromMTProvider: Created projectRoot for path: $path, docId: $treeDocId")
                    } else {
                        Log.w(TAG, "No data found for rootDocUri: $rootDocUri")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying root document for URI: $rootDocUri", e)
                return@Callable null
            }

            if (projectRoot == null) {
                Log.e(TAG, "projectRoot is null after querying root document. Aborting.")
                return@Callable null
            }

            // 查询根目录的子文件/文件夹
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, treeDocId)
            Log.d(TAG, "loadFilesFromMTProvider: Querying children for childrenUri: $childrenUri")

            try {
                contentResolver.query(childrenUri, DOCUMENT_PROJECTION_WITH_PATH, null, null, null)?.use { cursor ->
                    if (cursor.getColumnIndex(COLUMN_MT_PATH) == -1) {
                         Log.e(TAG, "COLUMN_MT_PATH not found in children cursor for URI: $childrenUri. Check MTDataFilesProvider implementation.")
                         return@use // 提前返回，避免崩溃
                    }
                    Log.d(TAG, "loadFilesFromMTProvider: Found ${cursor.count} children for root.")
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                        val path = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MT_PATH))
                        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                        val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        
                        Log.d(TAG, "  Child: DisplayName=${cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))}, Path=$path, DocId=$docId, Mime=$mimeType")

                        // 创建包含 documentId 的 VirtualFile
                        val file = createVirtualFile(path, isDirectory, docId)
                        val node = TreeNode(file)
                        node.viewHolder = FileTreeViewHolder(context)
                        projectRoot?.addChild(node, false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying children for childrenUri: $childrenUri", e)
                return@Callable null
            }

            val rootNode = TreeNode(File("")) // AndroidTreeView 的虚拟根节点
            rootNode.viewHolder = FileTreeViewHolder(requireContext())
            projectRoot?.let { rootNode.addChild(it) } // 将实际项目根节点添加到虚拟根节点下
            rootNode // 返回这个根节点
        }) { rootNode ->
            if (binding == null || rootNode == null) {
                binding?.loading?.visibility = View.GONE
                Log.e(TAG, "loadFilesFromMTProvider callback: Binding or rootNode is null. Aborting UI update.")
                return@executeAsync
            }

            binding!!.loading.visibility = View.GONE
            binding!!.horizontalCroll.visibility = View.VISIBLE
            Log.d(TAG, "loadFilesFromMTProvider completed, creating tree view for virtual path.")

            val tree = createTreeView(rootNode)
            if (tree != null) {
                tree.setUseAutoToggle(false)
                tree.setDefaultNodeClickListener(this)
                tree.setDefaultNodeLongClickListener(this)
                val view = tree.view
                binding!!.horizontalCroll.addView(view)
                view.post { tryRestoreState(rootNode) }
                Log.d(TAG, "Tree view added to UI for virtual path.")
            } else {
                Log.e(TAG, "Failed to create tree view, context is null after async task.")
            }
        }
    }

    /**
     * 中文注释: createTreeView 方法创建并返回一个 AndroidTreeView 实例。
     * English annotation: The createTreeView method creates and returns an AndroidTreeView instance.
     * @param node 中文注释: 树的根节点。 English annotation: The root node of the tree.
     * @return 中文注释: 新创建的 AndroidTreeView 实例，如果上下文为空则为 null。 English annotation: The newly created AndroidTreeView instance, or null if context is null.
     */
    private fun createTreeView(node: TreeNode): AndroidTreeView? {
        return if (context == null) {
            null
        } else AndroidTreeView(requireContext(), node, drawable.bg_ripple).also { fileTreeView = it }
    }

    /**
     * 中文注释: tryRestoreState 方法尝试恢复文件树的先前展开状态。
     * English annotation: The tryRestoreState method attempts to restore the previous expanded state of the file tree.
     * @param rootNode 中文注释: 树的根节点。 English annotation: The root node of the tree.
     * @param state 中文注释: 保存的树状态字符串，如果为 null 则从 viewModel 获取。 English annotation: The saved tree state string, or null to get from viewModel.
     */
    private fun tryRestoreState(rootNode: TreeNode, state: String? = viewModel.savedState) {
        if (!state.isNullOrEmpty() && fileTreeView != null) {
            fileTreeView!!.collapseAll()
            val openNodes = state.split(AndroidTreeView.NODES_PATH_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toHashSet() // Convert to HashSet
            restoreNodeState(rootNode, openNodes)
            Log.d(TAG, "Tree state restored from: $state")
        }

        if (rootNode.children.isNotEmpty()) {
            rootNode.children.firstOrNull()?.let { projectRoot -> expandNode(projectRoot, false) }
            Log.d(TAG, "Expanded first child of root node.")
        } else {
             Log.d(TAG, "Root node has no children to expand initially.")
        }
    }

    /**
     * 中文注释: restoreNodeState 方法递归地恢复文件树中节点的展开状态。
     * English annotation: The restoreNodeState method recursively restores the expanded state of nodes in the file tree.
     * @param root 中文注释: 当前处理的根节点。 English annotation: The current root node being processed.
     * @param openNodes 中文注释: 包含所有应展开的节点路径的集合。 English annotation: A set containing paths of all nodes that should be expanded.
     */
    private fun restoreNodeState(root: TreeNode, openNodes: Set<String>) {
        val children = root.children
        for (node in children) { // 简化循环，直接使用 for-each
            if (openNodes.contains(node.path)) {
                listNode(node) {
                    expandNode(node, false)
                    restoreNodeState(node, openNodes)
                }
            }
        }
    }

    /**
     * 中文注释: companion object 包含静态常量和方法。
     * English annotation: The companion object contains static constants and methods.
     */
    companion object {
        /**
         * 中文注释: 用于日志记录的 Fragment TAG。
         * English annotation: Fragment TAG for logging.
         */
        const val TAG = "editor.datafileTree"

        /**
         * 中文注释: 自定义文档列的名称，用于获取文件的绝对路径。
         * English annotation: The name of the custom document column for retrieving the absolute path of a file.
         */
        private const val COLUMN_MT_PATH = "mt_path"
        /**
         * 中文注释: 用于查询 DocumentsProvider 时请求的文档投影（列）。
         * English annotation: The document projection (columns) requested when querying DocumentsProvider.
         */
        private val DOCUMENT_PROJECTION_WITH_PATH = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            COLUMN_MT_PATH
        )

        /**
         * 中文注释: newInstance 方法创建并返回 DataFileTreeFragment 的新实例。
         * English annotation: The newInstance method creates and returns a new instance of DataFileTreeFragment.
         * @return 中文注释: DataFileTreeFragment 的新实例。 English annotation: A new instance of DataFileTreeFragment.
         */
        @JvmStatic
        fun newInstance(): DataFileTreeFragment = DataFileTreeFragment()
    }
}