package com.aisec.sa.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;

import com.aisec.sa.SemanticBlock;
import com.aisec.sa.apdg.APDG;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl.ConcreteJavaMethod;
import com.ibm.wala.examples.drivers.PDFSlice;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAAddressOfInstruction;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadIndirectInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAStoreIndirectInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphPrint;
import com.ibm.wala.util.graph.traverse.BFSIterator;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.StringStuff;

public class SAUtil {

	/**
	 * number of instruction types stored in the semantic vector
	 */
	//new
	public static final int INSTR_TYPE_COUNT = 38;

	public static final String UNKNOWN_TYPE = "UNKNOWN";

	/**
	 * instruction indices of the semantic vector
	 */
	public static final int INSTR_TYPE_ERROR = -1;

	public static final int INV_INSTR = 0;

	public static final int ADDR_OF = 1;

	public static final int ARR_LENGTH = 2;

	public static final int ARR_LOAD = 3;

	public static final int ARR_REF = 4;

	public static final int ARR_STORE = 5;

	public static final int BINARY_OP = 6;

	public static final int CHECK_CAST = 7;

	public static final int COMPARISON = 8;

	public static final int CONVERSION = 9;

	public static final int FIELD_ACC = 10;

	public static final int GET_CAUGHT_EXC = 11;

	public static final int GET = 12;

	public static final int INST_OF = 13;

	public static final int LOAD_IND = 14;

	public static final int LOAD_META_DATA = 15;

	public static final int NEW = 16;

	public static final int RETURN = 17;

	public static final int STORE_INDIRECT = 18;

	public static final int THROW = 19;

	public static final int UNARY_OP = 20;

	public static final int INV_SPECIAL = 21;

	public static final int ADD = 22;

	public static final int SUB = 23;

	public static final int MUL = 24;

	public static final int DIV = 25;

	public static final int REM = 26;

	public static final int AND = 27;

	public static final int OR = 28;

	public static final int XOR = 29;

	public static final int GOTO = 30;

	public static final int COND_BRANCH = 31;

	public static final int PUT = 32;

	// new
	public static final int INV_INTERFACE = 33;

	public static final int INV_STATIC = 34;

	public static final int INV_VIRTUAL = 35;

	public static final int SWITCH = 36;

	public static final int MONITOR = 37;

	/**
	 *
	 * @param pdg
	 * @param s
	 * @return
	 */
	public int getOutNodeDegree(PDG pdg, Statement s) {
		if (pdg == null || s == null)
			throw new IllegalArgumentException();

		// WARNING we had to manually remove an assertion
		// in @link{com.ibm.wala.ipa.slicer.PDG} to prevent an
		// UnimplementedError Exception thrown by this method
		int outNodeDegree = pdg.getSuccNodeCount(s);

		return outNodeDegree;
	}

	/**
	 * Get direct offsprings from a node
	 * @param pdg
	 * @param s
	 * @return
	 */
	public static List<Statement> getOutNodes(PDG pdg, Statement s) {
		if (pdg == null || s == null)
			throw new IllegalArgumentException();

		List<Statement> outNodes = new ArrayList<Statement>();
		for (Iterator<Statement> succIt = pdg.getSuccNodes(s); succIt.hasNext();) {
			outNodes.add(succIt.next());
		}

		return outNodes;
	}
	
	/**
	 * Get direct offsprings from a node
	 * @param sdg
	 * @param s
	 * @return
	 */
	public static List<Statement> getOutNodes(SDG sdg, Statement s) {
		if (sdg == null || s == null)
			throw new IllegalArgumentException();

		List<Statement> outNodes = new ArrayList<Statement>();
		for (Iterator<Statement> succIt = sdg.getSuccNodes(s); succIt.hasNext();) {
			outNodes.add(succIt.next());
		}

		return outNodes;
	}

