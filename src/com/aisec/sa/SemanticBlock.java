package com.aisec.sa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.graph.traverse.BFSIterator;


/**
 * A semantic block contains a data-independent subgraph of a 
 * data-dependent PDG which is build from a method.
 * The PDG can therefore contain several semantic blocks which 
 * do not depend on each other. 
 *  
 * @author fischer
 *
 *
 */
public class SemanticBlock {
	
	private int minSize;
	
	private final PDG pdg;
	
	private List<ArrayList<Statement>> semanticBlocks;
	
	/**
	 * default constructor which creates all semantic blocks
	 * 
	 * @param pdg
	 */
	public SemanticBlock(CGNode n, SDG sdg, int minSize) {
		if (n == null || sdg == null) 
			throw new IllegalArgumentException();
		
		this.pdg = sdg.getPDG(n);
		this.semanticBlocks = new ArrayList<ArrayList<Statement> >();
		this.minSize = minSize;
	}
		
	public PDG getPdg() {
		return this.pdg;
	}
	
	public List<ArrayList<Statement> > getSemanticBlocks() {
		return this.semanticBlocks;
	}
		
	public void buildSemanticBlocks() {
		HashSet<Statement> statementSet = new HashSet<Statement>(); 
		HashSet<Statement> controlSet = new HashSet<Statement>();
		
//		System.out.println("iterating pdg");
		for (Iterator<Statement> pdgIt = this.pdg.iterator(); pdgIt.hasNext();) {
			Statement s = pdgIt.next();
//			System.out.println(s);
			
			if (s.getKind().equals(Statement.Kind.NORMAL) 
					/*|| s.getKind().equals(Statement.Kind.EXC_RET_CALLER)*/
					|| s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)
					|| s.getKind().equals(Statement.Kind.PARAM_CALLER)) {
				statementSet.add(s);
			}
		}
		
		// remove all visited nodes by bfs from set
		// do bfs on remaining nodes until set is empty
		while (!statementSet.isEmpty()) { 
			Iterator<Statement> setIt = statementSet.iterator();
			Statement startingNode = setIt.next();
			
			// set is not empty, therefore an unconnected 
			// subgraph exists which is a semantic block
			ArrayList<Statement> semanticBlockList = new ArrayList<Statement>();
			
			// create bfs which ignores directed edges
			BFSIterator<Statement> bfsConnIter = new BFSIterator<Statement>(this.pdg, startingNode) {
				
				@Override
				public java.util.Iterator<Statement> getConnected(Statement s) {
					List<Statement> connList = new ArrayList<Statement>();
					for (Iterator<Statement> predIt = pdg.getPredNodes(s); predIt.hasNext();) {
						connList.add(predIt.next());
					}
					
					for (Iterator<Statement> succIt = pdg.getSuccNodes(s); succIt.hasNext();) {
						connList.add(succIt.next());
					}
					
					return connList.iterator();
				}
			};
			
			// iterate over all nodes in the subgraph
			// and add them to the semantic block
			HashSet<Statement> sBStatementSet = new HashSet<Statement>();
			for (BFSIterator<Statement> bfsIt = bfsConnIter; bfsIt.hasNext();) {
				Statement s = bfsIt.next();

				statementSet.remove(s);
//				if (s.getKind() == Statement.Kind.NORMAL || s.getKind() == Statement.Kind.PHI
//						|| s.getKind() == Statement.Kind.PI || s.getKind() == Statement.Kind.CATCH) {
//					semanticBlockList.add(s);
//				}
				if (s.getKind().equals(Statement.Kind.NORMAL) 
						/*|| s.getKind().equals(Statement.Kind.EXC_RET_CALLER)*/
						|| s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)
						|| s.getKind().equals(Statement.Kind.PARAM_CALLER)) {
					semanticBlockList.add(s);
					sBStatementSet.add(s);
//					System.out.println("\tadd statement " + s.toString());
				}
				
				assert(!controlSet.contains(s));
				controlSet.add(s);
//				if (controlSet.contains(s)) {
//					System.err.println("control set already contains node");
//				} else {
//					controlSet.add(s);
//				}
			}
			
			// if there are missing edges between NORMAL invocation instruction
			// and its PARAM_CALLER nodes we have to manually add them, i.e. 
			// inserting the PARAM_CALLER nodes in the semantic block list.
			// By doing this we won't miss any constant values which are params
//			for (int i = 0; i < semanticBlockList.size(); i++) {
//				Statement s = semanticBlockList.get(i);
//				
//				// we only want the invocation instructions from NORMAL statements 
//				if (s instanceof StatementWithInstructionIndex 
//						&& (s.getKind().equals(Statement.Kind.NORMAL))) {
//					
//					StatementWithInstructionIndex swii = (StatementWithInstructionIndex) s;
//					SSAInstruction ssa = swii.getInstruction();
//					
//					// if the PARAM_CALLER statement has not already been inserted
//					// in the list we manually do it here
//					if (ssa instanceof SSAAbstractInvokeInstruction) {
//						Set<Statement> paramCallerSet = pdg.getCallerParamStatements((SSAAbstractInvokeInstruction) ssa);
//						for (Statement pc : paramCallerSet) {
//							if (!sBStatementSet.contains(pc)) {
//								semanticBlockList.add(pc);
//								sBStatementSet.add(pc);
//							}
//						}
//					}
//				}
//			}
			
			// add semantic block to the semantic 
			// blocks of the given pdg
			if (semanticBlockList.size() >= this.minSize) {
//				System.out.println("\tsemantic block list has size " + semanticBlockList.size());
				this.semanticBlocks.add(semanticBlockList);
				
//				// TODO remove or create assertion/exception
//				if (semanticBlockList.size() == 1) {
//					if (this.pdg.getPredNodes(semanticBlockList.get(0)).hasNext()) {
//						System.err.println("statement has pred nodes "
//								+ semanticBlockList.get(0).toString());
//					}
//					if (this.pdg.getSuccNodes(semanticBlockList.get(0)).hasNext()) {
//						System.err.println("statement has succ nodes "
//								+ semanticBlockList.get(0).toString());
//					}
//				}
			}
		}
	}
}
