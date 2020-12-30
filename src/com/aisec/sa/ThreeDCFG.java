package com.aisec.sa;

import java.util.ArrayList;
import java.util.Iterator;

import com.ibm.wala.cfg.CFGSanitizer;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.Graph;

public class ThreeDCFG {
	private IR ir;
	
	private IClassHierarchy cha;

	public ThreeDCFG(IR ir, IClassHierarchy cha) {
		this.ir = ir;
		this.cha = cha;
	}
	
	public void iterateBasicBlocks() throws IllegalArgumentException, WalaException {
		SSACFG cfg = ir.getControlFlowGraph();

		System.out.println("iterate statements of " + ir.getMethod().getName().toString());
		
		for (Iterator<ISSABasicBlock> it = cfg.iterator(); it.hasNext(); ) {
			ISSABasicBlock bb = it.next();
			
			for (Iterator<SSAInstruction> itSsa = bb.iterator(); itSsa.hasNext(); ) {
				SSAInstruction ssa = itSsa.next();
				
				System.out.println("\t" + ssa.toString());
			}
		}
	}

}
