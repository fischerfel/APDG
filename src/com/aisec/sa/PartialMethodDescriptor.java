package com.aisec.sa;

import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

public class PartialMethodDescriptor {
	
	private static final String SEPARATOR = "|";
	
	private MethodReference mr;
	
	private String methodName;
	private String declaringClass;
	private List<String> params;
	private int paramCount;
	private String returnType;
	
	private String completeMethodDescriptor;
	
	public PartialMethodDescriptor(MethodReference mr) {
		if (mr == null)
			throw new IllegalArgumentException();
		
		this.mr = mr;
		this.params = new LinkedList<String>();
		
		this.completeMethodDescriptor = this.createCompleteMethodDescriptor(this.mr);
	}
	
	private String createCompleteMethodDescriptor(MethodReference mr) {
		if (mr == null)
			throw new IllegalArgumentException();

		this.methodName = mr.getName().toString();

		TypeReference tr = mr.getDeclaringClass();
		if (tr != null) {
			String tmpDeclaringClass = tr.getName().getClassName().toString();
			if (!tmpDeclaringClass.contains(SemanticVector.UNKNOWN_TYPE) && tmpDeclaringClass.length() > 1) {
				this.declaringClass = tmpDeclaringClass;
			} 
		}

		Descriptor d = mr.getDescriptor();
		if (d != null) {
			this.paramCount = d.getNumberOfParameters();
			
			TypeName[] paramsArr = d.getParameters();
			List<String> tmpParams = new LinkedList<String>();
			
			for (int i = 0; paramsArr != null && i < paramsArr.length; i++) {
				String paramType = paramsArr[i].getClassName().toString();
				
				if (paramType != null && !paramType.contains(SemanticVector.UNKNOWN_TYPE)
						&& paramType.length() > 1) {
					tmpParams.add(paramType);
				}
			}
			
			if (tmpParams.size() > 0 && tmpParams.size() == this.paramCount)
				this.params = tmpParams;
		}

		TypeReference r = mr.getReturnType();
		String tmpReturnType = r.getName().getClassName().toString();
		if (!tmpReturnType.contains(SemanticVector.UNKNOWN_TYPE)
				&& tmpReturnType.length() > 1) {
			this.returnType = tmpReturnType;
		}
		
		if (this.methodName != null && this.declaringClass != null
				&& ((this.params != null && this.params.size() > 0) || this.paramCount == 0)  
				&& this.returnType != null)
			this.completeMethodDescriptor = 
				this.declaringClass + PartialMethodDescriptor.SEPARATOR + this.methodName + PartialMethodDescriptor.SEPARATOR 
				+ ((this.paramCount > 0) ? (this.createParamString() + PartialMethodDescriptor.SEPARATOR) : "") + this.returnType;
		
		return this.completeMethodDescriptor;
	}

	public MethodReference getMr() {
		return mr;
	}
	
	public void setMr(MethodReference mr) {
		this.mr = mr;
		
		this.completeMethodDescriptor = this.createCompleteMethodDescriptor(mr);
	}

	public String getMethodName() {
		return methodName;
	}

	public String getDeclaringClass() {
		return declaringClass;
	}

	public List<String> getParams() {
		return params;
	}
	
	public int getParamCount() {
		return this.paramCount;
	}

	public String getReturnType() {
		return returnType;
	}

	public String getCompleteMethodDescriptor() {
		return completeMethodDescriptor;
	}
	
	public String createParamString() {
		String params = new String();
		for (String param : this.params)
			params += (param + PartialMethodDescriptor.SEPARATOR);
		
		// delete last separator
		if (params.length() > 1)
			params = params.substring(0, params.length() - 1);

		if (params.length() > 0)
			return params;
		else
			return null;
	}
}
