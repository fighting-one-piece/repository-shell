package org.project.modules.classifier.decisiontree.mr.dt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.ReflectionUtils;
import org.project.modules.classifier.decisiontree.data.Data;
import org.project.modules.classifier.decisiontree.data.DataHandler;
import org.project.modules.classifier.decisiontree.data.DataLoader;
import org.project.modules.classifier.decisiontree.data.DataSplit;
import org.project.modules.classifier.decisiontree.data.DataSplitItem;
import org.project.modules.classifier.decisiontree.mr.AbstractJob;
import org.project.modules.classifier.decisiontree.mr.writable.AttributeGainWritable;
import org.project.modules.classifier.decisiontree.node.TreeNode;
import org.project.modules.classifier.decisiontree.node.TreeNodeHelper;
import org.project.utils.FileUtils;
import org.project.utils.HDFSUtils;
import org.project.utils.ShowUtils;

public class DecisionTreeC45Job extends AbstractJob {
	
	private Data data = null;
	
	/**
	 * 对数据集做预处理
	 * @param input
	 * @return
	 */
	public String prepare(Path input) {
		String hdfsPath = null;
		try {
			FileSystem fs = input.getFileSystem(conf);
			Path[] hdfsPaths = HDFSUtils.getPathFiles(fs, input);
			FSDataInputStream fsInputStream = fs.open(hdfsPaths[0]);
			data = DataLoader.load(fsInputStream, true);
			DataHandler.fill(data, 0);
			String path = FileUtils.obtainRandomTxtPath();
			DataHandler.writeData(path, data);
			System.out.println(path);
			String name = path.substring(path.lastIndexOf(File.separator) + 1);
			hdfsPath = HDFSUtils.HDFS_TEMP_DATA_URL + name;
			HDFSUtils.copyFromLocalFile(conf, path, hdfsPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return hdfsPath;
	}
	
	/**
	 * 选择最佳属性
	 * @param output
	 * @return
	 */
	public AttributeGainWritable chooseBestAttribute(String output) {
		AttributeGainWritable maxAttribute = null;
		Path path = new Path(output);
		try {
			FileSystem fs = path.getFileSystem(conf);
			Path[] paths = HDFSUtils.getPathFiles(fs, path);
			ShowUtils.print(paths);
			double maxGainRatio = 0.0;
			SequenceFile.Reader reader = null;
			for (Path p : paths) {
				reader = new SequenceFile.Reader(fs, p, conf);
				Text key = (Text) ReflectionUtils.newInstance(
						reader.getKeyClass(), conf);
				AttributeGainWritable value = new AttributeGainWritable();
				while (reader.next(key, value)) {
					double gainRatio = value.getGainRatio();
					if (gainRatio >= maxGainRatio) {
						maxGainRatio = gainRatio;
						maxAttribute = value;
					}
					value = new AttributeGainWritable();
				}
				IOUtils.closeQuietly(reader);
			}
			System.out.println("output: " + path.toString());
			HDFSUtils.delete(conf, path);
			System.out.println("hdfs delete file : " + path.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return maxAttribute;
	}
	
	/**
	 * 构造决策树
	 * @param input
	 * @return
	 */
	public Object build(String input, Data data) {
		Object preHandleResult = preHandle(data);
		if (null != preHandleResult) return preHandleResult;
		String output = HDFSUtils.HDFS_TEMP_OUTPUT_URL;
		try {
			HDFSUtils.delete(conf, new Path(output));
			System.out.println("delete path : " + output);
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] paths = new String[]{input, output};
		DecisionTreeC45MR.main(paths);
		
		AttributeGainWritable bestAttr = chooseBestAttribute(output);
		String attribute = bestAttr.getAttribute();
		System.out.println("best attribute: " + attribute);
		System.out.println("isCategory: " + bestAttr.isCategory());
		if (bestAttr.isCategory()) {
			return attribute;
		}
		String[] splitPoints = bestAttr.obtainSplitPoints();
		System.out.print("splitPoints: ");
		ShowUtils.print(splitPoints);
		TreeNode treeNode = new TreeNode(attribute);
		String[] attributes = data.getAttributesExcept(attribute);
		
		DataSplit dataSplit = DataHandler.split(new Data(
				data.getInstances(), attribute, splitPoints));
		for (DataSplitItem item : dataSplit.getItems()) {
			String path = item.getPath();
			String name = path.substring(path.lastIndexOf(File.separator) + 1);
			String hdfsPath = HDFSUtils.HDFS_TEMP_DATA_URL + name;
			HDFSUtils.copyFromLocalFile(conf, path, hdfsPath);
			treeNode.setChild(item.getSplitPoint(), build(hdfsPath, 
					new Data(attributes, item.getInstances())));
		}
		return treeNode;
	}
	
	private void classify(TreeNode treeNode, Path testSetPath, 
			String output) {
		OutputStream out = null;
		BufferedWriter writer = null;
		try {
			FileSystem fs = testSetPath.getFileSystem(conf);
			Path[] hdfsPaths = HDFSUtils.getPathFiles(fs, testSetPath);
			FSDataInputStream fsInputStream = fs.open(hdfsPaths[0]);
			Data testData = DataLoader.load(fsInputStream, true);
			DataHandler.fill(testData.getInstances(), data.getAttributes(), 0);
			Object[] results = (Object[]) treeNode.classify(testData);
			String path = FileUtils.obtainRandomTxtPath();
			out = new FileOutputStream(new File(path));
			writer = new BufferedWriter(new OutputStreamWriter(out));
			StringBuilder sb = null;
			for (int i = 0, len = results.length; i < len; i++) {
				sb = new StringBuilder();
				sb.append(i+1).append("\t").append(results[i]);
				writer.write(sb.toString());
				writer.newLine();
			}
			writer.flush();
			Path outputPath = new Path(output);
			fs = outputPath.getFileSystem(conf);
			if (!fs.exists(outputPath)) {
				fs.mkdirs(outputPath);
			}
			String name = path.substring(path.lastIndexOf(File.separator) + 1);
			HDFSUtils.copyFromLocalFile(conf, path, output + 
					File.separator + name);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(out);
			IOUtils.closeQuietly(writer);
		}
	}
	
	public void run(String[] args) {
		try {
			if (null == conf) conf = new Configuration();
			String[] inputArgs = new GenericOptionsParser(
					conf, args).getRemainingArgs();
			if (inputArgs.length != 3) {
				System.out.println("error, please input three path.");
				System.out.println("1. trainset path.");
				System.out.println("2. testset path.");
				System.out.println("3. result output path.");
				System.exit(2);
			}
			String input = prepare(new Path(inputArgs[0]));
			TreeNode treeNode = (TreeNode) build(input, data);
			TreeNodeHelper.print(treeNode, 0, null);
			String testSetPath = inputArgs[1];
			String output = inputArgs[2];
			classify(treeNode, new Path(testSetPath), output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		DecisionTreeC45Job job = new DecisionTreeC45Job();
		long startTime = System.currentTimeMillis();
		job.run(args);
		long endTime = System.currentTimeMillis();
		System.out.println("spend time: " + (endTime - startTime));
	}

}