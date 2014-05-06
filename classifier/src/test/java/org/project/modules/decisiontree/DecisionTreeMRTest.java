package org.project.modules.decisiontree;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.util.ReflectionUtils;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.project.modules.decisiontree.builder.Builder;
import org.project.modules.decisiontree.builder.DecisionTreeC45Builder;
import org.project.modules.decisiontree.data.Data;
import org.project.modules.decisiontree.data.DataLoader;
import org.project.modules.decisiontree.mr.BuilderMapperOutput;
import org.project.modules.decisiontree.node.TreeNode;
import org.project.utils.DFSUtils;
import org.project.utils.JSONUtils;
import org.project.utils.ShowUtils;

public class DecisionTreeMRTest {

	public static final String DFS_URL = "hdfs://centos.host1:9000/user/hadoop/data/example/";
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
	public void json() {
		Builder treeBuilder = new DecisionTreeC45Builder();
		String trainFilePath = "d:\\trainset_extract_10.txt";
		Data data = DataLoader.load(trainFilePath);
		TreeNode tree = (TreeNode) treeBuilder.build(data);
		String jsonData = JSONUtils.object2json(tree,
				new String[] {"attributeValues", "children", "class"});
		System.out.println(jsonData);
	}

	@Test
	public void jackson() throws JsonProcessingException, IOException {
		Builder treeBuilder = new DecisionTreeC45Builder();
		String trainFilePath = "d:\\trainset_extract_10.txt";
		Data data = DataLoader.load(trainFilePath);
		TreeNode tree = (TreeNode) treeBuilder.build(data);
		System.out.println("jsonGenerator");        
		//writeObject可以转换java对象，eg:JavaBean/Map/List/Array等        
		jsonGenerator.writeObject(tree);            
		System.out.println();                
	}
	
	@Test
	public void testRead() {
		
	}

	@Test
	public void writeSequenceFile() {
		SequenceFile.Writer writer = null;
		try {
			FileSystem fs = FileSystem.get(conf);
			Path path = new Path(DFS_URL + "005/output/part-m-00000");
			writer = SequenceFile.createWriter(fs, conf, path,
					LongWritable.class, BuilderMapperOutput.class);
			LongWritable key = new LongWritable(1);
			Builder treeBuilder = new DecisionTreeC45Builder();
			String trainFilePath = "d:\\trainset_extract_10.txt";
			Data data = DataLoader.load(trainFilePath);
			TreeNode tree = (TreeNode) treeBuilder.build(data);
			BuilderMapperOutput value = new BuilderMapperOutput(tree);
			writer.append(key, value);
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
			BuilderMapperOutput value = new BuilderMapperOutput();
			while (reader.next(key, value)) {
				System.out.println(value.getTreeNode().getAttribute());
				value = new BuilderMapperOutput();
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
			ShowUtils.print(DFSUtils.getPathFiles(fs, path));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
