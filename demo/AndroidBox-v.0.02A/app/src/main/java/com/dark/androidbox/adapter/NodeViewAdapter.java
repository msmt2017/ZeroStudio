package com.dark.androidbox.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.dark.androidbox.databinding.NodeviewBinding;
import com.dark.androidbox.model.NodeData;
import com.dark.androidbox.types.NodeTypes;
import com.gyso.treeview.adapter.DrawInfo;
import com.gyso.treeview.adapter.TreeViewAdapter;
import com.gyso.treeview.adapter.TreeViewHolder;
import com.gyso.treeview.line.BaseLine;
import com.gyso.treeview.model.NodeModel;

import java.util.Locale;


public class NodeViewAdapter extends TreeViewAdapter<NodeData> {

    int id = 0;

    @Override
    public TreeViewHolder<NodeData> onCreateViewHolder(@NonNull ViewGroup viewGroup, NodeModel<NodeData> model) {
        return new TreeViewHolder<>(NodeviewBinding.inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false).getRoot(), model);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull TreeViewHolder<NodeData> holder) {
        View root = holder.getView();
        NodeModel<NodeData> data = holder.getNode();
        NodeviewBinding binding = NodeviewBinding.bind(root);
        data.value.nodeId = id++;
        binding.mainView.removeAllViews();
        binding.mainView.addView(data.value.node.getNode(data));
        String originalText = data.value.types.toString().toLowerCase(Locale.ROOT);
        String capitalizedText = originalText.substring(0, 1).toUpperCase(Locale.ROOT) + originalText.substring(1);
        if (data.value.types == NodeTypes.CLASS) binding.title.setText(data.value.title);
        else binding.title.setText(capitalizedText);
        binding.id.setText("Node ID: " + data.value.nodeId);
    }

    @Override
    public BaseLine onDrawLine(DrawInfo drawInfo) {
        return null;
    }


}
