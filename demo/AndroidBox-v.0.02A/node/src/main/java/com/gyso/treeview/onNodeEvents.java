package com.gyso.treeview;

import com.gyso.treeview.model.NodeModel;

public interface onNodeEvents {
    boolean onNodeDrop(NodeModel<?> parent, NodeModel<?> child);
}
