package com.itsaky.androidide.fragments.output

import android.os.Bundle
import android.view.View
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentNonEditableEditorBinding
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.fragments.EmptyStateFragment
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE
import com.itsaky.androidide.utils.jetbrainsMono
import io.github.rosemoe.sora.lang.EmptyLanguage

abstract class NonEditableEditorFragment :
    EmptyStateFragment<FragmentNonEditableEditorBinding>(
        R.layout.fragment_non_editable_editor,
        FragmentNonEditableEditorBinding::bind
    ),
    ShareableOutputFragment {

    val editor: IDEEditor?
        get() = binding?.editor

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyStateViewModel.emptyMessage.value = createEmptyStateMessage()

        binding.editor.apply {
            isEditable = false
            // FIX #1: Provide a Float literal (0f) instead of an Int (0).
            dividerWidth = 0f
            setEditorLanguage(EmptyLanguage())
            isWordwrap = false
            isUndoEnabled = false
            setTypefaceLineNumber(jetbrainsMono())
            setTypefaceText(jetbrainsMono())
            
            // FIX #2: Provide a Float literal (12f) as the argument.
            setTextSize(12f) 

            colorScheme = SchemeAndroidIDE.newInstance(requireContext())
        }
    }

    private fun createEmptyStateMessage(): CharSequence? {
        return null
    }

    override fun getContent(): String {
        return editor?.text?.toString() ?: ""
    }

    override fun getFilename(): String = "build_output"

    override fun clearOutput() {
        editor?.let { editor ->
            val text = editor.text
            // This is a good and robust way to clear the text.
            text.delete(0, text.length)
            
            emptyStateViewModel.isEmpty.value = true
        }
    }
}