	/**
	 * Get direct in-nodes from a node
	 * @param pdg
	 * @param s
	 * @return
	 */
	public static List<Statement> getInNodes(PDG pdg, Statement s) {
		if (pdg == null || s == null)
			return null;

		List<Statement> inNodes = new ArrayList<Statement>();
		for (Iterator<Statement> predIt = pdg.getPredNodes(s); predIt.hasNext();) {
			inNodes.add(predIt.next());
		}

		return inNodes;
	}
	
	/**
	 * Get direct in-nodes from a node
	 * @param sdg
	 * @param s
	 * @return
	 */
	public static List<Statement> getInNodes(SDG sdg, Statement s) {
		if (sdg == null || s == null)
			return null;

		List<Statement> inNodes = new ArrayList<Statement>();
		for (Iterator<Statement> predIt = sdg.getPredNodes(s); predIt.hasNext();) {
			inNodes.add(predIt.next());
		}

		return inNodes;
	}

	/**
	 * Get number of all reachable direct and indirect offsprings
	 * @param pdg
	 * @param s
	 */
	public static int getOffspringCount(PDG pdg, Statement s) {
		if (pdg == null || s == null)
			throw new IllegalArgumentException();

		// breadth-first search using only outgoing edges.
		// We follow the path and count all successor nodes
		int succCount = 0;
		BFSIterator<Statement> bfsIter = new BFSIterator<Statement>(pdg, s);
		for (BFSIterator<Statement> bfsIt = bfsIter; bfsIt.hasNext();) {
			Statement succ = bfsIt.next();
			succCount++;
		}

		return succCount;
	}

	/**
	 *
	 */
	public static int getParameterCount(SSAInstruction ssa) {
		if (ssa == null)
			return 0;

		if (SAUtil.isInvokeInstruction(ssa)) {
			SSAAbstractInvokeInstruction ii = (SSAAbstractInvokeInstruction) ssa;
			return ii.getNumberOfParameters();
		}

		return 0;
	}

	/**
	 *
	 * @param mr
	 * @return
	 */
	public static String getReadableSignature(MethodReference mr) {
		if (mr == null)
			return null;

		String methodName = mr.getDeclaringClass().getName().toString().substring(1).replace('/', '.') + "." + mr.getName();

		String params ="(";
		if (mr.getDescriptor().getNumberOfParameters() == 0) {
			params += ")";
		} else {
			for (TypeName tr : mr.getDescriptor().getParameters()) {
				params+= StringStuff.jvmToReadableType(tr.toString()) + ", ";
			}
			params = params.substring(0, params.lastIndexOf(", ")) + ")";
		}
		String signature = methodName + params;
//		System.out.println(signature);

		return signature;
	}

	public static Integer getLineNumber(Statement s) {
		Integer lineNumber = null;
		if (s == null)
			return lineNumber;

		Integer ii = null;
		if (s instanceof StatementWithInstructionIndex) {
			ii = ((StatementWithInstructionIndex) s).getInstructionIndex();
			try {
				lineNumber = ((ConcreteJavaMethod) s.getNode().getMethod()).getLineNumber(ii);
			} catch (Exception e) {
				lineNumber = -1;
			}

			return lineNumber;
		}

		return null;
	}

