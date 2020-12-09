package com.aisec.sa.test;

import java.util.ArrayList;
import java.util.List;

import com.aisec.sa.apdg.APDG;
import com.ibm.wala.ipa.slicer.Statement;

public class APDGTest {
	List<Statement> testStatements = new ArrayList<Statement>();

	public APDGTest() {

	}

	public boolean testParseSignatures() {
		return false;
	}

	public boolean testGetSignatureCandidate() {
		return false;
	}

	public boolean testGetOffspringCount() {
		return false;
	}

	public boolean testGetMethodSiganture() {
		APDG apdg = new APDG(null, null, null, null, null, null);
		for (Statement s : this.testStatements) {
		}

		return false;
	}


}