package com.dark.androidbox.model;

import com.dark.androidbox.nodes.BaseNode;
import com.dark.androidbox.types.NodeTypes;
import com.github.javaparser.ast.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NodeData {


    public String title;
    public String code;
    public NodeTypes types;
    public BaseNode node;
    public Node data;
    public int nodeId;


    public NodeData(String title, String code, NodeTypes types, BaseNode node, Node data) {
        this.title = title;
        this.code = code;
        this.types = types;
        this.node = node;
        this.data = data;
    }


}