	/**
	 *
	 * @param ssa
	 * @return
	 * @throws Exception
	 */
	public static int resolveInstructionType(SSAInstruction ssa) throws Exception {
		if (ssa == null)
			throw new IllegalArgumentException();

		if (ssa instanceof SSAAbstractInvokeInstruction) {
			try {
				return SAUtil.resolveInvokeInstructionTarget((SSAAbstractInvokeInstruction) ssa);
			} catch (Exception e) {
				return SAUtil.INV_INSTR;
			}
		} else if (ssa instanceof SSAInvokeInstruction) {
			try {
				return SAUtil.resolveInvokeInstructionTarget((SSAAbstractInvokeInstruction) ssa);
			} catch (Exception e) {
				return SAUtil.INV_INSTR;
			}
		} else if (ssa instanceof SSABinaryOpInstruction) {
			try {
				return SAUtil.resolveBinaryOp((SSABinaryOpInstruction) ssa);
			} catch (Exception e) {
				return SAUtil.BINARY_OP;
			}
		} else if (ssa instanceof SSAAddressOfInstruction) {
			return SAUtil.ADDR_OF;
		} else if (ssa instanceof SSAArrayLengthInstruction) {
			return SAUtil.ARR_LENGTH;
		} else if (ssa instanceof SSAArrayLoadInstruction) {
			return SAUtil.ARR_LOAD;
		} else if (ssa instanceof SSAArrayReferenceInstruction) {
			return SAUtil.ARR_REF;
		} else if (ssa instanceof SSAArrayStoreInstruction) {
			return SAUtil.ARR_STORE;
		} else if (ssa instanceof SSACheckCastInstruction) {
			return SAUtil.CHECK_CAST;
		} else if (ssa instanceof SSAComparisonInstruction) {
			return SAUtil.COMPARISON;
		} else if (ssa instanceof SSAConversionInstruction) {
			return SAUtil.CONVERSION;
		} else if (ssa instanceof SSAFieldAccessInstruction) {
			return SAUtil.FIELD_ACC;
		} else if (ssa instanceof SSAPutInstruction ) {
			return SAUtil.PUT;
		} else if (ssa instanceof SSAGetCaughtExceptionInstruction) {
			return SAUtil.GET_CAUGHT_EXC;
		} else if (ssa instanceof SSAGetInstruction) {
					return SAUtil.GET;
		} else if (ssa instanceof SSAInstanceofInstruction) {
			return SAUtil.INST_OF;
		} else if (ssa instanceof SSALoadIndirectInstruction) {
			return SAUtil.LOAD_IND;
		} else if (ssa instanceof SSALoadMetadataInstruction) {
			return SAUtil.LOAD_META_DATA;
		} else if (ssa instanceof SSANewInstruction) {
			return SAUtil.NEW;
		} else if (ssa instanceof SSAReturnInstruction) {
			return SAUtil.RETURN;
		} else if (ssa instanceof SSAStoreIndirectInstruction) {
			return SAUtil.STORE_INDIRECT;
		} else if (ssa instanceof SSAThrowInstruction) {
			return SAUtil.THROW;
		} else if (ssa instanceof SSAUnaryOpInstruction) {
			return SAUtil.UNARY_OP;
		} else if (ssa instanceof SSAGotoInstruction) {
			return SAUtil.GOTO;
		} else if (ssa instanceof SSAConditionalBranchInstruction) {
			return SAUtil.COND_BRANCH;
		} else if (ssa instanceof SSASwitchInstruction) {
			return SAUtil.SWITCH;
		} else if (ssa instanceof SSAMonitorInstruction) {
			return SAUtil.MONITOR;
		} else {
			throw new Exception("Unknown instruction type for " + ssa.toString());
			//return -1;
		}
	}

	public static boolean isInvokeInstruction(SSAInstruction ssa) {
		if (ssa == null)
			return false;

		return (ssa instanceof SSAAbstractInvokeInstruction || ssa instanceof SSAInvokeInstruction);
	}

	public static boolean isNewInstruction(SSAInstruction ssa) {
		if (ssa == null)
			return false;

		return (ssa instanceof SSANewInstruction);
	}

	public static boolean isFieldAccess(SSAInstruction ssa) {
		if (ssa == null)
			return false;

		return (ssa instanceof SSAFieldAccessInstruction);
	}


	/**
	 *
	 * @param aii
	 * @return
	 * @throws Exception
	 */
	private static int resolveInvokeInstructionTarget(SSAAbstractInvokeInstruction aii) throws Exception {
		if (aii == null)
			throw new IllegalArgumentException();

		IInvokeInstruction.Dispatch dispatch = (IInvokeInstruction.Dispatch) aii.getCallSite().getInvocationCode();
		switch (dispatch) {
			case SPECIAL:
				return SAUtil.INV_SPECIAL;
			case INTERFACE:
				return SAUtil.INV_INTERFACE;
			case STATIC :
				return SAUtil.INV_STATIC;
			case VIRTUAL :
				return SAUtil.INV_VIRTUAL;
			default:
				return SAUtil.INV_INSTR;

		}
	}

