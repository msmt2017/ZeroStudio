package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R
import com.itsaky.androidide.fragments.sidebar.DataFileTreeFragment
import kotlin.reflect.KClass

/**
 * 侧边栏操作，用于显示数据文件树。
 * 此操作将打开 DataFileTreeFragment，允许用户选择一个自定义目录进行文件浏览。
 *
 *
 */
class DataFileTreeSidebarAction(context: Context, override val order: Int) : AbstractSidebarAction() {

  companion object {
    const val ID ="ide.editor.sidebar.dataFileTree"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = DataFileTreeFragment::class

  init {
    // 设置侧边栏项的标签，通常从资源文件中获取
    label = context.getString(R.string.msg_data_file_tree)
        // 设置侧边栏项的图标
    icon = ContextCompat.getDrawable(context, R.drawable.ic_data_catalog)
  }
}
