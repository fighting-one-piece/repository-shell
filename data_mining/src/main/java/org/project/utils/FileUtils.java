package org.project.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.commons.io.IOUtils;

public class FileUtils {
	
	private FileUtils() {
		
	}
	
	public static File create(String path) {
		File file = new File(path);
		File parent = file.getParentFile();
		if (!parent.exists()) {
			parent.mkdir();
		}
		return file;
	}

	public static String obtainOSTmpPath() {
		String os = System.getProperty("os.name").toLowerCase();
		String tmpPath = null;
		if (os.contains("windows")) {
			tmpPath = System.getProperty("java.io.tmpdir");
		} else if (os.contains("linux")) {
			tmpPath = System.getProperty("user.home") + File.separator 
					+ "temp" + File.separator;
		}
		return tmpPath;
	}
	
	public static String obtainRandomTxtPath() {
		return obtainOSTmpPath() + IdentityUtils.generateUUID() + ".txt";
	}
	
	public static void addLineNum(String input, String output) {
		InputStream in = null;
		BufferedReader reader = null;
		OutputStream out = null;
		BufferedWriter writer = null;
		try {
			in = new FileInputStream(new File(input));
			reader = new BufferedReader(new InputStreamReader(in));
			out = new FileOutputStream(new File(output));
			writer = new BufferedWriter(new OutputStreamWriter(out));
			String line = reader.readLine();
			StringBuilder sb = null;
			long lineNum = 1;
			while (null != line) {
				sb = new StringBuilder();
				sb.append(lineNum++).append(" ").append(line);
				writer.write(sb.toString());
				writer.newLine();
				line = reader.readLine();
			}
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(reader);
			IOUtils.closeQuietly(out);
			IOUtils.closeQuietly(writer);
		}
	}
}