	/**
	 *
	 * @param bo
	 * @return
	 */
	private static int resolveBinaryOp(SSABinaryOpInstruction bo) throws Exception{
		if (bo == null)
			throw new IllegalArgumentException();

		//  ADD, SUB, MUL, DIV, REM, AND, OR, XOR;
		IBinaryOpInstruction.Operator op = (IBinaryOpInstruction.Operator) bo.getOperator();
		switch (op) {
		case ADD:
			return SAUtil.ADD;
		case SUB:
			return SAUtil.SUB;
		case MUL:
			return SAUtil.MUL;
		case DIV:
			return SAUtil.DIV;
		case REM:
			return SAUtil.REM;
		case AND:
			return SAUtil.AND;
		case OR:
			return SAUtil.OR;
		case XOR:
			return SAUtil.XOR;
		default:
			return SAUtil.BINARY_OP;
		}
	}
	
	/**
	 * 
	 * @param pdg
	 * @return
	 */
	public static Map<SSAInstruction, Set<Statement>> buildInstructionToStatementSetMap(PDG pdg) {
		if (pdg == null)
			return null;

		Map<SSAInstruction, Set<Statement>> instrToStatementSetMap = new HashMap<SSAInstruction, Set<Statement>>();

		for(Iterator<Statement> pdgIt = pdg.iterator(); pdgIt.hasNext();) {
			Statement s = pdgIt.next();
			if (s.getKind().equals(Statement.Kind.NORMAL)
					|| s.getKind().equals(Statement.Kind.EXC_RET_CALLER)
					|| s.getKind().equals(Statement.Kind.PARAM_CALLER)
					|| s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)) {
				StatementWithInstructionIndex ns = (StatementWithInstructionIndex) s;
				SSAInstruction ssa = ns.getInstruction();
				
				Set<Statement> stmtSet = instrToStatementSetMap.get(ssa);
				if (stmtSet == null)
					stmtSet = new HashSet<Statement>();
				
				stmtSet.add(s);
				instrToStatementSetMap.put(ssa, stmtSet);
//				System.out.println("Instr " + ssa);
//				System.out.println("has stmts " + stmtSet);
				
			} else {
//				System.out.println("ignoring non normal statement " + s.toString());
			}
		}
		
