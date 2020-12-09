package com.aisec.sa.apdg;

import java.util.ArrayList;
import java.util.List;

public class Slice {

	private String instructionName;
	private String instructionType;
	private String statementKind;
	private String signature;
	private String stringConstant;
	private String numericConstant;
	private String lineNumber;

	public String getInstructionName() {
		return instructionName;
	}

	public void setInstructionName(String instruction) {
		this.instructionName = instruction;
	}

	public String getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(String lineNumber) {
		this.lineNumber = lineNumber;
	}

	public String getInstructionType() {
		return instructionType;
	}

	public void setInstructionType(Integer instructionType) {
		this.instructionType = instructionType.toString();
	}

	public String getStatementKind() {
		return statementKind;
	}

	public void setStatementKind(String statementKind) {
		this.statementKind = statementKind;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature.replace(" ", "");
	}

	public String getStringConstants() {
		return stringConstant;
	}

	public void setStringConstants(String stringConstants) {
		this.stringConstant = stringConstants;
	}

	public String getNumericConstants() {
		return numericConstant;
	}

	public void setNumericConstants(String numericConstants) {
		this.numericConstant = numericConstants;
	}

	@Override
	public String toString() {
		List<String> labels = new ArrayList<String>();
		String label = new String();

		if (this.statementKind != null)
			labels.add(this.statementKind);
		if (this.instructionType != null)
			labels.add(this.instructionType);
		if (this.signature != null)
			labels.add(this.signature);
		if (this.stringConstant != null)
			labels.add(this.stringConstant);
		if (this.lineNumber != null)
			labels.add(this.lineNumber);
		if (this.instructionName != null)
			labels.add(this.instructionName);

		for (int i = 0; i < labels.size(); i++) {
			if (i < labels.size() - 1)
				label += labels.get(i) + "||";
			else
				label += labels.get(i);
		}

		return label;
	}
}