package modder.hub.dexeditor.activity

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import modder.hub.dexeditor.R // 确保 R 文件已正确导入

/**
 * 中文注释: SearchAction 类封装了文件搜索对话框的显示逻辑。
 * 它提供了一个方法来启动 SearchDialog，以便用户可以执行文件和文件夹搜索。
 * English annotation: The SearchAction class encapsulates the logic for displaying the file search dialog.
 * It provides a method to launch the SearchDialog, allowing users to perform file and folder searches.
 */
class SearchAction {

    /**
     * 中文注释: 显示文件搜索对话框。
     * English annotation: Displays the file search dialog.
     * @param activity 中文注释: 用于显示对话框的 Activity 上下文。 English annotation: The Activity context to display the dialog.
     */
    fun showSearchDialog(activity: Activity) {
        val searchDialog = SearchDialog(activity)
        searchDialog.show()
    }

    /**
     * 中文注释: 为 Activity 的选项菜单添加搜索菜单项。
     * English annotation: Adds a search menu item to the Activity's options menu.
     * @param menu 中文注释: 要添加菜单项的 Menu 对象。 English annotation: The Menu object to which the item will be added.
     */
    fun addSearchMenuItem(menu: Menu) {
        // Add a new search menu item
        // 中文注释: 添加一个新的搜索菜单项
        // English annotation: Add a new search menu item
        menu.add(Menu.NONE, R.id.action_search_files, Menu.NONE, "查找文件 / Search Files")
            .setIcon(android.R.drawable.ic_menu_search) // Example icon
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) // Show as action if space allows
    }

    /**
     * 中文注释: 处理搜索菜单项的选择事件。
     * English annotation: Handles the selection event of the search menu item.
     * @param itemId 中文注释: 被选中菜单项的 ID。 English annotation: The ID of the selected menu item.
     * @param activity 中文注释: 当前的 Activity 实例。 English annotation: The current Activity instance.
     * @return Boolean 中文注释: 如果事件被处理则为 true，否则为 false。 English annotation: True if the event was handled, false otherwise.
     */
    fun handleOptionsItemSelected(itemId: Int, activity: Activity): Boolean {
        return if (itemId == R.id.action_search_files) {
            showSearchDialog(activity)
            true
        } else {
            false
        }
    }
}
