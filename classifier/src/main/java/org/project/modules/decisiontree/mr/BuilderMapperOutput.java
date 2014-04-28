package org.project.modules.decisiontree.mr;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.Writable;
import org.project.modules.decisiontree.node.TreeNode;

public class BuilderMapperOutput implements Writable, Cloneable {
	
	private TreeNode treeNode = null;
	
	public BuilderMapperOutput() {
		
	}
	
	public BuilderMapperOutput(TreeNode treeNode) {
		this.treeNode = treeNode;
	}
	
	public TreeNode getTreeNode() {
		return treeNode;
	}

	@Override
	public void readFields(DataInput dataInput) throws IOException {
		boolean isReadTree = dataInput.readBoolean();
		System.out.println("isReadTree: " + isReadTree);
		if (isReadTree) {
			treeNode = TreeNode.read(dataInput);
		}
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		dataOutput.writeBoolean(null != treeNode);
		if (null != treeNode) {
			treeNode.write(dataOutput);
		}
	}

}