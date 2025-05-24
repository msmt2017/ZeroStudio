package android.zero.studio.chatai.presentation.ui.setting

import android.content.Context
import android.widget.Toast

object PreferenceUtils {
    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}