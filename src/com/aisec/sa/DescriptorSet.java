package com.aisec.sa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

public class DescriptorSet {
	
	private static final String[] pkgWhitelist = { "com/sun/security/auth",
			"com/sun/security/jgss", "com/sun/security/ntlm",
			"com/sun/security/sasl", "com/sun/security/cert", "java/security",
			"javax/net/ssl", "javax/crypto", "javax/xml/crypto",
			"javax/security", "org/ietf/jgss", "org/bouncycastle/jce",
			"org/bouncycastle/asn1", "org/bouncycastle/pqc/asn1",
			"org/bouncycastle/pqc/jcajce", "org/bouncycastle/pqc/crypto",
			"org/bouncycastle/i18n/filter", "org/bouncycastle/jcajce",
			"org/bouncycastle/crypto", "org/apache/http/conn/ssl", "org/bouncycastle/x509", "gnu/crypto",
			"org/jasypt", "org/keyczar", "org/spongycastle"};
	
	private Map<String, Integer> pkgWhitelistMap;
	
	private Set<String> methodNameSet;
	
	private Set<String> packageNameSet;
	
	private Set<String> typeNameSet;
	
	public DescriptorSet() {
		this.pkgWhitelistMap = new HashMap<String, Integer>();
		for (int i = 0; i < DescriptorSet.pkgWhitelist.length; i++) {
			this.pkgWhitelistMap.put(pkgWhitelist[i], i);
		}
	}
	
	private boolean isInPackageWhitelist(String pkgName) {
		if (pkgName == null)
			return false;
		
		for (String listedPkg : this.pkgWhitelistMap.keySet()) {
			if (pkgName.contains(listedPkg))
				return true;
		}
		
		return false;
	}
	
	private List<MethodReference> getMethodReferences(List<Statement> statements) {
		if (statements == null)
			throw new IllegalArgumentException();
		
		LinkedList<MethodReference> mrList = new LinkedList<MethodReference>();
		for (Statement s : statements) {
			if (s.getKind().equals(Statement.Kind.NORMAL)) {
				NormalStatement ns = (NormalStatement) s;
				SSAInstruction ssa = ns.getInstruction();
				
				if (ssa instanceof SSAAbstractInvokeInstruction) {
					SSAAbstractInvokeInstruction aii = (SSAAbstractInvokeInstruction) ssa;
					MethodReference mr = aii.getDeclaredTarget();
					
					// always add method name to set for counting 
					// all its occurrences in the semantic vector
					if (mr != null) {
						String methodName = mr.getName().toString();
						mrList.add(mr);
					}
				}
			}
		}
		
		return mrList;
	}
	
	public void buildPackageNameSet(List<Statement> statements) {
		this.packageNameSet = new HashSet<String>();

		if (statements == null) 
			throw new IllegalArgumentException();
		
		List<MethodReference> mrList = this.getMethodReferences(statements);
		if (mrList == null || mrList.size() == 0)
			return;
		
		for (MethodReference mr : mrList) {
			TypeReference dc = mr.getDeclaringClass();
			if (dc != null) {
				TypeName tn = dc.getName();
				if (tn != null) {
					Atom pkg = tn.getPackage();
					if (pkg != null) {
						String pkgName = pkg.toString();
						if (pkgName != null && pkgName.length() != 0 && this.isInPackageWhitelist(pkgName)) {
							this.packageNameSet.add(pkgName);
						}
					}
				}
			}
		}
	}
	
	public void buildMethodAndTypeNameSet(List<ArrayList<Statement> > sbList) {
		this.methodNameSet = new HashSet<String>();
		this.typeNameSet = new HashSet<String>();
		
		if (sbList == null)
			throw new IllegalArgumentException();
		
		for (ArrayList<Statement> sb : sbList)
			for (Statement s : sb) {
				if (s instanceof StatementWithInstructionIndex) {
					StatementWithInstructionIndex swii = (StatementWithInstructionIndex) s;
					SSAInstruction ssa = swii.getInstruction();

					if (ssa instanceof SSAAbstractInvokeInstruction) {
						SSAAbstractInvokeInstruction aii = (SSAAbstractInvokeInstruction) ssa;
						MethodReference mr = aii.getDeclaredTarget();

						if (mr != null) {
							String methodName = mr.getName().toString();
							this.methodNameSet.add(methodName);
						}

						TypeReference dc = mr.getDeclaringClass();
						TypeReference rt = mr.getReturnType();

						if (dc != null)
							this.typeNameSet.add(dc.getName().toString());
						if (rt != null)
							this.typeNameSet.add(rt.getName().toString());

						for (int i = 0; i < mr.getNumberOfParameters(); i++) {
							TypeReference pt = mr.getParameterType(i);
							if (pt != null) {
								this.typeNameSet.add(pt.getName().toString());
							}
						}
					}
				}
			}
	}
	
	public Map<String, Integer> getPkgWhitelistSet() {
		return this.pkgWhitelistMap;
	}
	
	public Set<String> getMethodNameSet() {
		return this.methodNameSet;
	}
	
	public Set<String> getPackageNameSet() {
		return this.packageNameSet;
	}
	
	public void setPackageNameSet(Set<String> packNameSet) {
		this.packageNameSet = packNameSet;
	}
	
	public Set<String> getTypeNameSet() {
		return this.typeNameSet;
	}
	
	public static int getPkgWhitelistLength() {
		return DescriptorSet.pkgWhitelist.length;
	}

}
