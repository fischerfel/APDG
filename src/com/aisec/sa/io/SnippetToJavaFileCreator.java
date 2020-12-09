package com.aisec.sa.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class SnippetToJavaFileCreator {
		
	private final String inputFileAbsPath;
	
	private final String outputDirectoryAbsPath;
	
	private String javaFileName;
	
	private String wellFormedJavaCode;
	
	public SnippetToJavaFileCreator(String inputFileAbsPath, String outputDirectoryAbsPath, String javaFileName, String wellFormedJavaCode) {
		this.inputFileAbsPath = inputFileAbsPath;
		this.outputDirectoryAbsPath = outputDirectoryAbsPath;
		this.javaFileName = javaFileName;
		this.wellFormedJavaCode = wellFormedJavaCode;
	}
	
	public void createJavaFile() throws Exception {
		if (this.wellFormedJavaCode == null && this.inputFileAbsPath != null) {
			SnippetParser sp = new SnippetParser(this.inputFileAbsPath);
		
			this.wellFormedJavaCode = sp.getWellFormedJavaCodeFromFile();
//			this.javaFileName = sp.getPublicClassName();
//			System.out.println("well formed code " + wellFormedJavaCode);
		}
		
		if (this.wellFormedJavaCode == null)
			throw new Exception("could not create java file");
		
		BufferedWriter writer = null;
        try {
        	File javaFile = new File(this.outputDirectoryAbsPath + File.separator + this.javaFileName);

            writer = new BufferedWriter(new FileWriter(javaFile));
            writer.write(wellFormedJavaCode);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                writer.close();
            } catch (Exception e) {
            }
        }
	}

	public String getInputFileAbsPath() {
		return inputFileAbsPath;
	}

	public String getOutputDirectoryAbsPath() {
		return outputDirectoryAbsPath;
	}

	public String getJavaFileName() {
		return javaFileName;
	}
	
	public String getWellFormedJavaCode() {
		return this.wellFormedJavaCode;
	}
	
	public void setWellFormedJavaCode(String code) {
		this.wellFormedJavaCode = code;
	}
}
