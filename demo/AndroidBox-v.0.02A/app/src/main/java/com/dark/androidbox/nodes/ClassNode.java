package com.dark.androidbox.nodes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;

import com.dark.androidbox.databinding.ClassNodeBinding;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

public class ClassNode extends BaseNode {

    private ClassNodeBinding binding;
    private ClassOrInterfaceDeclaration declaration;

    public ClassNode(Context context) {
        super(context);
    }

    public ClassNode(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ClassNode(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClassNode(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void init() {
        super.init();
        binding = ClassNodeBinding.inflate(LayoutInflater.from(getContext()), this, true);
        setBackgroundColor(Color.WHITE);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void postInit() {
        super.postInit();
        if (data.value.data instanceof ClassOrInterfaceDeclaration) {
            declaration = (ClassOrInterfaceDeclaration) data.value.data;
            binding.ctx.setText(buildSpannableText());
        }else {
            binding.ctx.setText(data.value.title);
        }
    }

    private SpannableString buildSpannableText() {
        // Construct the text
        String typeText = "Type   : class\n\n";
        String implementationText = "Implementation : " + formatList(declaration.getImplementedTypes().toString()) + "\n\n";
        String extendedText = "Extended : " + formatList(declaration.getExtendedTypes().toString());

        String completeText = typeText + implementationText + extendedText;
        SpannableString spannableString = new SpannableString(completeText);

        // Apply spans (use the complete text instead of individual segments)
        applySpan(spannableString, completeText, "class", Color.parseColor("#B276FF"));
        applySpan(spannableString, completeText, formatList(declaration.getImplementedTypes().toString()), Color.parseColor("#E49D33"));
        applySpan(spannableString, completeText, formatList(declaration.getExtendedTypes().toString()), Color.parseColor("#E49D33"));

        return spannableString;
    }

    private String formatList(String list) {
        return list.replace("[", "").replace("]", "");
    }

    private void applySpan(SpannableString spannable, String fullText, String subText, int color) {
        int start = fullText.indexOf(subText);
        if (start != -1) {
            spannable.setSpan(new ForegroundColorSpan(color), start, start + subText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

}