		return instrToStatementSetMap;
	}

	/**
	 *
	 * @param pdg
	 */
	public static Map<Statement, SSAInstruction> buildStatementToInstructionMap(PDG pdg) {
		if (pdg == null)
			throw new IllegalArgumentException();

		Map<Statement, SSAInstruction> statementToInstrMap = new HashMap<Statement, SSAInstruction>();

		for(Iterator<Statement> pdgIt = pdg.iterator(); pdgIt.hasNext();) {
			Statement s = pdgIt.next();
			if (s.getKind().equals(Statement.Kind.NORMAL)
					|| s.getKind().equals(Statement.Kind.EXC_RET_CALLER)
					|| s.getKind().equals(Statement.Kind.PARAM_CALLER)
					|| s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)) {
				StatementWithInstructionIndex ns = (StatementWithInstructionIndex) s;
				statementToInstrMap.put(s, ns.getInstruction());
			} else {
//				System.out.println("ignoring non normal statement " + s.toString());
			}
		}

		return statementToInstrMap;
	}
	
	/**
	 * 
	 * @param statementToInstrMap
	 * @param s
	 */
	public static void addToStatementToInstructionMap(Map<Statement, SSAInstruction> statementToInstrMap, Statement s) {
		if (statementToInstrMap == null || s == null)
			return;
		
		if (statementToInstrMap.containsKey(s))
			return;
		
		if (s.getKind().equals(Statement.Kind.NORMAL)
				|| s.getKind().equals(Statement.Kind.EXC_RET_CALLER)
				|| s.getKind().equals(Statement.Kind.PARAM_CALLER)
				|| s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)) {
			StatementWithInstructionIndex ns = (StatementWithInstructionIndex) s;
			statementToInstrMap.put(s, ns.getInstruction());
		}
	}
	
	/**
	 * 
	 * @param pdg
	 * @param instrToStatementSetMap
	 * @param ssa
	 */
	public static void addToInstructionToStatementSetMap(PDG pdg, Map<SSAInstruction, Set<Statement>> instrToStatementSetMap, Statement stmt) {
		if (pdg ==  null || instrToStatementSetMap == null || stmt == null)
			return;
		
		SSAInstruction ssa = null;
		if (stmt.getKind().equals(Statement.Kind.NORMAL)
				|| stmt.getKind().equals(Statement.Kind.EXC_RET_CALLER)
				|| stmt.getKind().equals(Statement.Kind.PARAM_CALLER)
				|| stmt.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)) {
			StatementWithInstructionIndex ns = (StatementWithInstructionIndex) stmt;
			ssa = ns.getInstruction();
		} else 
			return;
		
		for(Iterator<Statement> pdgIt = pdg.iterator(); pdgIt.hasNext();) {
			Statement s = pdgIt.next();
			if (s.getKind().equals(Statement.Kind.NORMAL)
					|| s.getKind().equals(Statement.Kind.EXC_RET_CALLER)
					|| s.getKind().equals(Statement.Kind.PARAM_CALLER)
					|| s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)) {
				StatementWithInstructionIndex ns = (StatementWithInstructionIndex) s;
				SSAInstruction tmpSsa = ns.getInstruction();
				if (!ssa.equals(tmpSsa)) // only add statements belonging to ssa
					continue;
				
				Set<Statement> stmtSet = instrToStatementSetMap.get(ssa);
				if (stmtSet == null)
					stmtSet = new HashSet<Statement>();
				
				stmtSet.add(s);
				instrToStatementSetMap.put(ssa, stmtSet);
			} else {
//				System.out.println("ignoring non normal statement " + s.toString());
			}
		}
	}
	
	/**
	 * No check for leafs
	 * @param s
	 * @param consideredStatements
	 */
	private static void addToConsideredStatements(Statement s, List<Statement> consideredStatements) {
		if (s == null || consideredStatements == null)
			return;
		if (consideredStatements.contains(s)) {
			return;
		}
		if (!SAUtil.isConsideredStatement(s)) {
			return;
		}
		
//		List<Statement> inNodes = APDG.getInNodes(this.sdg.getPDG(s.getNode()), s, true);
//		List<Statement> outNodes = APDG.getOutNodes(this.sdg.getPDG(s.getNode()), s, true);
//		if (inNodes.size() == 0 && outNodes.size() == 0 && NO_ISOLATED_NODES) // delete isolated statements
//			return;
		
		consideredStatements.add(s);
		
	}
	
	/**
	 * 
	 * @param sdg
	 * @param s
	 * @param consideredStatements 
	 */
	public static void addEdgesToStatementsWithSameInstr(Statement s, List<Statement> consideredStatements, Map<SSAInstruction, Set<Statement>> instrToStmtMap, Map<Statement, Set<Statement>> stmtToInNodeSetMap, Map<Statement, Set<Statement>> stmtToOutNodeSetMap) {
		if (s == null || instrToStmtMap == null || stmtToInNodeSetMap == null || stmtToOutNodeSetMap == null)
			return;
		
		if (!s.getKind().equals(Kind.NORMAL)) { // model flow from NORMAL to all other statements with same instruction
			return;
		}
		
		StatementWithInstructionIndex ns = (StatementWithInstructionIndex) s;
		Set<Statement> stmtSet = instrToStmtMap.get(ns.getInstruction());
		
		stmtSet.remove(s); // s is not an in or out node of s, so remove
		if (stmtSet == null || stmtSet.isEmpty()) { // if s is only statement with instruction ns.getInstruction, there is nothing to do
			return;
		}
		
		// add out nodes to s
		Set<Statement> outNodes = stmtToOutNodeSetMap.get(s);
		if (outNodes == null)
			outNodes = new HashSet<Statement>();
		
		outNodes.addAll(stmtSet);
		stmtToOutNodeSetMap.put(s, outNodes);
		
		// add s and outNodes to considered stmts, as they are no leafs
		SAUtil.addToConsideredStatements(s, consideredStatements);
		for (Statement outNode : outNodes) {
			SAUtil.addToConsideredStatements(outNode, consideredStatements);
		}
		
		// add s as in node to out nodes of s
		for (Statement outNode : stmtSet) {
			Set<Statement> inNodes = stmtToInNodeSetMap.get(outNode);
			if (inNodes == null)
				inNodes = new HashSet<Statement>();
			inNodes.add(s);
			stmtToInNodeSetMap.put(outNode, inNodes);
		}
	}
	
	/**
	 * @param s is considered statement iff NORMAL statement.
	 * @param s
	 * @return
	 */
	public static boolean isConsideredStatement(Statement s) {
		if (s == null)
			return false;

		return (s.getKind().equals(Statement.Kind.NORMAL) ||
				s.getKind().equals(Statement.Kind.PARAM_CALLER) || 
				s.getKind().equals(Statement.Kind.PARAM_CALLEE) || // not a StatementWithInstructionIndex
				s.getKind().equals(Statement.Kind.EXC_RET_CALLER) ||
				s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER) /*||
				s.getKind().equals(Statement.Kind.NORMAL_RET_CALLEE)*/); // not a StatementWithInstructionIndex
	}
	
	/**
	 * @param s is heap statement iff HEAP_* statement.
	 * @param s
	 * @return
	 */
	public static boolean isHeapStatement(Statement s) {
		if (s == null)
			return false;

		return (s.getKind().equals(Kind.HEAP_PARAM_CALLEE)
				|| s.getKind().equals(Kind.HEAP_PARAM_CALLER)
				|| s.getKind().equals(Kind.HEAP_RET_CALLEE)
				|| s.getKind().equals(Kind.HEAP_RET_CALLER));
	}

	public static void iteratePdg(CGNode n, SDG sdg) {
		if (n == null || sdg == null)
			throw new IllegalArgumentException();

		PDG pdg = sdg.getPDG(n);

		for (Iterator<Statement> pdgIt = pdg.iterator(); pdgIt.hasNext();) {
			Statement s = pdgIt.next();
			System.out.println("node number " + pdg.getNumber(s));
			System.out.println(s.toString());

			for (Iterator<Statement> predNodes = pdg.getPredNodes(s); predNodes
					.hasNext();) {
				System.out.println("has predecessor "
						+ pdg.getNumber(predNodes.next()));
			}

			for (Iterator<Statement> succNodes = pdg.getSuccNodes(s); succNodes
					.hasNext();) {
				System.out.println("has successor "
						+ pdg.getNumber(succNodes.next()));
			}
		}
	}

	public static void iterateSSAInstructions(CGNode n, SDG sdg) {
		if (n == null || sdg == null)
			throw new IllegalArgumentException();

		PDG pdg = sdg.getPDG(n);

		IR ir = n.getIR();
		Map<SSAInstruction,java.lang.Integer> ssaMap = PDG.computeInstructionIndices(ir);
		for (Iterator<SSAInstruction> it = ssaMap.keySet().iterator(); it.hasNext();) {
			SSAInstruction ssa = it.next();
			System.out.println("ssa " + ssa.toString());

			try {
				TypeInference ti = TypeInference.make(ir, true);
				 for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
						TypeAbstraction type = ti.getType(ssa.getDef(i));
						if (type != null) {
							TypeReference typeRef = type.getTypeReference();
							if (typeRef != null) {
								System.out.println("\ttype " + typeRef.getName());
							}
						}
					}
			} catch (Exception e) {
				System.out.println("unable to infere type");
			}

			Statement s = pdg.ssaInstruction2Statement(n, ssa, ssaMap, ir);
			System.out.println("ssa 2 statement " + s.toString());
			System.out.println("\tkind " + s.getKind().toString());
			System.out.println("\tnode " + s.getNode());

//			System.out.println("\tbasic block");
//			ISSABasicBlock bb = ir.getBasicBlockForInstruction(ssa);
//			for (int i = bb.getFirstInstructionIndex(); i >= 0 && i < bb.getLastInstructionIndex(); i++) {
//				SSAInstruction tmpSsa = ir.getInstructions()[i];
//				if (tmpSsa != null)
//					System.out.println("\t\t " + tmpSsa.toString());
//			}
		}
	}

	public static void iterateBasicBlocks(CGNode n) {
		if (n == null)
			throw new IllegalArgumentException();

		IR ir = n.getIR();
		for (Iterator<ISSABasicBlock> it = ir.getControlFlowGraph().iterator(); it.hasNext();) {
			ISSABasicBlock bb = it.next();
			System.out.println("basic block " + bb.toString());
			for (Iterator<SSAInstruction> itSsa = bb.iterator(); itSsa.hasNext();) {
				SSAInstruction ssa = itSsa.next();
				System.out.println("\t" + ssa.toString());
			}
		}
	}

	public static void iterateSemanticBlocks(SemanticBlock sb) {
		for (List<Statement> list : sb.getSemanticBlocks()) {
			System.out.println("iterating new semantic block");
			for (Statement s : list) {
				System.out.println("\t" + s.toString());
			}
		}
	}

	public static CGNode findCGForMethod(CallGraph cg, String name, Descriptor d) {
		if (cg == null)
			throw new IllegalArgumentException();

		Atom methName = Atom.findOrCreateUnicodeAtom(name);
		for (Iterator<? extends CGNode> it = cg.getSuccNodes(cg.getFakeRootNode()); it.hasNext();) {
	        CGNode n = it.next();
	        if (d == null && n.getMethod().getName().equals(methName)) {
	        	return n;
	        } else if (n.getMethod().getName().equals(methName) && n.getMethod().getDescriptor().equals(d)) {
	        	return n;
	        }
		}

		for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
			CGNode n = it.next();
			if (d == null && n.getMethod().getName().equals(methName)) {
				return n;
			} else if (n.getMethod().getName().equals(methName) && n.getMethod().getDescriptor().equals(d)) {
				return n;
			}
		}
		Assertions.UNREACHABLE("failed to find " + name);

		return null;
	}

	public static CGNode findCGNodeIterOverAllNodes(CallGraph cg, String name,
			Descriptor d) {
		Atom methName = Atom.findOrCreateUnicodeAtom(name);
		for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
			CGNode n = it.next();
			System.out.println(n.getMethod().getName());
			if (d == null && n.getMethod().getName().equals(methName)) {
				return n;
			} else if (n.getMethod().getName().equals(methName)
					&& n.getMethod().getDescriptor().equals(d)) {
				return n;
			}
		}
		Assertions.UNREACHABLE("failed to find " + name);

		return null;
	}

