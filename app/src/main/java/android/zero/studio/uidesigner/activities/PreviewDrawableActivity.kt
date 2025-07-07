package android.zero.studio.uidesigner.activities

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.ActionBar
import android.zero.studio.uidesigner.BaseActivity
import android.zero.studio.uidesigner.R
import android.zero.studio.uidesigner.databinding.ActivityPreviewDrawableBinding
import android.zero.studio.uidesigner.views.AlphaPatternDrawable

class PreviewDrawableActivity : BaseActivity() {
    private lateinit var binding: ActivityPreviewDrawableBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewDrawableBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())

        setSupportActionBar(binding.topAppBar)
        supportActionBar!!.setTitle(R.string.preview_drawable)

        binding.topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.background.setImageDrawable(AlphaPatternDrawable(24))

        onLoad(binding.mainImage, supportActionBar)
    }

    companion object {
        @JvmStatic
        var onLoad: (ImageView, ActionBar?) -> Unit = { _, _ -> }
    }
}
