package modder.hub.dexeditor.activity

import android.content.Context

/*** by android_zero/零丶
 * 中文注释: SearchAction 类是一个简单的动作类，用于启动文件搜索对话框。
 * English annotation: The SearchAction class is a simple action class used to launch the file search dialog.
 */
class SearchAction(private val context: Context) {

    /**
     * 中文注释: 执行搜索动作，显示 SearchDialog。
     * English annotation: Executes the search action, displaying the SearchDialog.
     */
    fun execute() {
        SearchDialog(context).show()
    }
}
