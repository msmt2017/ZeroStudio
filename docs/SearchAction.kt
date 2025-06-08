package com.itsaky.androidide.actions.etc

import android.content.Context
import com.itsaky.androidide.actions.ActionItem
import modder.hub.dexeditor.activity.SearchDialog // Import the SearchDialog

/**
 * 中文注释: SearchAction 类是一个用于启动文件搜索对话框的动作。
 * 它继承自 ActionItem，并注册到 ActionsRegistry 中，以便在编辑器活动中通过菜单或工具栏触发。
 * English annotation: The SearchAction class is an action used to launch the file search dialog.
 * It extends ActionItem and is registered with the ActionsRegistry to be triggered via menus or toolbars in the editor activity.
 */
class SearchAction(context: Context, order: Int) : ActionItem(
    id = "action_search_files", // 动作的唯一ID / Unique ID for the action
    title = "查找文件 / Search Files", // 动作的显示名称 / Display name for the action
    order = order // 动作在列表中的排序顺序 / Order of the action in the list
) {

    /**
     * 中文注释: 动作所属的位置（例如：工具栏、文件树等）。
     * English annotation: The location where the action belongs (e.g., toolbar, file tree, etc.).
     */
    override val location: ActionItem.Location = ActionItem.Location.EDITOR_TOOLBAR // 或者 EDITOR_FILE_TREE / Or EDITOR_FILE_TREE

    /**
     * 中文注释: 执行此动作的具体逻辑。
     * 在这里，它会创建一个 SearchDialog 实例并显示它。
     * English annotation: The specific logic to perform this action.
     * Here, it creates and shows an instance of SearchDialog.
     * @param context 中文注释: 上下文对象，用于显示对话框。 English annotation: The Context object, used to display the dialog.
     */
    override fun performAction(context: Context) {
        SearchDialog(context).show()
    }

    /**
     * 中文注释: 判断此动作是否应该启用。
     * English annotation: Determines if this action should be enabled.
     * @param context 中文注释: 上下文对象。 English annotation: The Context object.
     * @return Boolean 中文注释: 如果动作应该启用，则为 true；否则为 false。 English annotation: True if the action should be enabled; false otherwise.
     */
    override fun isEnabled(context: Context): Boolean {
        // 中文注释: 可以在这里添加逻辑来动态启用/禁用动作，例如检查权限
        // English annotation: Add logic here to dynamically enable/disable the action, e.g., check permissions
        return true
    }

    /**
     * 中文注释: 判断此动作是否应该可见。
     * English annotation: Determines if this action should be visible.
     * @param context 中文注释: 上下文对象。 English annotation: The Context object.
     * @return Boolean 中文注释: 如果动作应该可见，则为 true；否则为 false。 English annotation: True if the action should be visible; false otherwise.
     */
    override fun isVisible(context: Context): Boolean {
        // 中文注释: 可以在这里添加逻辑来动态显示/隐藏动作
        // English annotation: Add logic here to dynamically show/hide the action
        return true
    }
}