//	public static void visitCUFromPPA(String path) {
//		File javaFile = new File(path);
//	    // CompilationUnit contains the AST of the partial program
//	    // PPAOptions is a wrapper and contains various configuration options:
//	    // most options are still not implemented, so the default options are
//	    // usually fine.
//	    CompilationUnit cu = PPAUtil.getCU(javaFile, new PPAOptions());
//	    PPATypeVisitor visitor = new PPATypeVisitor(System.out);
//	    cu.accept(visitor);
//	}

	public static HashMap<String, Integer> buildStringToIndexMap(Set<String> keySet) {
		if (keySet == null)
			throw new IllegalArgumentException();

		LinkedList<String> sortedMethodList = new LinkedList<String>();
		for (String methodName : keySet) {
			sortedMethodList.add(methodName);
		}
		Collections.sort(sortedMethodList);

		HashMap<String, Integer> stringToIndexMap = new HashMap<String, Integer>();
		for (int i = 0; i < sortedMethodList.size(); i++) {
			stringToIndexMap.put(sortedMethodList.get(i), i);
		}

		return stringToIndexMap;
	}

	public static RealVector addEmbeddings(List<RealVector> embeddingList, int embeddingDimension) {
		if (embeddingList == null || embeddingList.size() == 0) {
			return null;
		}

		RealVector summedUpEmbedding = new ArrayRealVector(embeddingDimension);
		for (RealVector embedding : embeddingList) {
			summedUpEmbedding = summedUpEmbedding.add(embedding);
		}

		return summedUpEmbedding;
	}

	public static Integer addIntegerValues(Set<Integer> numbers) {
		if (numbers == null) {
			return null;
		}

		Integer sum = 0;
		for (Integer number : numbers) {
			sum += number;
		}

		return sum;
	}

	public static boolean containsUnknownType(String signature) {
		if (signature == null)
			return false;

		return signature.contains("UNKNOWN");
	}

	public static boolean hasUnkownPackage(MethodReference mr) {
		if (mr == null)
			return false;
		String pqn = mr.getDeclaringClass().getName().getClassName().toString();
		String fqn = StringStuff.jvmToReadableType(mr.getDeclaringClass().getName().toString());
//		System.out.println("PQN: " + pqn);
//		System.out.println("FQN: " + fqn);
//		System.out.println(pqn.trim().equals(fqn.trim()));
		return pqn.trim().equals(fqn.trim());
	}
	
	public static void printAllUsedValues(SSAInstruction ssa, CGNode n) {
		if (ssa == null || n == null)
			return;

		SymbolTable st = n.getIR().getSymbolTable();
		Logger.log("Used values: " );
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			int vn = ssa.getUse(i);
			Logger.log("\t" + st.getValueString(vn));
		}
	}
	public static void printAllDefValues(SSAInstruction ssa, CGNode n) {
		if (ssa == null || n == null)
			return;

		SymbolTable st = n.getIR().getSymbolTable();
		Logger.log("Def values: " );
		for (int i = 0; i < ssa.getNumberOfDefs(); i++) {
			int vn = ssa.getDef(i);
			Logger.log("\t" + st.getValueString(vn));
		}
	}
	
	public static Set<Statement> jumpHeapAndGetConsideredOutNodesForStatement(final Collection<Statement> slice, SDG sdg, Statement s) {
		if (slice == null || sdg == null || s == null)
			return null;
		
		if (!SAUtil.isConsideredStatement(s)) {
			return null;
		}
		
		Filter<Statement> consideredStmtsFilter = new Filter<Statement>() {
			public boolean accepts(Statement o) {
				boolean accept = SAUtil.isConsideredStatement(o);
				
				return accept;
			}
		};
		
		Graph<Statement> prunedGraph = PDFSlice.pruneSDG(sdg, slice);
		Set<Statement> consideredOffspringsSet = new HashSet<Statement>();
		
		for (Iterator<Statement> outNodes = prunedGraph.getSuccNodes(s); outNodes.hasNext();) {
			Statement outNode = outNodes.next();
			if (SAUtil.isHeapStatement(outNode)) {
				BFSPathFinder<Statement> bfsPathFinder = new BFSPathFinder<Statement>(prunedGraph, outNode, consideredStmtsFilter); //TODO returns path only to
				// first found considered stmt, but we need all reachable considered stmts
				List<Statement> path = bfsPathFinder.find();
				for (Statement pathNode : path) {
					if (SAUtil.isConsideredStatement(pathNode))
						consideredOffspringsSet.add(pathNode);
				}
			}
		}
		
		if (consideredOffspringsSet.isEmpty()) {
//			Logger.log("INFO: No heap connected statement found");
			return null;
		}
		
		return consideredOffspringsSet;
	}
}






















































