package com.aisec.sa.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.eclipse.core.runtime.Path;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.ibm.wala.ipa.callgraph.CGNode;

public class SemanticVectorIO {
	
	private String svFilePath; 
	
	private String nodeFilePath;
	
	private File svOutputFile;
	
	private File nodesOutputFile;
	
	private FileOutputStream svOutputStream;
	
	private GZIPOutputStream svGzipOutputStream;
	
	private FileOutputStream nodesOutputStream;
	
	private GZIPOutputStream nodesGzipOutputStream;
	
	public SemanticVectorIO(String svFilePath, String nodeFilePath) throws IOException {
		if (svFilePath == null)
			throw new IllegalArgumentException("path");
		
		this.svFilePath = svFilePath;
		Path path = new Path(this.svFilePath);
		
		this.svOutputFile = path.toFile();
		this.svOutputFile.createNewFile();
		
		if (nodeFilePath != null) {
			this.nodeFilePath = nodeFilePath;
			Path nodePath = new Path(this.nodeFilePath); 
			
			this.nodesOutputFile = nodePath.toFile();
			this.nodesOutputFile.createNewFile();
		}
	}

	public String getAbsPath() {
		return svFilePath;
	}

	public File getOutputFile() {
		return svOutputFile;
	}
	
//	public void writeToFile(List<int[]> semanticVectorList, boolean doAppend) {
//		if (semanticVectorList == null)
//			throw new IllegalArgumentException();
//		
//		BufferedWriter bw = null;
//		
//		try {
//			FileWriter fw = new FileWriter(this.outputFile, doAppend);
//			bw = new BufferedWriter(fw);
//			
//			for (int[] semanticVector : semanticVectorList) {
//				String semanticVectorString = new String();
//				int semanticVectorLength = semanticVector.length;
//				for (int i = 0; i < semanticVector.length - 1; i++) {
//					Integer count = new Integer(semanticVector[i]);
//					
//					semanticVectorString += count.toString() + " ";
//				}
//				semanticVectorString += semanticVector[semanticVectorLength - 1];
//				
////				System.out.println(semanticVectorString);
//				
//				bw.write(semanticVectorString);
//				bw.newLine();
//			}
//			
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
////		}
//		} finally {
//			try {
//				if (bw != null)
//					bw.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//	}
	
	public void writeToNodesFile(boolean doAppend, JSONArray nodes) {
		if (nodes == null) 
			throw new IllegalArgumentException();
		
		if (this.nodesOutputFile == null)
			return;
		
		try {
			if (this.nodesOutputStream == null) {
				this.nodesOutputStream = new FileOutputStream(this.nodesOutputFile,
						doAppend);
				this.nodesGzipOutputStream = new GZIPOutputStream(this.nodesOutputStream);

			} else if (this.nodesGzipOutputStream == null) {
				this.nodesGzipOutputStream = new GZIPOutputStream(this.nodesOutputStream);
			}

//			System.out.println(nodes.toJSONString());
			this.nodesGzipOutputStream.write(nodes.toJSONString().getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void writeToGZIPVecFile(int[] semanticVector, CGNode n, boolean doAppend) {
		if (semanticVector == null)
			throw new IllegalArgumentException();

		try {
			if (this.svOutputStream == null) {
				this.svOutputStream = new FileOutputStream(this.svOutputFile,
						doAppend);
				this.svGzipOutputStream = new GZIPOutputStream(
						this.svOutputStream);
			} else if (this.svGzipOutputStream == null) {
				this.svGzipOutputStream = new GZIPOutputStream(
						this.svOutputStream);
			}

			String semanticVectorString = this.createStringFromVec(semanticVector);
			semanticVectorString += System.getProperty("line.separator");

//			 System.out.println(semanticVectorString);

			svGzipOutputStream.write(semanticVectorString.getBytes());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private String createStringFromVec(int[] semanticVector) {
		String semanticVectorString = new String();
		int semanticVectorLength = semanticVector.length;
		for (int i = 0; i < semanticVector.length - 1; i++) {
			Integer count = new Integer(semanticVector[i]);

			semanticVectorString += count.toString() + " ";
		}
		
		return semanticVectorString;
	}
	
	public JSONObject createJSON(int vecId, int[] vector,
			CGNode n, Set<String> methodNameSet, Set<String> typeNameSet, List<String> valueSet) {
		
		if (vector == null || n == null || methodNameSet == null || typeNameSet == null || valueSet == null)
			throw new IllegalArgumentException();
		
		String semanticVectorString = this.createStringFromVec(vector);
		
		JSONObject method = new JSONObject();
		method.put("id", vecId);
		
		String methodSignature = n.getMethod().getSignature();
		if (methodSignature != null)
			method.put("cgn", n.getMethod().getSignature());
		
		if (semanticVectorString != null)
			method.put("vec", semanticVectorString);

		JSONArray methodNames = new JSONArray();
		for (String methodName : methodNameSet) {
			methodNames.add(methodName);
		}
		
		JSONArray typeNames = new JSONArray();
		for (String typeName : typeNameSet) {
			typeNames.add(typeName);
		}
		
		JSONArray values = new JSONArray();
		for (String value : valueSet) {
			values.add(value);
		}
		
		method.put("methodNames", methodNames);
//		method.put("typeNames", typeNames);
		method.put("constants", values);
		
		return method;
	}
		
	public void closeSvOutput() {
		try {
			if (this.svGzipOutputStream != null) {
				this.svGzipOutputStream.close();
				this.svGzipOutputStream = null;
			}
			if (this.svOutputStream != null) {
				this.svOutputStream.close();
				this.svOutputStream = null;
			}
			
			this.closeNodesOutput();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void closeNodesOutput() {
		try {
			if (this.nodesGzipOutputStream != null) {
				this.nodesGzipOutputStream.close();
				this.nodesGzipOutputStream = null;
			}
			if (this.nodesOutputStream != null) {
				this.nodesOutputStream.close();
				this.nodesOutputStream = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}