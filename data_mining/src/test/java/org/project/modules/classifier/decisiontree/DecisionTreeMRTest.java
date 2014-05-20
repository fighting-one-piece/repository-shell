package org.project.modules.classifier.decisiontree;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.ReflectionUtils;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.ser.impl.SimpleBeanPropertyFilter;
import org.codehaus.jackson.map.ser.impl.SimpleFilterProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.project.modules.classifier.decisiontree.builder.Builder;
import org.project.modules.classifier.decisiontree.builder.DecisionTreeC45Builder;
import org.project.modules.classifier.decisiontree.data.Data;
import org.project.modules.classifier.decisiontree.data.DataHandler;
import org.project.modules.classifier.decisiontree.data.DataLoader;
import org.project.modules.classifier.decisiontree.mr.writable.AttributeGainWritable;
import org.project.modules.classifier.decisiontree.mr.writable.TreeNodeWritable;
import org.project.modules.classifier.decisiontree.node.TreeNode;
import org.project.modules.classifier.decisiontree.node.TreeNodeHelper;
import org.project.utils.HDFSUtils;
import org.project.utils.JSONUtils;
import org.project.utils.ShowUtils;

import com.alibaba.fastjson.JSONReader;
import com.alibaba.fastjson.JSONWriter;

public class DecisionTreeMRTest {

	public static final String DFS_URL = "hdfs://centos.host1:9000/user/hadoop/data/dt/";
	// public static final String DFS_URL =
	// "hdfs://hadoop-namenode-1896:9000/user/hadoop_hudong/project/rf/";

	private Configuration conf = new Configuration();

	private JsonGenerator jsonGenerator = null;

	private ObjectMapper objectMapper = null;

