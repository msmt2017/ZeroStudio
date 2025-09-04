package com.itsaky.androidide.fragments.sidebar.datatree; // 或 com.itsaky.androidide.adapters.viewholders;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.transition.ChangeImageTransform;
import androidx.transition.TransitionManager;

import com.blankj.utilcode.util.ImageUtils;
import com.blankj.utilcode.util.SizeUtils;
import com.itsaky.androidide.databinding.LayoutFiletreeItemBinding;
import com.itsaky.androidide.models.FileExtension;
import com.itsaky.androidide.resources.R;
import com.unnamed.b.atv.model.TreeNode;

import java.io.File;

/**
 * 中文注释: FileTreeViewHolder 是一个自定义的 ViewHolder，用于在 AndroidTreeView 中显示文件或目录节点。
 * 它负责根据文件类型设置图标、显示名称，并根据节点的层级应用适当的内边距。
 * English annotation: FileTreeViewHolder is a custom ViewHolder for displaying file or directory nodes in an AndroidTreeView.
 * It is responsible for setting icons, display names based on file type, and applying appropriate padding based on the node's level.
 */
public class FileTreeViewHolder extends TreeNode.BaseNodeViewHolder<File> {

  /**
   * 中文注释: 视图绑定实例，用于访问布局文件中的 UI 元素。
   * English annotation: View binding instance to access UI elements in the layout file.
   */
  private LayoutFiletreeItemBinding binding;

  /**
   * 中文注释: FileTreeViewHolder 的构造函数。
   * English annotation: Constructor for FileTreeViewHolder.
   * @param context 中文注释: 上下文对象，用于视图膨胀和资源访问。 English annotation: The context object, used for inflating views and accessing resources.
   */
  public FileTreeViewHolder(Context context) {
    super(context);
  }

  /**
   * 中文注释: createNodeView 方法用于创建和配置每个文件树节点的视图。
   * 它膨胀布局，设置文件名、图标，并根据文件是目录还是文件来显示或隐藏折叠/展开指示器。
   * English annotation: The createNodeView method is used to create and configure the view for each file tree node.
   * It inflates the layout, sets the file name, icon, and shows or hides the collapse/expand indicator based on whether the file is a directory or a file.
   * @param node 中文注释: 对应此视图的 TreeNode。 English annotation: The TreeNode corresponding to this view.
   * @param file 中文注释: 节点的值，一个 File 对象（可以是实际文件或 VirtualFile）。 English annotation: The value of the node, a File object (can be a real File or VirtualFile).
   * @return 中文注释: 配置好的节点视图。 English annotation: The configured node view.
   */
  @Override
  public View createNodeView(TreeNode node, File file) {
    this.binding = LayoutFiletreeItemBinding.inflate(LayoutInflater.from(context));

    final var dp15 = SizeUtils.dp2px(15);
    final var icon = getIconForFile(file);
    final var chevron = binding.filetreeChevron;
    binding.filetreeName.setText(file.getName());
    binding.filetreeIcon.setImageResource(icon);

    final var root = applyPadding(node, binding, dp15);

    if (file.isDirectory()) {
      chevron.setVisibility(View.VISIBLE);
      updateChevronIcon(node.isExpanded());
    } else {
      chevron.setVisibility(View.INVISIBLE);
    }

    return root;
  }

  /**
   * 中文注释: updateChevronIcon 方法根据节点的展开状态更新折叠/展开指示器（chevron）的图标。
   * English annotation: The updateChevronIcon method updates the icon of the collapse/expand indicator (chevron) based on the node's expanded state.
   * @param expanded 中文注释: 如果节点已展开则为 true，否则为 false。 English annotation: True if the node is expanded, false otherwise.
   */
  private void updateChevronIcon(boolean expanded) {
    final int chevronIcon;
    if (expanded) {
      chevronIcon = R.drawable.ic_chevron_down;
    } else {
      chevronIcon = R.drawable.ic_chevron_right;
    }

    TransitionManager.beginDelayedTransition(binding.getRoot(), new ChangeImageTransform());
    binding.filetreeChevron.setImageResource(chevronIcon);
  }

