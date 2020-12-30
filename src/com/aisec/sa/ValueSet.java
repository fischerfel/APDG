package com.aisec.sa;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.ipa.slicer.ValueNumberCarrier;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;

public class ValueSet {
	
	private static String VN_VALUE_SEPARATOR = "#";
	
	private Set<String> valueSet;
	
	private int maxInt;
	
	private int maxStringLength;
	
	private int intConstantCount;
	
	private int stringConstantCount;
	
	public ValueSet() {
		this.valueSet = new HashSet<String>();
	}
	
	public static String getValueFromValueString(String valueString) {
		if (valueString != null) {
			int seperatorIndex = valueString.indexOf(ValueSet.VN_VALUE_SEPARATOR);
			if (seperatorIndex >= 0 && seperatorIndex < valueString.length()) {
				return valueString.substring(seperatorIndex + 1);
			} else {
				System.out.println("variable number, value seperator not found " + valueString);
			}
		}

		return null;
	}
	
	public void buildValueSetFromStatements(List<Statement> statements, IR ir) {
		if (statements == null || ir == null)
			throw new IllegalArgumentException();
		
		SymbolTable st = ir.getSymbolTable();
		for (Statement s : statements) {
			if (s instanceof ValueNumberCarrier) {
				ValueNumberCarrier vnc = (ValueNumberCarrier) s;
				int vn = vnc.getValueNumber();
				
				String value = getValueFromValueString(st.getValueString(vn));
				if (value != null && value.length() != 0 && !value.equals("null")) {
					if (st.isIntegerConstant(vn)) {
						this.intConstantCount++;
						
						Integer intValue = new Integer(st.getIntValue(vn));
						if (intValue > this.maxInt)
							this.maxInt = intValue;
					} else if (st.isStringConstant(vn)) {
						this.stringConstantCount++;
						
						String stringValue = st.getStringValue(vn);
						int stringLength = stringValue.length();
						if (stringLength > this.maxStringLength)
							this.maxStringLength = stringLength;
					}
					
					this.valueSet.add(value);
				}
			}
			
			if (s instanceof StatementWithInstructionIndex) {
				StatementWithInstructionIndex swii = (StatementWithInstructionIndex) s;
				SSAInstruction ssa = swii.getInstruction();
				
				int defVarCount = ssa.getNumberOfDefs();
				for(int i = 0; i < defVarCount; i++) {
					int vn = ssa.getDef(i);
					
					if (st.isConstant(vn)) {
						String value = null;
						
						if (st.isIntegerConstant(vn)) {
							this.intConstantCount++;
							
							Integer intValue = new Integer(st.getIntValue(vn));
							if (intValue > this.maxInt)
								this.maxInt = intValue;
							value = intValue.toString();
						} else if (st.isStringConstant(vn)) {
							this.stringConstantCount++;
							
							value = st.getStringValue(vn);
							int stringLength = value.length();
							if (stringLength > this.maxStringLength)
								this.maxStringLength = stringLength;
						}
						
						if (value != null && value.length() != 0 && !value.equals("null")) {
							this.valueSet.add(value);
						}
					}
				}
			} else {
				System.out.println("\tno vnc");
			}
		}
		
	}
	
	public void buildValueSetFromCGNodes(List<CGNode> cgNodes, PDG pdg) {
		if (cgNodes == null)
			throw new IllegalArgumentException();
		
		for(CGNode n : cgNodes) {
			IR ir = n.getIR();
			
			LinkedList<Statement> statements = new LinkedList<Statement>();
			for (Iterator<Statement> pdgIt = pdg.iterator(); pdgIt.hasNext();) {
				Statement s = pdgIt.next();
				statements.add(s);
			}
			
			this.buildValueSetFromStatements(statements, ir);
		}
	}
	
	public Set<String> getValueSet() {
		return this.valueSet;
	}
	
	public int getMaxInt() {
		return this.maxInt;
	}
	
	public int getMaxStringLength() {
		return this.maxStringLength;
	}
	
	public int getIntConstantCount() {
		return this.intConstantCount;
	}
	
	public int getStringConstantCount() {
		return this.stringConstantCount;
	}
}