	@Before
	public void before() {
		conf.addResource(new Path(
				"D:\\develop\\data\\hadoop\\hadoop-1.0.4\\conf\\core-site.xml"));
		objectMapper = new ObjectMapper();
		try {
//			FilterProvider filterProvider = new SimpleFilterProvider()
//					.addFilter("a", SimpleBeanPropertyFilter
//							.serializeAllExcept(new String[]{"attributeValues"}));
//			objectMapper.setFilters(filterProvider);
			SerializationConfig cfg = objectMapper.getSerializationConfig();
			SimpleFilterProvider filterProvider = new SimpleFilterProvider();
			filterProvider.setDefaultFilter(SimpleBeanPropertyFilter.serializeAllExcept(
					new String[]{"attributeValues"}));
			cfg.withFilters(filterProvider);
			objectMapper.setSerializationConfig(cfg);

			jsonGenerator = objectMapper.getJsonFactory().createJsonGenerator(
					System.out, JsonEncoding.UTF8);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@After
	public void destory() {
		try {
			if (jsonGenerator != null) {
				jsonGenerator.flush();
			}
			if (!jsonGenerator.isClosed()) {
				jsonGenerator.close();
			}
			jsonGenerator = null;
			objectMapper = null;
			System.gc();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void tree2json() {
		Builder treeBuilder = new DecisionTreeC45Builder();
		String trainFilePath = "d:\\trainset_extract_10.txt";
		Data data = DataLoader.load(trainFilePath);
		DataHandler.fill(data, 0);
		TreeNode treeNode = (TreeNode) treeBuilder.build(data);
		Set<TreeNode> treeNodes = new HashSet<TreeNode>();
		TreeNodeHelper.splitTreeNode(treeNode, 25, 0, treeNodes);
		for (TreeNode node : treeNodes) {
			StringBuilder sb = new StringBuilder();
			TreeNodeHelper.treeNode2json(node, sb);
			System.out.println(sb.toString());
		}
	}
	
	@Test
	public void json() throws JsonProcessingException, IOException {
		Builder treeBuilder = new DecisionTreeC45Builder();
		String trainFilePath = "d:\\trains14.txt";
		Data data = DataLoader.load(trainFilePath);
		DataHandler.fill(data, 0);
		TreeNode tree = (TreeNode) treeBuilder.build(data);
		TreeNodeHelper.print(tree, 0, null);
		System.out.println("jsonGenerator");
		String jsonData = JSONUtils.object2json(tree,
				new String[] { "attributeValues" });
		System.out.println(jsonData);
		System.out.println("jacksonGenerator");
		jsonGenerator.writeObject(tree);
		System.out.println();
		StringBuilder sb = new StringBuilder();
		handle(tree, sb);
		System.out.println(sb.toString());
		
		TreeNode temp = (TreeNode) TreeNodeHelper.json2TreeNode(sb.toString());
		System.out.println(temp.getName());
		ShowUtils.print(temp.getChildren());
	}
	
	private void handle(TreeNode treeNode, StringBuilder sb) {
		sb.append("{");
		sb.append("\"attribute\":");
		sb.append("\"" + treeNode.getName()).append("\",");
		Map<Object, Object> children = treeNode.getChildren();
		if (children.size() != 0) {
			sb.append("\"children\":");
			sb.append("{");
			int i = 0;
			for (Map.Entry<Object, Object> entry : children.entrySet()) {
				i++;
				Object value = entry.getValue();
				sb.append("\"" + entry.getKey() + "\":");
				if (value instanceof TreeNode) {
//					sb.append("\"" + entry.getKey() + "\":");
					handle((TreeNode) value, sb);
//					if (i != children.size()) sb.append(",");
				} else {
//					sb.append("\"" + entry.getKey() + "\":");
					sb.append("\"" + value + "\"");
//					if (i != children.size()) sb.append(",");
				}
				if (i != children.size()) sb.append(",");
			}
			sb.append("}");
		}
		sb.append("}");
	}
	
	@Test
	public void fastJson() throws IOException {
		Builder treeBuilder = new DecisionTreeC45Builder();
		String trainFilePath = "d:\\trains14.txt";
		Data data = DataLoader.load(trainFilePath);
		DataHandler.fill(data, 0);
		TreeNode tree = (TreeNode) treeBuilder.build(data);
		TreeNodeHelper.print(tree, 0, null);
		JSONWriter writer = new JSONWriter(new FileWriter("d:\\tree.json"));
		writer.startArray();
		writer.writeValue(tree);
		writer.endArray();
		writer.close();
		JSONReader reader = new JSONReader(new FileReader("d:\\tree.json"));
		reader.startArray();
		while (reader.hasNext()) {
			TreeNode treeNode = reader.readObject(TreeNode.class);
			System.out.println(treeNode.getName());
			System.out.println(treeNode.getChildren());
		}
		reader.endArray();
		reader.close();
	}
	
	@Test
	public void fastJsonW() throws IOException {
		Builder treeBuilder = new DecisionTreeC45Builder();
		String trainFilePath = "d:\\trainset_extract_1.txt";
		Data data = DataLoader.load(trainFilePath);
		DataHandler.fill(data, 0);
		TreeNode tree = (TreeNode) treeBuilder.build(data);
		JSONWriter writer = new JSONWriter(new PrintWriter(System.out));
		writer.startObject();
		writer.writeValue(tree);
		writer.endObject();
		writer.close();
		
	}
	
	@Test
	public void fastJsonR() throws IOException {
		Builder treeBuilder = new DecisionTreeC45Builder();
		String trainFilePath = "d:\\trains14.txt";
		Data data = DataLoader.load(trainFilePath);
		DataHandler.fill(data, 0);
		TreeNode tree = (TreeNode) treeBuilder.build(data);
		TreeNodeHelper.print(tree, 0, null);
		StringBuilder sb = new StringBuilder();
		handle(tree, sb);
		String jsonData = sb.toString();
		jsonData = "{" + jsonData + "}";
		System.out.println(jsonData);
		StringReader sreader = new StringReader(jsonData);
		JSONReader reader = new JSONReader(sreader);
		reader.startObject();
		while (reader.hasNext()) {
			TreeNode treeNode = reader.readObject(TreeNode.class);
			System.out.println(treeNode.getName());
			System.out.println(treeNode.getChildren());
			break;
		}
		reader.endObject();
		reader.close();
	}

	@Test
	public void jackson() throws JsonProcessingException, IOException {
		Builder treeBuilder = new DecisionTreeC45Builder();
		String trainFilePath = "d:\\trainset_extract_10.txt";
		Data data = DataLoader.load(trainFilePath);
		DataHandler.fill(data, 0);
		TreeNode tree = (TreeNode) treeBuilder.build(data);
		System.out.println("jsonGenerator");
		// writeObject可以转换java对象，eg:JavaBean/Map/List/Array等
		jsonGenerator.writeObject(tree);
		System.out.println();
	}

	@Test
	public void writeSequenceFile() {
		SequenceFile.Writer writer = null;
		try {
			FileSystem fs = FileSystem.get(conf);
			Path path = new Path(DFS_URL + "005/output/part-m-00005");
			writer = SequenceFile.createWriter(fs, conf, path,
					LongWritable.class, TreeNodeWritable.class);
			LongWritable key = new LongWritable(1);
			Builder treeBuilder = new DecisionTreeC45Builder();
			String trainFilePath = "d:\\trainset_extract_1.txt";
			Data data = DataLoader.load(trainFilePath);
			DataHandler.fill(data, 0);
			TreeNode treeNode = (TreeNode) treeBuilder.build(data);
			Set<TreeNode> treeNodes = new HashSet<TreeNode>();
			TreeNodeHelper.splitTreeNode(treeNode, 25, 0, treeNodes);
			for (TreeNode node : treeNodes) {
				StringBuilder sb = new StringBuilder();
				TreeNodeHelper.treeNode2json(node, sb);
				System.out.println("--" + sb.toString());
				TreeNodeWritable value = new TreeNodeWritable(node);
				writer.append(key, value);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(writer);
		}
	}

	@Test
	public void readSequenceFile() {
		SequenceFile.Reader reader = null;
		try {
			FileSystem fs = FileSystem.get(conf);
			Path path = new Path(DFS_URL + "005/output/part-m-00000");
			reader = new SequenceFile.Reader(fs, path, conf);
			LongWritable key = (LongWritable) ReflectionUtils.newInstance(
					reader.getKeyClass(), conf);
			TreeNodeWritable value = new TreeNodeWritable();
			while (reader.next(key, value)) {
				TreeNode treeNode = value.getTreeNode();
				if (null  == treeNode) continue;
				System.out.println(treeNode.getName());
				value = new TreeNodeWritable();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(reader);
		}
	}
	
	@Test
	public void readReducerFile() {
		SequenceFile.Reader reader = null;
		try {
			FileSystem fs = FileSystem.get(conf);
			Path path = new Path(DFS_URL + "001/output1/part-r-00002");
			reader = new SequenceFile.Reader(fs, path, conf);
			Text key = (Text) ReflectionUtils.newInstance(
					reader.getKeyClass(), conf);
			AttributeGainWritable value = new AttributeGainWritable();
			while (reader.next(key, value)) {
				System.out.println(value.getAttribute());
				System.out.println(value.getGainRatio());
				value = new AttributeGainWritable();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(reader);
		}
	}

	@Test
	public void listFile() {
		try {
			FileSystem fs = FileSystem.get(conf);
			Path path = new Path(DFS_URL + "input");
			ShowUtils.print(HDFSUtils.getPathFiles(fs, path));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
