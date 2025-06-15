package com.itsaky.androidide.fragments.sidebar

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.blankj.utilcode.util.SizeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.itsaky.androidide.adapters.viewholders.FileTreeViewHolder
import com.itsaky.androidide.databinding.LayoutEditorFileTreeBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.events.ExpandTreeNodeRequestEvent
import com.itsaky.androidide.events.ListProjectFilesRequestEvent
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R.drawable
import com.itsaky.androidide.tasks.TaskExecutor.executeAsync
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

class FileTreeFragment : BottomSheetDialogFragment(), TreeNodeClickListener,
  TreeNodeLongClickListener {

  private val TAG = "FileTreeFragment"
  
  private var binding: LayoutEditorFileTreeBinding? = null
  private var fileTreeView: AndroidTreeView? = null

  private val viewModel by viewModels<FileTreeViewModel>(ownerProducer = { requireActivity() })

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    Log.d(TAG, "onCreateView | Fragment created, container: ${container?.hashCode() ?: "null"}")
    
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
      Log.d(TAG, "Registered to EventBus | Current thread: ${Thread.currentThread().name}")
    }

    binding = LayoutEditorFileTreeBinding.inflate(inflater, container, false)
    binding?.root?.doOnApplyWindowInsets { view, insets, _, _ ->
      insets.getInsets(statusBars()).apply { view.updatePadding(top = top + SizeUtils.dp2px(8f)) }
    }
    Log.d(TAG, "Layout inflated | View hierarchy created")
    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Log.d(TAG, "onViewCreated | View ID: ${view.id}, savedInstanceState: ${if (savedInstanceState != null) "non-null" else "null"}")
    listProjectFiles()
  }

  override fun onDestroyView() {
    Log.d(TAG, "onDestroyView | Cleaning up fragment resources")
    super.onDestroyView()
    EventBus.getDefault().unregister(this)
    Log.d(TAG, "Unregistered from EventBus | Current state: fragment destroyed")

    saveTreeState()

    binding = null
    fileTreeView = null
  }

  fun saveTreeState() {
    Log.d(TAG, "saveTreeState | Saving tree state, view model: ${viewModel.hashCode()}")
    viewModel.saveState(fileTreeView)
  }

  override fun onClick(node: TreeNode, p2: Any) {
    val file = p2 as File
    Log.d(TAG, "onClick | File: ${file.absolutePath}, isDirectory: ${file.isDirectory}")
    
    if (!file.exists()) {
      Log.w(TAG, "onClick | File does not exist: ${file.absolutePath}")
      return
    }
    
    if (file.isDirectory) {
      if (node.isExpanded) {
        Log.d(TAG, "onClick | Collapsing directory: ${file.absolutePath}")
        collapseNode(node)
      } else {
        Log.d(TAG, "onClick | Expanding directory: ${file.absolutePath}, node: ${node.value.toString()}")
        setLoading(node)
        listNode(node) { expandNode(node) }
      }
    }
    
    val event = FileClickEvent(file)
    event.put(Context::class.java, requireContext())
    EventBus.getDefault().post(event)
    Log.d(TAG, "onClick | Posted FileClickEvent for: ${file.absolutePath}")
  }

  private fun updateChevron(node: TreeNode) {
    Log.d(TAG, "updateChevron | Node: ${node.value.toString()}, isExpanded: ${node.isExpanded}")
    if (node.viewHolder is FileTreeViewHolder) {
      (node.viewHolder as FileTreeViewHolder).updateChevron(node.isExpanded)
    }
  }

  private fun expandNode(node: TreeNode, animate: Boolean = true) {
    val file = node.value as File
    Log.d(TAG, "expandNode | File: ${file.absolutePath}, animate: $animate, isExpanded: ${node.isExpanded}")
    
    if (fileTreeView == null) {
      Log.w(TAG, "expandNode | fileTreeView is null, cannot expand: ${file.absolutePath}")
      return
    }
    
    if (animate) {
      TransitionManager.beginDelayedTransition(binding!!.root, ChangeBounds())
      Log.d(TAG, "expandNode | Started transition animation for: ${file.absolutePath}")
    }
    
    fileTreeView!!.expandNode(node)
    updateChevron(node)
  }

  private fun collapseNode(node: TreeNode, animate: Boolean = true) {
    val file = node.value as File
    Log.d(TAG, "collapseNode | File: ${file.absolutePath}, animate: $animate, isExpanded: ${node.isExpanded}")
    
    if (fileTreeView == null) {
      Log.w(TAG, "collapseNode | fileTreeView is null, cannot collapse: ${file.absolutePath}")
      return
    }
    
    if (animate) {
      TransitionManager.beginDelayedTransition(binding!!.root, ChangeBounds())
      Log.d(TAG, "collapseNode | Started transition animation for: ${file.absolutePath}")
    }
    
    fileTreeView!!.collapseNode(node)
    updateChevron(node)
  }

  private fun setLoading(node: TreeNode) {
    val file = node.value as File
    Log.d(TAG, "setLoading | File: ${file.absolutePath}, setting loading state")
    if (node.viewHolder is FileTreeViewHolder) {
      (node.viewHolder as FileTreeViewHolder).setLoading(true)
    }
  }

  private fun listNode(node: TreeNode, whenDone: Runnable) {
    val file = node.value as File
    Log.d(TAG, "listNode | File: ${file.absolutePath}, children count before: ${node.children.size}")
    
    node.children.clear()
    node.isExpanded = false
    
    executeAsync({
      Log.d(TAG, "listNode | Background thread: ${Thread.currentThread().name}, processing: ${file.absolutePath}")
      val files = node.value.listFiles()
      
      if (files == null) {
        Log.w(TAG, "listNode | files is null for: ${file.absolutePath}")
        return@executeAsync null
      }
      
      listFilesForNode(files, node)
      
      var temp = node
      while (temp.size() == 1) {
        temp = temp.childAt(0)
        if (!(temp.value is File)) {
          Log.w(TAG, "listNode | temp.value is not a File: ${temp.value.toString()}")
          break
        }
        
        val tempFile = temp.value as File
        if (!tempFile.isDirectory) {
          Log.d(TAG, "listNode | Breaking loop, node is not a directory: ${tempFile.absolutePath}")
          break
        }
        
        val tempFiles = tempFile.listFiles()
        if (tempFiles == null) {
          Log.w(TAG, "listNode | tempFiles is null for: ${tempFile.absolutePath}")
          continue
        }
        
        listFilesForNode(tempFiles, temp)
        temp.isExpanded = true
        Log.d(TAG, "listNode | Expanded node: ${tempFile.absolutePath}")
      }
      
      null
    }) {
      Log.d(TAG, "listNode | Background task completed, file: ${file.absolutePath}, running callback")
      whenDone.run()
    }
  }

  private fun listFilesForNode(files: Array<File>, parent: TreeNode) {
    val parentFile = parent.value as File
    Log.d(TAG, "listFilesForNode | Parent: ${parentFile.absolutePath}, files count: ${files.size}")
    
    Arrays.sort(files, SortFileName())
    Arrays.sort(files, SortFolder())
    
    for (file in files) {
      val node = TreeNode(file)
      node.viewHolder = FileTreeViewHolder(context)
      parent.addChild(node, false)
      Log.d(TAG, "listFilesForNode | Added child: ${file.absolutePath} to parent: ${parentFile.absolutePath}")
    }
  }

  override fun onLongClick(node: TreeNode, value: Any): Boolean {
    val file = value as File
    Log.d(TAG, "onLongClick | File: ${file.absolutePath}, node: ${node.value.toString()}")
    
    val event = FileLongClickEvent(file)
    event.put(Context::class.java, requireContext())
    event.put(TreeNode::class.java, node)
    EventBus.getDefault().post(event)
    
    Log.d(TAG, "onLongClick | Posted FileLongClickEvent for: ${file.absolutePath}")
    return true
  }

  @Suppress("unused", "UNUSED_PARAMETER")
  @Subscribe(threadMode = MAIN)
  fun onGetListFilesRequested(event: ListProjectFilesRequestEvent?) {
    Log.d(TAG, "onGetListFilesRequested | Event received, fragment visible: $isVisible, context: ${context != null}")
    
    if (!isVisible || context == null) {
      Log.w(TAG, "onGetListFilesRequested | Fragment not visible or context is null, skipping")
      return
    }
    
    listProjectFiles()
  }

  @Suppress("unused")
  @Subscribe(threadMode = MAIN)
  fun onGetExpandTreeNodeRequest(event: ExpandTreeNodeRequestEvent) {
    if (event.node.value !is File) {
      Log.w(TAG, "onGetExpandTreeNodeRequest | Node value is not a File, cannot expand")
      return
    }
    
    val file = event.node.value as File
    Log.d(TAG, "onGetExpandTreeNodeRequest | Event received for: ${file.absolutePath}, fragment visible: $isVisible")
    
    if (!isVisible || context == null) {
      Log.w(TAG, "onGetExpandTreeNodeRequest | Fragment not visible or context is null, skipping expand")
      return
    }
    
    expandNode(event.node)
  }

  fun listProjectFiles() {
    Log.d(TAG, "listProjectFiles | Starting to list project files")
    
    if (binding == null) {
      Log.w(TAG, "listProjectFiles | Binding is null, fragment destroyed")
      return
    }
    
    val projectDirPath = IProjectManager.getInstance().projectDirPath
    Log.d(TAG, "listProjectFiles | Project directory: $projectDirPath")
    
    val projectDir = File(projectDirPath)
    val rootNode = TreeNode(File(""))
    rootNode.viewHolder = FileTreeViewHolder(requireContext())
    
    val projectRoot = TreeNode.root(projectDir)
    projectRoot.viewHolder = FileTreeViewHolder(context)
    rootNode.addChild(projectRoot, false)
    Log.d(TAG, "listProjectFiles | Created project root node: ${projectDir.absolutePath}")
    
    binding!!.horizontalCroll.visibility = View.GONE
    binding!!.horizontalCroll.visibility = View.VISIBLE
    binding!!.loading.visibility = View.VISIBLE
    Log.d(TAG, "listProjectFiles | Showing loading view, preparing to load file tree")
    
    executeAsync(FileTreeCallable(context, projectRoot, projectDir)) {
      Log.d(TAG, "listProjectFiles | FileTreeCallable completed in background thread: ${Thread.currentThread().name}")
      
      if (binding == null) {
        Log.w(TAG, "listProjectFiles | Binding is null, fragment destroyed")
        return@executeAsync
      }
      
      binding!!.horizontalCroll.visibility = View.VISIBLE
      binding!!.loading.visibility = View.GONE
      Log.d(TAG, "listProjectFiles | Hiding loading view, showing file tree")
      
      val tree = createTreeView(rootNode)
      if (tree != null) {
        tree.setUseAutoToggle(false)
        tree.setDefaultNodeClickListener(this@FileTreeFragment)
        tree.setDefaultNodeLongClickListener(this@FileTreeFragment)
        Log.d(TAG, "listProjectFiles | Configured tree view with click listeners")
        
        binding!!.horizontalCroll.removeAllViews()
        val view = tree.view
        binding!!.horizontalCroll.addView(view)
        Log.d(TAG, "listProjectFiles | Added tree view to container, view ID: ${view.id}")
        
        view.post { tryRestoreState(rootNode) }
      }
    }
  }

  private fun createTreeView(node: TreeNode): AndroidTreeView? {
    Log.d(TAG, "createTreeView | Creating tree view for node: ${node.value.toString()}")
    
    return if (context == null) {
      Log.w(TAG, "createTreeView | Context is null, cannot create tree view")
      null
    } else AndroidTreeView(context, node, drawable.bg_ripple).also { fileTreeView = it }
  }

  private fun tryRestoreState(rootNode: TreeNode, state: String? = viewModel.savedState) {
    Log.d(TAG, "tryRestoreState | Attempting to restore state, state length: ${if (state != null) state.length else 0}")
    
    if (!TextUtils.isEmpty(state) && fileTreeView != null) {
      Log.d(TAG, "tryRestoreState | Restoring state from saved data, nodes count: ${state.split(AndroidTreeView.NODES_PATH_SEPARATOR).size}")
      fileTreeView!!.collapseAll()
      
      val openNodes = state!!.split(AndroidTreeView.NODES_PATH_SEPARATOR.toRegex())
        .dropLastWhile { it.isEmpty() }
        .toHashSet()
      restoreNodeState(rootNode, openNodes)
    }
    
    if (rootNode.children.isNotEmpty()) {
      rootNode.childAt(0)?.let { projectRoot ->
        Log.d(TAG, "tryRestoreState | Expanding project root node by default: ${(projectRoot.value as File).absolutePath}")
        expandNode(projectRoot, false)
      }
    }
  }

  private fun restoreNodeState(root: TreeNode, openNodes: Set<String>) {
    if (root.value !is File) {
      Log.w(TAG, "restoreNodeState | Root node value is not a File: ${root.value.toString()}")
      return
    }
    
    val rootFile = root.value as File
    Log.d(TAG, "restoreNodeState | Restoring state for root: ${rootFile.absolutePath}, open nodes count: ${openNodes.size}")
    
    val children = root.children
    var i = 0
    val childrenSize = children.size
    
    while (i < childrenSize) {
      val node = children[i]
      if (node.value !is File) {
        Log.w(TAG, "restoreNodeState | Node value is not a File: ${node.value.toString()}, skipping")
        i++
        continue
      }
      
      val nodeFile = node.value as File
      if (openNodes.contains(node.path)) {
        Log.d(TAG, "restoreNodeState | Node should be open: ${node.path}, file: ${nodeFile.absolutePath}")
        
        listNode(node) {
          expandNode(node, false)
          restoreNodeState(node, openNodes)
        }
      }
      i++
    }
  }

  companion object {
    const val TAG = "editor.fileTree"

    @JvmStatic
    fun newInstance(): FileTreeFragment {
      Log.d(TAG, "newInstance | Creating new fragment instance")
      return FileTreeFragment()
    }
  }
}
