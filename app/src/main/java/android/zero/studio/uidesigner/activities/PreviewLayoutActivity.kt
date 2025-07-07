package android.zero.studio.uidesigner.activities

import android.os.Bundle
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
        setContentView(binding.getRoot())
        @Suppress("DEPRECATION") val layoutFile = intent.extras!!.getParcelable<LayoutFile>(Constants.EXTRA_KEY_LAYOUT)
        val parser = XmlLayoutParser(this)
        parser.parseFromXml(layoutFile!!.read(), this)
        binding.getRoot().addView(parser.root)
    }
}
