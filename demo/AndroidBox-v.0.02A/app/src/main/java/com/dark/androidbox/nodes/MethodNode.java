package com.dark.androidbox.nodes;


import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;

import com.dark.androidbox.databinding.MethodNodeBinding;

public class MethodNode  extends BaseNode {

    private MethodNodeBinding binding;

    public MethodNode(Context context) {
        super(context);
    }

    public MethodNode(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MethodNode(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MethodNode(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void init() {
        super.init();
        binding = MethodNodeBinding.inflate(LayoutInflater.from(getContext()), this, true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#b4defe")); // Set the background color
        background.setCornerRadius(16f); // Set the corner radius (in pixels)

        // Set the drawable as the background for the view
        setBackground(background);

    }

    @Override
    public void postInit() {
        super.postInit();
        binding.title.setVisibility(GONE);

//        setup(binding.choose);

    }


//    void setup(AppCompatSpinner spinner) {
//
//        MethodSelectAdapter adapter = new MethodSelectAdapter(getContext(), (List<MethodDeclaration>) data.getParentNode().value.data);
//
//
//
//        // Bind the adapter to the Spinner
//        spinner.setAdapter(adapter);
//
//        // Set a listener to handle item selection
//        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                String selectedItem = parent.getItemAtPosition(position).toString();
//                binding.title.setText(selectedItem);
//                MainActivity.treeViewStatic.getEditor().setWantEdit(false);
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {
//                // Handle the case where no item is selected
//            }
//        });
//    }
}
