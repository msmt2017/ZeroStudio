package android.zero.studio.chatai.presentation

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.termux.app.TermuxApplication

@HiltAndroidApp
open class GPTMobileApp : TermuxApplication() {
    
    @Inject
    @ApplicationContext
    lateinit var context: Context
}
