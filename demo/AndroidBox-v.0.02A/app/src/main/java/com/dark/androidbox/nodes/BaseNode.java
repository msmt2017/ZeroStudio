package com.dark.androidbox.nodes;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.dark.androidbox.MainActivity;
import com.dark.androidbox.databinding.BaseNodeBinding;
import com.dark.androidbox.model.NodeData;
import com.gyso.treeview.model.NodeModel;

public class BaseNode extends FrameLayout {

    NodeModel<NodeData> data;
    private BaseNodeBinding binding;

    public BaseNode(Context context) {
        super(context);
        init();
    }

    public BaseNode(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BaseNode(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public BaseNode(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void init() {
        binding = BaseNodeBinding.inflate(LayoutInflater.from(getContext()), this, true);
        setBackgroundColor(Color.WHITE);
    }

    public View getNode(NodeModel<NodeData> data) {
        this.data = data;
        postInit();
        return this;
    }

    public void postInit() {



    }

}