  /**
   * 中文注释: applyPadding 方法根据节点的层级为根视图应用左侧内边距，以创建树状结构缩进效果。
   * English annotation: The applyPadding method applies left padding to the root view based on the node's level to create a tree-like indentation effect.
   * @param node 中文注释: 要应用内边距的 TreeNode。 English annotation: The TreeNode to apply padding to.
   * @param binding 中文注释: 视图绑定实例，用于访问根视图。 English annotation: The view binding instance to access the root view.
   * @param padding 中文注释: 每个层级要增加的内边距单位（dp）。 English annotation: The padding unit (in dp) to be added for each level.
   * @return 中文注释: 带有更新内边距的根 LinearLayout。 English annotation: The root LinearLayout with updated padding.
   */
  protected LinearLayout applyPadding(
      final TreeNode node, final LayoutFiletreeItemBinding binding, final int padding) {
    final var root = binding.getRoot();
    root.setPaddingRelative(
        root.getPaddingLeft() + (padding * (node.getLevel() - 1)),
        root.getPaddingTop(),
        root.getPaddingRight(),
        root.getPaddingBottom());
    return root;
  }

  /**
   * 中文注释: getIconForFile 方法根据文件的类型返回相应的图标资源 ID。
   * 它支持目录、图片、Gradle 脚本文件以及通过 FileExtension.Factory 定义的其他文件类型。
   * English annotation: The getIconForFile method returns the appropriate icon resource ID based on the file's type.
   * It supports directories, image files, Gradle script files, and other file types defined via FileExtension.Factory.
   * @param file 中文注释: 要获取图标的 File 对象。 English annotation: The File object for which to get the icon.
   * @return 中文注释: 图标的资源 ID。 English annotation: The resource ID of the icon.
   */
  protected int getIconForFile(final File file) {

    if (file.isDirectory()) {
      return R.drawable.ic_folder;
    }

    if (ImageUtils.isImage(file)) {
      return R.drawable.ic_file_image;
    }

    if ("gradlew".equals(file.getName()) || "gradlew.bat".equals(file.getName())) {
      return R.drawable.ic_terminal;
    }

    return FileExtension.Factory.forFile(file).getIcon();
  }

  /**
   * 中文注释: updateChevron 方法更新节点的加载状态，并刷新折叠/展开指示器图标。
   * English annotation: The updateChevron method updates the loading state of the node and refreshes the collapse/expand indicator icon.
   * @param expanded 中文注释: 如果节点已展开，则为 true。 English annotation: True if the node is expanded.
   */
  public void updateChevron(boolean expanded) {
    setLoading(false);
    updateChevronIcon(expanded);
  }

  /**
   * 中文注释: setLoading 方法控制加载指示器（chevronLoadingSwitcher）的显示状态。
   * English annotation: The setLoading method controls the display state of the loading indicator (chevronLoadingSwitcher).
   * @param loading 中文注释: 如果正在加载，则为 true；否则为 false。 English annotation: True if loading is in progress, false otherwise.
   */
  public void setLoading(boolean loading) {
    final int viewIndex;
    if (loading) {
      viewIndex = 1;
    } else {
      viewIndex = 0;
    }

    binding.chevronLoadingSwitcher.setDisplayedChild(viewIndex);
  }

    // ⭐ 重要：在 FileTreeViewHolder 内部添加 VirtualFile 定义，以确保它能在该类中被识别
    // This class needs to be accessible where TreeNode values are created and cast.
    // Making it public static in FileTreeViewHolder or in a separate file if it needs broader access.
    /**
     * 中文注释: VirtualFile 是一个特殊的 File 类，用于表示通过 DocumentsProvider 访问的虚拟文件或目录。
     * 它重写了 isDirectory() 和 isFile() 方法，以反映虚拟路径的实际类型，并且包含 `documentId`。
     * English annotation: VirtualFile is a special File class used to represent virtual files or directories accessed via DocumentsProvider.
     * It overrides isDirectory() and isFile() to reflect the actual type of the virtual path, and now includes `documentId`.
     * @property path 文件的路径。 English annotation: The path of the file.
     * @property isDir 指示文件是否为目录。 English annotation: Indicates whether the file is a directory.
     * @property documentId DocumentsProvider 提供的文档ID。 English annotation: The document ID provided by the DocumentsProvider.
     */
    public static class VirtualFile extends File {
        private final boolean isDir;
        public final String documentId; 

        public VirtualFile(String path, boolean isDir, String documentId) {
            super(path);
            this.isDir = isDir;
            this.documentId = documentId;
        }

        @Override
        public boolean isDirectory() {
            return isDir;
        }

        @Override
        public boolean isFile() {
            return !isDir;
        }
    }
}