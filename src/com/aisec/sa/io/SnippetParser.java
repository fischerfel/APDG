package com.aisec.sa.io;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class SnippetParser {
	
	private int flag = 0;
	private String inputFileAbsPath;
	private String publicClassName;
//	private String input_oracle;
//	private int cutype;
//	private String codeString;
//	private int bakerType;
//	
	
	public SnippetParser(String inputFileAbsPath) {
		if (inputFileAbsPath == null)
			throw new IllegalArgumentException("inputFileAbsPath");
		
		this.inputFileAbsPath = inputFileAbsPath;
	}
	
	private ASTParser getASTParser(String sourceCode, int parserType) 
	{
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setBindingsRecovery(true);
		parser.setKind(parserType);
		parser.setSource(sourceCode.toCharArray());
		return parser;
	}
	
	private String getCodefromSnippet() throws IOException 
	{
		BufferedReader br = new BufferedReader(new FileReader(this.inputFileAbsPath));
		StringBuilder codeBuilder = new StringBuilder();
		String codeText = null;
		try 
		{
			String strLine = br.readLine();
			while (strLine != null) 
			{
				codeBuilder.append(strLine);
				codeBuilder.append("\n");
				strLine = br.readLine();
			}
		}
		finally
		{
			br.close();
			codeText = codeBuilder.toString();
			codeText = codeText.replace("&lt;", "<");
			codeText = codeText.replace("&gt;", ">");
			codeText = codeText.replace("&amp;", "&");
		}
		
//		System.out.println(codeText);
		return codeText;
	}
	
	public String getWellFormedJavaCodeFromFile() throws IOException, NullPointerException, ClassNotFoundException {
		String code = this.getCodefromSnippet();
		
		return this.getWellFormedJavaCodeFromString(code);
	}
	
	public String getWellFormedJavaCodeFromString(String code) throws IOException, NullPointerException, ClassNotFoundException {
		ASTParser parser = getASTParser(code, ASTParser.K_COMPILATION_UNIT);
		ASTNode cu = (CompilationUnit) parser.createAST(null);
		
		String modifiedCode = code;
		//System.out.println(cu);
//		cutype = 0;
		List<AbstractTypeDeclaration> types = ((CompilationUnit) cu).types();
		if (types.isEmpty()) {
			flag = 1;
			// System.out.println("Missing class body, wrapper added");
//			cutype = 1;
			String s1 = "public class Foo{\n" + code + "\n}";
			this.publicClassName = "Foo";
			modifiedCode = s1;
			parser = getASTParser(s1, ASTParser.K_COMPILATION_UNIT);
			cu = parser.createAST(null);
			cu.accept(new ASTVisitor() {
				public boolean visit(MethodDeclaration node) {
					flag = 2;
					return false;
				}
			});
			if (flag == 1) {
				// System.out.println("Missing method, wrapper added");
				s1 = "public class Foo{\n public void foo(){\n" + code
						+ "\n}\n}";
				modifiedCode = s1;
				this.publicClassName = "Foo";
//				cutype = 2;
				parser = getASTParser(s1, ASTParser.K_COMPILATION_UNIT);
				cu = parser.createAST(null);
			}
			if (flag == 2) {
				s1 = "public class Foo{\n" + code + "\n}";
				modifiedCode = s1;
				this.publicClassName = "Foo";
//				cutype = 1;
				parser = getASTParser(s1, ASTParser.K_COMPILATION_UNIT);
				cu = parser.createAST(null);
			}
		} else {
//			System.out.println("Has complete class and method bodies, code not modified");
//			System.out.println("type is");
			AbstractTypeDeclaration abstractType = types.get(0);
			if (abstractType instanceof TypeDeclaration) {
				TypeDeclaration type = (TypeDeclaration) abstractType;
				this.publicClassName = type.getName().toString();
//				System.out.println(this.publicClassName);
			}
			
			modifiedCode = code;
//			cutype = 0;
			parser = getASTParser(code, ASTParser.K_COMPILATION_UNIT);
			cu = parser.createAST(null);
		}
		return modifiedCode;
	}
	
	public String getPublicClassName() {
		return this.publicClassName;
	}
}
