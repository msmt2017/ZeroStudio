package android.zero.studio.uidesigner.activities

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.zero.studio.uidesigner.BaseActivity
import android.zero.studio.uidesigner.LayoutFile
import android.zero.studio.uidesigner.databinding.ActivityPreviewLayoutBinding
import android.zero.studio.uidesigner.tools.XmlLayoutParser
import android.zero.studio.uidesigner.utils.Constants

class PreviewLayoutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPreviewLayoutBinding.inflate(
            layoutInflater
        )
        setContentView(binding.root)
        @Suppress("DEPRECATION")
        val layoutFile = intent.getParcelableExtra<LayoutFile>(Constants.EXTRA_KEY_LAYOUT)
        if (layoutFile != null) {
            val parser = XmlLayoutParser(this)
            parser.parseFromXml(layoutFile.read(), this)
            parser.root?.let {
                // Ensure the root view fits the screen
                val container = binding.root as FrameLayout
                container.addView(it, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }
    }
}