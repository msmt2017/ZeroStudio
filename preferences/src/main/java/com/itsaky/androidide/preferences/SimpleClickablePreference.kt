package com.itsaky.androidide.preferences

import androidx.preference.Preference
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.IgnoredOnParcel // 导入 IgnoredOnParcel

/**
 * A simple preference which is expected to be clickable only.
 *
 * @author Akash Yadav
 */
@Parcelize
class SimpleClickablePreference
@JvmOverloads
constructor(
  override val key: String,
  override val title: Int,
  override val summary: Int? = null,
  override val icon: Int? = null,
  @IgnoredOnParcel // <-- 添加此行，忽略此属性的序列化
  private val onClick: ((Preference) -> Boolean)? = { false }
) : SimplePreference() {

  override fun onPreferenceClick(preference: Preference): Boolean {
    // 确保在运行时，onClick 属性是可用的
    // 如果此 Preference 被 Parcelize 序列化后又反序列化，onClick 会恢复为默认值 { false }
    // 或者需要在使用前重新设置，但这通常不是问题，因为PreferenceActivity会重新创建Preference树
    return onClick?.let { it(preference) } ?: false
  }
}
