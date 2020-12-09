package com.aisec.sa.util;

import java.util.Collection;
import java.util.concurrent.Callable;

import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.CancelException;

public class CallableSlicer implements Callable<Collection<Statement>> {
	
	private SDG sdg;
	private Statement s;
	
	public CallableSlicer(SDG sdg, Statement s) {
		this.sdg = sdg;
		this.s = s;
	}

	@Override
	public Collection<Statement> call() {
		Collection<Statement> slice = null;
		try {
			slice = com.ibm.wala.ipa.slicer.Slicer.computeBackwardSlice(sdg, s);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (CancelException e) {
			e.printStackTrace();
		}
		
		return slice;
	}

}
