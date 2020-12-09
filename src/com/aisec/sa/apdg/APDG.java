package com.aisec.sa.apdg;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkDOT;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.aisec.sa.io.Label;
import com.aisec.sa.util.CallableSlicer;
import com.aisec.sa.util.Logger;
import com.aisec.sa.util.SAUtil;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.GraphPrint;
import com.ibm.wala.util.graph.traverse.BFSIterator;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.StringStuff;

/**
 * Attributed program dependency graph (APDG)
 *
 * @author fischer
 *
 */
public class APDG {

	private static final int CONSTANT_EMBEDDING_DIMENSION = 2;
	private static final int SIGNATURE_EMBEDDING_DIMENSION = 64;
	private static final int IS_PARAM_CALLEE_INDEX = SAUtil.INSTR_TYPE_COUNT;
	private static final int IS_PARAM_CALLER_INDEX = IS_PARAM_CALLEE_INDEX + 1;
	private static final int IS_EXC_RET_CALLER_INDEX = IS_PARAM_CALLER_INDEX + 1;
	private static final int STRING_CONSTANT_COUNT_INDEX = IS_EXC_RET_CALLER_INDEX + 1;
	private static final int NUMERIC_CONSTANT_COUNT_INDEX = STRING_CONSTANT_COUNT_INDEX + 1;
	private static final int PARAM_COUNT_INDEX = NUMERIC_CONSTANT_COUNT_INDEX + 1;
	private static final int NUMERIC_VALUE_INDEX = PARAM_COUNT_INDEX + 1;
	private static final int OFFSPRING_INDEX = NUMERIC_VALUE_INDEX + 1;
	private static final int NODE_DEGREE_INDEX = OFFSPRING_INDEX + 1;
//	private static final int IN_NODE_DEGREE_INDEX = OFFSPRING_INDEX + 1;
//	private static final int OUT_NODE_DEGREE_INDEX = IN_NODE_DEGREE_INDEX + 1;
	private static final int FEATURE_DIM = NODE_DEGREE_INDEX + 1; // FEATURE_DIM = 2(c) + 64(s) + 38(i) + 8(f) = 112

	private SDG sdg;
	private final CGNode n;
	private PDG pdg;
	
	private org.graphstream.graph.Graph pdgStream;
	
	private final Map<SSAInstruction, Set<Statement>> instrToStatementMap;

	private final List<Statement> consideredStatements;
	private final Map<Statement, SSAInstruction> statementToInstrMap;
	
	private final HashMap<Statement, Set<Statement>> statementToInNodeMap;
	private final HashMap<Statement, Set<Statement>> statementToOutNodeMap;
	
	private final HashMap<Statement, Set<Statement>> seedToInNodesMap;
	private final HashMap<Statement, Statement> inNodeToSeedMap;
	
	private Map<String, RealVector> constantEmbeddingMap;
	private Map<String, RealVector> signatureEmbeddingMap;
	
	private Map<String, Map<Integer, Integer>> annotations;

	private final List<String> signatures;

	private final String methodeName;
	private final String className;
	private int nodeCount;
//	private final int internalNodeCount;
	private String project;
	private String representation;

	private final List<RealVector> apdg;
	private final List<Slice> sliceLabels;
	private final Set<Integer> nodeLabels;

	// TODO apply flag
	public static boolean NO_ISOLATED_NODES = true;
	public static boolean IGNORE_LABELS = false;
	
	public static Label.Type label = Label.Type.NONE ;

	/**
	 * Constructor for the annotated PDG
	 * @param sdg
	 * @param n
	 * @param pdg
	 * @param constantEmbeddingMap
	 * @param signatureEmbeddingMap
	 * @param signatureFileAbsPath
	 * @param annotations
	 */
	public APDG(SDG sdg, CGNode n, PDG pdg, Map<String, RealVector> constantEmbeddingMap, Map<String, RealVector> signatureEmbeddingMap, String signatureFileAbsPath, Map<String, Map<Integer,Integer>> annotations) {
		this(sdg, n, pdg, constantEmbeddingMap, signatureEmbeddingMap, signatureFileAbsPath);
		if (annotations == null)
			throw new IllegalArgumentException();
		
		this.annotations = annotations;
	}
	/**
	 * 
	 * @param sdg
	 * @param n
	 * @param pdg
	 * @param constantEmbeddingMap
	 * @param signatureEmbeddingMap
	 * @param signatureFileAbsPath
	 */
	public APDG(SDG sdg, CGNode n, PDG pdg, Map<String, RealVector> constantEmbeddingMap, Map<String, RealVector> signatureEmbeddingMap, String signatureFileAbsPath) {
		if (n == null || pdg == null) // TODO null check @sdg 
			throw new IllegalArgumentException(); // TODO return null

		this.sdg = sdg;
		this.n = n;
		this.pdg = pdg;
		 
		this.pdgStream = new SingleGraph("pdg");

//		Logger.log("pdg start " + n.getMethod().getSignature() + "----------------------------------\n");
//		Logger.log(GraphPrint.genericToString(this.pdg));
//		Logger.log("\n");
		
//		Logger.log("----------------------------------\n");
//		Logger.log(GraphPrint.genericToString(this.sdg));
//		Logger.log("\n");
		
		// Allow isolated nodes iff overrides
		if (APDG.label.equals(Label.Type.HNV_OR) || APDG.label.equals(Label.Type.TM)) {
			APDG.NO_ISOLATED_NODES = false;
		}
			
		// store all considered stmts for the apdg
		this.consideredStatements = APDG.getConsideredStatements(pdg);
		
		// map all instr of the pdg to its stmts
		// all stmts are considered stmts
		this.instrToStatementMap = SAUtil.buildInstructionToStatementSetMap(this.pdg);
		// map all stmts to its instr
		// all stmts are considered stmts
		this.statementToInstrMap = SAUtil.buildStatementToInstructionMap(this.pdg);

		// store constant embeddings
		this.constantEmbeddingMap = new HashMap<String, RealVector>();
		if (constantEmbeddingMap != null)
			this.constantEmbeddingMap = constantEmbeddingMap;
		// store signature embeddings
		this.signatureEmbeddingMap = new HashMap<String, RealVector>();
		if (signatureEmbeddingMap != null)
			this.signatureEmbeddingMap = signatureEmbeddingMap;

		// load signatures detected by Java Baker
		this.signatures = APDG.parseSignatures(signatureFileAbsPath); // TODO remove
		
		this.statementToInNodeMap = new HashMap<Statement, Set<Statement>>();
		this.statementToOutNodeMap = new HashMap<Statement, Set<Statement>>();
		
		this.seedToInNodesMap = new HashMap<Statement, Set<Statement> >(); // TODO remove
		this.inNodeToSeedMap = new HashMap<Statement, Statement>(); // TODO remove
		
		// add bw slice to graph for labeled stmts
		if (!APDG.IGNORE_LABELS) {
			this.addBackwardSlicesToGraph(this.pdg);
		}	
		
		// add missing edges for return and paramater passing
		this.addMissingEdges(this.pdg);
		
		this.methodeName = n.getMethod().getSignature().toString().replace(" ", "");
		this.className = n.getMethod().getDeclaringClass().getName().getClassName().toString().replace(" ", "");
//		this.nodeCount = APDG.getNodeCount(this.consideredStatements);
		this.nodeCount = 0;
//		this.internalNodeCount = APDG.getInternalNodeCount(this.pdg, this.consideredStatements);

		this.apdg = new LinkedList<RealVector>();
		this.sliceLabels = new ArrayList<Slice>();
		this.nodeLabels = new HashSet<Integer>();

		this.buildAPDGFromPDG(); // FIXME last statement in constructor, therefore not static using class fields
	}

	private static ArrayList<String> parseSignatures(String signatureFileAbsPath) {
		if (signatureFileAbsPath == null)
			return null;

		JSONParser parser = new JSONParser();
		try {
			Object obj = parser.parse(new FileReader(signatureFileAbsPath));
			JSONObject jsonObject = (JSONObject) obj;
			ArrayList<String> signatures = (ArrayList<String>) jsonObject.get("methods");
			return signatures;
		} catch (Exception e) {
//			e.printStackTrace();
//			System.out.println("Could not parse json");
			return null;
		}
	}

	private String getSignatureCandidate(String methodName, MethodReference mr) {
		if (methodName == null)
			return null;

		if (this.signatures != null) {
			for (String signature : this.signatures) {
				if (signature.contains(methodName)) {
					return signature;
				}
			}
		}

		Atom pnAtom = mr.getDeclaringClass().getName().getPackage();
		String pn = null;
		if (pnAtom != null)
			pn = StringStuff.slashToDot(pnAtom.toString());
		String cn = mr.getDeclaringClass().getName().getClassName().toString();
		String mn = mr.getName().toString();

		String pqmn = null;
		if (pn == null || pn.contains("UNKNOWNP"))
			pqmn = "." + cn + "." + mn; // TODO test the first dot
		else
			pqmn = pn + "." + cn + "." + mn;

//		Logger.log("pqmn+++ " + pqmn);

		for (String signature :this.signatureEmbeddingMap.keySet()) {
			if (signature.contains(pqmn + "(")) {
				return signature;
			}
		}

		return null;
	}
	
	private String getCNSignatureCandidate(String methodName, MethodReference mr) {
		if (methodName == null)
			return null;

		if (this.signatures != null) {
			for (String signature : this.signatures) {
				if (signature.contains(methodName)) {
					return signature;
				}
			}
		}


		String mn = mr.getName().toString();
//		Logger.log(mn);

		for (String signature :this.signatureEmbeddingMap.keySet()) {
			if (signature.contains(mn + "(")) {
				return signature;
			}
		}

		return null;
	}

	private static ArrayList<Integer> initFeatureVector() {
		ArrayList<Integer> featureVector =
			new ArrayList<Integer>(Arrays.asList(new Integer[FEATURE_DIM]));
		assert featureVector.size() != 0;
		Collections.fill(featureVector, 0);

		return featureVector;
	}

	/**
	 * @param s is considered statement iff NORMAL statement.
	 * @param s
	 * @return
	 */
	private static boolean isConsideredStatement(Statement s) {
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
	 * Lists all considered statements of the APDG.
	 * Stmts are considered if they are a specific KIND
	 * and if they are NO_ISOLATED_NODES
	 * @param pdg
	 * @return
	 */
	private static List<Statement> getConsideredStatements(PDG pdg) {
		if (pdg == null)
			throw new IllegalArgumentException(); // TODO return null

		List<Statement> consideredStatements = new ArrayList<Statement>(); // TODO make set

		for (Iterator<Statement> pdgIt = pdg.iterator(); pdgIt.hasNext();) {
			Statement s = pdgIt.next();

			if (!APDG.isConsideredStatement(s))
				continue;

			List<Statement> inNodes = APDG.getInNodes(pdg, s, true);
			List<Statement> outNodes = APDG.getOutNodes(pdg, s, true);
			if (inNodes.size() == 0 && outNodes.size() == 0 && NO_ISOLATED_NODES) // delete isolated statements
				continue;

			consideredStatements.add(s);
		}

		return consideredStatements;
	}
	
	/**
	 * TODO This method does not check for isolated stmts !!!
	 * This is inconsistent with the rest of the code.
	 */
	private void addToConsideredStatements(Statement s) {
		if (s == null)
			return;
		
		if (!APDG.isConsideredStatement(s) || this.consideredStatements.contains(s))
			return;
		
//		List<Statement> inNodes = APDG.getInNodes(this.sdg.getPDG(s.getNode()), s, true);
//		List<Statement> outNodes = APDG.getOutNodes(this.sdg.getPDG(s.getNode()), s, true);
//		if (inNodes.size() == 0 && outNodes.size() == 0 && NO_ISOLATED_NODES) // delete isolated statements
//			return;
		
		this.consideredStatements.add(s);
		
	}
	
	/**
	 *
	 */
	private static boolean isInConsideredStatements(Statement s, List<Statement> consideredStatements) {
		if (s == null || consideredStatements == null) {
			return false;
		}

		Set<Statement> consideredStatementsSet = new HashSet<Statement>(consideredStatements);

		return consideredStatementsSet.contains(s);
	}
	
	/**
	 * 
	 * @param seed
	 * @param s
	 */
	private void addToSeedToInNodesMap(Statement seed, Statement s) {
		if (seed == null || s == null)
			return;
		
		Set<Statement> InNodes = null;
		if (!this.seedToInNodesMap.containsKey(seed)) {
			InNodes = new HashSet<Statement>();
		} else {
			InNodes = this.seedToInNodesMap.get(seed);
		}
		InNodes.add(s);
		this.seedToInNodesMap.put(seed, InNodes);		
	}
	
	/**
	 * 
	 */
	private void addToinNodeToSeedMap(Statement inNode, Statement seed) {
		if (seed == null || inNode == null)
			return;
		
		if (!this.inNodeToSeedMap.containsKey(inNode)) {
			this.inNodeToSeedMap.put(inNode, seed);
		}
	}		
		
	/**
	 * Adds considered stmts within the bw slice of @seed
	 * to the graph. All stms should belong to a considered KIND
	 * and should not be isolated.
	 * 
	 * @param slice
	 * @param seed
	 */
	private void addBackwardSlicesToGraph(PDG pgg) {
		if (pdg == null)
			return;

		for (Iterator<Statement> pdgIt = pdg.iterator(); pdgIt.hasNext();) {
			Statement seed = pdgIt.next();

			if (Label.isLabeledForBackwardSlice(this.getMethodSignature(seed), seed.getKind(), APDG.label)) {
				Collection<Statement> slice = this.getBackwardSlice(this.sdg, seed/*, false*/);
//				Logger.log(slice);
				
				if (slice == null)
					continue;
				
//				if (!slice.contains(seed))
//					slice.remove(seed);

				for (Statement ss : slice) {
					Set<Statement> consideredOutNodesJumpOverHeap = SAUtil.jumpHeapAndGetConsideredOutNodesForStatement(slice, this.sdg, ss);
					if (consideredOutNodesJumpOverHeap != null) {
							this.statementToOutNodeMap.put(ss, consideredOutNodesJumpOverHeap);
							for (Statement outNode : consideredOutNodesJumpOverHeap) {
								Set<Statement> inNodes = this.statementToInNodeMap.get(outNode);
								if (inNodes == null)
									inNodes = new HashSet<Statement>();
								inNodes.add(ss);
								this.statementToInNodeMap.put(outNode, inNodes);
							}
					}
					
					this.addToConsideredStatements(ss); // FIXME we might lose some stmts here
//					Logger.log("\tIs considered stmt "  + this.consideredStatements.contains(ss));
					
					SAUtil.addToStatementToInstructionMap(this.statementToInstrMap, ss);
					SAUtil.addToInstructionToStatementSetMap(this.sdg.getPDG(ss.getNode()), this.instrToStatementMap, ss);
//					Logger.log("\tIn instr map "  + this.statementToInstrMap.containsKey(ss));
					
					List<Statement> outNodes = APDG.getOutNodes(this.sdg.getPDG(ss.getNode()), ss, true);
					for (Statement outNode : outNodes) {
//						Logger.log("\tAdded outNode: " + outNode);
						this.addToConsideredStatements(outNode);
						SAUtil.addToStatementToInstructionMap(this.statementToInstrMap, outNode);
						SAUtil.addToInstructionToStatementSetMap(this.sdg.getPDG(outNode.getNode()), this.instrToStatementMap, outNode);
					}
				}
			}
		}
	}
	
	/**
	 * Flow of returns and parameter passing of instructions belonging to NORMAL stmts
	 * is sometimes not present in the @pdg!? Therefore, we model it here using maps.
	 * 
	 * @param pdg
	 */
	private void addMissingEdges(PDG pdg) {
		for (Iterator<Statement> pdgIter = pdg.iterator(); pdgIter.hasNext();) {
			Statement s = pdgIter.next();
			SAUtil.addEdgesToStatementsWithSameInstr(s, this.consideredStatements, this.instrToStatementMap, this.statementToInNodeMap, this.statementToOutNodeMap);
		}
	}
	
//	/**
//	 * 
//	 */
//	private void conntectLeafsToSeed(Collection<Statement> bwSlice, Statement seed) {
//		if (bwSlice == null || seed == null)
//			return;
//		
//		if (bwSlice.contains(seed))
//			bwSlice.remove(seed);
//		
//		boolean allConnected = false;
//		
//		List<Statement> leafs = new ArrayList<Statement>(bwSlice);
//		while (!allConnected) {
//			for (Statement s : leafs) {
//				List<Statement> outNodes = APDG.getOutNodes(this.sdg.getPDG(s.getNode()), s, true);
//				for (Statement outNode : outNodes) {
//					if (bwSlice.contains(outNode) || outNode.equals(seed)) {
//						leafs.remove(s);
//						break;
//					}
//				}
//			}
//		}
//	}

	/**
	 * Node count of pruned APDG (NORMAL statements only).
	 *
	 * @param statements
	 * @return
	 */
	private static int getNodeCount(List<Statement> statements) {
		if (statements == null)
			throw new IllegalArgumentException(); // TODO return null

		int verticeCount = 0;
		for (Statement s : statements) {

			if (!APDG.isConsideredStatement(s))
				continue;

			verticeCount++;
		}

		return verticeCount;
	}

	/**
	 * Count how many statements have outgoing edges
	 *
	 * @param statements
	 * @return
	 */
	private static int getInternalNodeCount(PDG pdg, List<Statement> statements) {
		if (statements == null)
			return 0;

		int internalNodeCount = 0;
		for (Statement s : statements) {
			List<Statement> outNodes = APDG.getOutNodes(pdg, s, true);
			if (outNodes != null && outNodes.size() > 0)
				internalNodeCount++;
		}

		return internalNodeCount;
	}

	/**
	 * Get number of all reachable direct and indirect ancestors which are
	 * considered statements
	 * @param pdg
	 * @param s
	 */
	private static int getAncestorCount(final PDG pdg, Statement s, Set<Statement> interproceduralInNodesSet) {
		if (pdg == null || s == null)
			return 0;

		BFSIterator<Statement> bfsIter = new BFSIterator<Statement>(pdg, s) {

			/**
			 * Consider only in nodes that are considered statements
			 */
			@Override
			public java.util.Iterator<Statement> getConnected(Statement s) {
				List<Statement> connList = new ArrayList<Statement>();

				for (Iterator<Statement> predNodes = pdg.getPredNodes(s); predNodes.hasNext();) {
					Statement pred = predNodes.next();
					if (APDG.isConsideredStatement(pred)) {
						connList.add(pred);
					}
				}

				return connList.iterator();
			}
		};
		
		Set<Statement> tmpInterproceduralInNodesSet = new HashSet<Statement>();
		if (interproceduralInNodesSet != null)
			tmpInterproceduralInNodesSet = new HashSet<Statement>(interproceduralInNodesSet);
		
		// breadth-first search using only in going edges.
		// We follow the path and count all predecessor nodes
		int predCount = 0;
		for (BFSIterator<Statement> bfsIt = bfsIter; bfsIt.hasNext();) {
			Statement pred = bfsIt.next();
			// ignore starting point
			if (pred.equals(s))
				continue;
			
			if (tmpInterproceduralInNodesSet.contains(pred))
				tmpInterproceduralInNodesSet.remove(pred);

			predCount++;
		}

		return predCount + tmpInterproceduralInNodesSet.size();
	}
	
	/**
	 * Get number of all reachable direct and indirect offsprings which are
	 * considered statements
	 * @param pdg
	 * @param s
	 */
	private static int getOffspringCount(final PDG pdg, Statement s, Set<Statement> interproceduralOutNodesSet) {
		if (pdg == null || s == null)
			return 0;

		BFSIterator<Statement> bfsIter = new BFSIterator<Statement>(pdg, s) {

			/**
			 * Consider only out nodes that are considered statements
			 */
			@Override
			public java.util.Iterator<Statement> getConnected(Statement s) {
				List<Statement> connList = new ArrayList<Statement>();

				for (Iterator<Statement> succIt = pdg.getSuccNodes(s); succIt.hasNext();) {
					Statement succ = succIt.next();
					if (APDG.isConsideredStatement(succ)) {
						connList.add(succ);
					}
				}

				return connList.iterator();
			}
		};

		Set<Statement> tmpInterproceduralOutNodesSet = new HashSet<Statement>();
		if (interproceduralOutNodesSet != null)
			tmpInterproceduralOutNodesSet = new HashSet<Statement>(interproceduralOutNodesSet);
		
		// breadth-first search using only outgoing edges.
		// We follow the path and count all successor nodes
		int succCount = 0;
		for (BFSIterator<Statement> bfsIt = bfsIter; bfsIt.hasNext();) {
			Statement succ = bfsIt.next();
			// ignore starting point
			if (succ.equals(s))
				continue;
			
			if (tmpInterproceduralOutNodesSet.contains(succ))
				tmpInterproceduralOutNodesSet.remove(succ);

			succCount++;
		}

		return succCount  + tmpInterproceduralOutNodesSet.size();
	}

	/**
	 *
	 * @param s
	 * @return
	 */
	private String getNewType(Statement s) {
		if (s == null)
			return null;

		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (!SAUtil.isNewInstruction(ssa)) {
			return null;
		}

		SSANewInstruction ni = (SSANewInstruction) ssa;
		TypeReference tr = ni.getConcreteType();
		String readableType = StringStuff.jvmToReadableType(tr.getName().toString());
//		System.out.println("type: " + readableType);

		return readableType;
	}

	/**
	 * Returns the methods readable FQN signature, iff the statement is an
	 * invocation instruction
	 * @uniqueSignatures.
	 * @param s
	 * @return
	 */
	private String getMethodSignature(Statement s) {
		if (s == null)
			return null;

		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (!SAUtil.isInvokeInstruction(ssa)) {
			return null;
		}

//		Integer ii = null;
//		if (s instanceof StatementWithInstructionIndex) {
//			StatementWithInstructionIndex swii = (StatementWithInstructionIndex) s;
//			ii = swii.getInstructionIndex();
//			int lineNumber = this.n.getMethod().getLineNumber(ii);
//			System.out.println("line: " + lineNumber + " : " + s);
//		}

		SSAAbstractInvokeInstruction aii = (SSAAbstractInvokeInstruction) ssa;
		MethodReference mr = aii.getDeclaredTarget();
		String readableSignature = SAUtil.getReadableSignature(mr);

		if (SAUtil.containsUnknownType(readableSignature) || SAUtil.hasUnkownPackage(mr)) {
//			Logger.log("u+++" + readableSignature);
			String readableType = StringStuff.jvmToReadableType(mr.getDeclaringClass().getName().toString()) + "." + mr.getName().toString();
			String signatureCandidate = this.getSignatureCandidate(readableType, mr);
			if (signatureCandidate != null) {
				return signatureCandidate;
			} else {
//				Logger.log("no sign candidate for " + readableType);
			}
		}

		return readableSignature;
	}

	private  String getMethodSignature(CGNode n) {
		if (n == null)
			return null;

		MethodReference mr = n.getMethod().getReference();
		String readableSignature = SAUtil.getReadableSignature(mr);
		
		if (SAUtil.containsUnknownType(readableSignature) || SAUtil.hasUnkownPackage(mr) || true) { // hotfix skip unk type+pkg check
//			Logger.log("u+++" + readableSignature);
			String readableType = StringStuff.jvmToReadableType(mr.getDeclaringClass().getName().toString()) + "." + mr.getName().toString();
			String signatureCandidate = this.getCNSignatureCandidate(readableType, mr);
			if (signatureCandidate != null) {
				return signatureCandidate;
			} else {
//				Logger.log("no sign candidate for " + readableType);
			}
		}

		return readableSignature;
	}

	/**
	 *
	 */
	private String getFieldName(Statement s) {
		if (s == null)
			return null;

		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (!SAUtil.isFieldAccess(ssa)) {
			return null;
		}

		SSAFieldAccessInstruction fai = (SSAFieldAccessInstruction) ssa;
		FieldReference fr = fai.getDeclaredField();

		return fr.getName().toString();
	}

	/**
	 * Returns the set of string constants
	 * @param s
	 * @return
	 */
	private Set<String> getStringConstants(Statement s) {
		if (s == null)
			return null;

		Set<String> stringConstantsSet = new HashSet<String>();
		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (ssa == null)
			return null;

//		SymbolTable st = this.n.getIR().getSymbolTable();
		SymbolTable st = s.getNode().getIR().getSymbolTable();
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			int vn = ssa.getUse(i);
			if (st.isStringConstant(vn)) {
				String constant = st.getStringValue(vn);
				stringConstantsSet.add(constant);
			}
		}

		return stringConstantsSet;
	}

	/**
	 *
	 * @param stringConstants
	 * @return
	 */
	private List<RealVector> getStringConstantEmbeddings(Set<String> stringConstants) {
		if (stringConstants == null || stringConstants.size() == 0) {
			return null;
		}

		List<RealVector> constantEmbeddingList = new LinkedList<RealVector>();
		for (String constant : stringConstants) {
			RealVector embedding = this.constantEmbeddingMap.get(constant);
			if (embedding != null) {
				constantEmbeddingList.add(embedding);
//				Logger.log("c+++ " + constant);
			}
			else {
//				Logger.log("c--- " + constant);
			}
		}

		if (constantEmbeddingList.size() == 0)
			return null;

		return constantEmbeddingList;
	}

	/**
	 *
	 * @param statements
	 * @return
	 */
	private List<String> getStringConstants(List<Statement> statements) {
		if (statements == null)
			return null;

		Set<String> stringConstants = new HashSet<String>();
		for (Statement s : statements) {
			stringConstants.addAll(this.getStringConstants(s));
		}


		return new ArrayList<String>(stringConstants);
	}

	/**
	 * Number of string constants used by @param s.
	 *
	 * @param s
	 * @return
	 */
	private int getNumberOfStringConstants(Statement s) {
		if (s == null)
			return 0;

		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (ssa == null)
			return 0;

		int stringCount = 0;
		SymbolTable st = s.getNode().getIR().getSymbolTable();
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			int vn = ssa.getUse(i);
			if (st.isStringConstant(vn)) {
				stringCount++;
			}
		}

		return stringCount;
	}

	/**
	 * Return the set of numeric constants used by @param s
	 *
	 * @param s
	 * @return
	 */
	private Set<Integer> getNumericConstants(Statement s) {
		if (s == null)
			return null;

		Set<Integer> numericConstantsSet = new HashSet<Integer>();
		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (ssa == null)
			return null;

//		SymbolTable st = n.getIR().getSymbolTable();
		SymbolTable st = s.getNode().getIR().getSymbolTable();
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			int vn = ssa.getUse(i);
			if (st.isIntegerConstant(vn)) {
				Integer number = st.getIntValue(vn);
				assert number != null;
				numericConstantsSet.add(number);
			}
		}

		return numericConstantsSet;
	}

	/**
	 *
	 * @param statements
	 * @return
	 */
	private List<Integer> getNumericConstants(List<Statement> statements) {
		if (statements == null)
			return null;

		Set<Integer> numericConstants = new HashSet<Integer>();
		for (Statement s : statements) {
			numericConstants.addAll(this.getNumericConstants(s));
		}

		return new ArrayList<Integer>(numericConstants);
	}

	/**
	 * Number of numeric constants used by @param s.
	 *
	 * @param s
	 * @return
	 */
	private int getNumberOfNumericConstants(Statement s) {
		if (s == null)
			return 0;

		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (ssa == null)
			return 0;

		int numberCount = 0;
//		SymbolTable st = n.getIR().getSymbolTable();
		SymbolTable st = s.getNode().getIR().getSymbolTable();
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			int vn = ssa.getUse(i);
			if (st.isNumberConstant(vn))
				numberCount++;
		}

		return numberCount;
	}
	
	/**
	 * Return the set of numeric constants used by @param s
	 *
	 * @param s
	 * @return
	 */
	private Boolean getBooleanConstant(Statement s) {
		if (s == null)
			return null;

		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (ssa == null)
			return null;

		Boolean aggrBoolean = null;
		SymbolTable st = s.getNode().getIR().getSymbolTable();
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			int vn = ssa.getUse(i);
			if (st.isBooleanConstant(vn)) {
				Boolean tmpBoolean = (Boolean) st.getConstantValue(vn);
				if (aggrBoolean == null)
					aggrBoolean = new Boolean(tmpBoolean);
				else
					aggrBoolean = new Boolean(tmpBoolean ? true : aggrBoolean);
				assert aggrBoolean != null;
			}
		}
		if (aggrBoolean == null)
			return null;

		return aggrBoolean;
	}
	
	/**
	 * 
	 * @param s
	 * @return
	 */
	private int getNumberOfBooleanConstants(Statement s) {
		if (s == null)
			return 0;

		SSAInstruction ssa = this.statementToInstrMap.get(s);
		if (ssa == null)
			return 0;

		int booleanCount = 0;
		SymbolTable st = s.getNode().getIR().getSymbolTable();
		for (int i = 0; i < ssa.getNumberOfUses(); i++) {
			int vn = ssa.getUse(i);
			if (st.isBooleanConstant(vn))
				booleanCount++;
		}

		return booleanCount;
	}

	/**
	 * Re-implementation of SAUtil method which prunes the graph
	 * including only considered statements
	 * @param pdg
	 * @param s
	 * @return
	 */
	private static List<Statement> getOutNodes(PDG pdg, Statement s, boolean onlyConsideredStmts) {
		List<Statement> consideredOutNodes = new ArrayList<Statement>();
		List<Statement> outNodes = SAUtil.getOutNodes(pdg, s);
		for (Statement outNode : outNodes) {
			if (!onlyConsideredStmts)
				consideredOutNodes.add(outNode);
			else if (APDG.isConsideredStatement(outNode)) { // we dont need to call isInConsideredStms
				// because we know that outNode has a direct neighbour + isConsideredStmnt = true
				// this asserts outNode isInConsideredStms
				consideredOutNodes.add(outNode);
			}
		}

		return consideredOutNodes;
	}

	/**
	 * Re-implementation of SAUtil method which prunes the graph
	 * including only considered statements
	 * @param pdg
	 * @param s
	 * @return
	 */
	private static List<Statement> getInNodes(PDG pdg, Statement s, boolean onlyConsideredStmts) {
		List<Statement> consideredInNodes = new ArrayList<Statement>();
		List<Statement> inNodes = SAUtil.getInNodes(pdg, s);
		for (Statement inNode : inNodes) {
			if (!onlyConsideredStmts)
				consideredInNodes.add(inNode);
			else if (APDG.isConsideredStatement(inNode)) {
				consideredInNodes.add(inNode);
			}
		}

		return consideredInNodes;
	}

	private List<Statement> getForwardSlice(SDG sdg, Statement s, boolean onlyInConsideredStmts) {
		if (sdg == null || s == null)
			return null;

		List<Statement> consideredSlice = new ArrayList<Statement>();
        // context-sensitive traditional slice
        try {
        	Collection<Statement> slice = Slicer.computeForwardSlice(sdg, s);
			for (Statement st : slice) {
				if (!onlyInConsideredStmts && APDG.isConsideredStatement(st))
					consideredSlice.add(st);
				else if (APDG.isInConsideredStatements(st, this.consideredStatements)) {
					consideredSlice.add(st);
				}
			}
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CancelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return consideredSlice;
	}
	
	private Collection<Statement> getBackwardSlice(SDG sdg, Statement s/*, boolean onlyInConsideredStmts*/) {
		if (sdg == null || s == null)
			return null;

		Collection<Statement> slice = null;
		
        // context-sensitive traditional slice
        try {
        	CallableSlicer cSlicer = new CallableSlicer(sdg, s);
        	final ExecutorService executor = Executors.newSingleThreadExecutor();
            final Future<Collection<Statement>> future = executor.submit(cSlicer);
            executor.isShutdown();
            
			try {
				slice = future.get(5, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (TimeoutException e) {
				Logger.log("to+++");
			}
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}

		return slice;
	}

	private int getNumberOfParameters(Statement s) {
		if (s == null)
			return 0;

		SSAInstruction ssa = this.statementToInstrMap.get(s);

		return SAUtil.getParameterCount(ssa);
	}

	/**
	 * The APDG is created by iterating over the PDG of a method.
	 * A feature vector is created for each node of the PDG (i.e.
	 * each statement) and each constant used by a statement.
	 */
	private void buildAPDGFromPDG() {
		// Iterate over all statements and store relations to other statements
		for (Statement s : this.consideredStatements) {
			RealVector featureVector = this.buildFeatureVectorFromStatement(this.consideredStatements, s);

			if (featureVector != null) {
				this.apdg.add(featureVector);
				if (Label.isLabeledSignature(this.getMethodSignature(s), s.getKind(), APDG.label)) {
					this.nodeLabels.add(this.consideredStatements.indexOf(s));
					Logger.log("l+++ " + s);
				} else {
//					Logger.log("l--- " + s);
				}
//				if (Label.isLabeledType(this.getNewType(s))) {
//					this.nodeLabels.add(this.consideredStatements.indexOf(s));
//					Logger.log("l+++ " + s);
//				}
			}
		}

//		 Check for API method overrides and store signature
		if (Label.isLabeledSignature(this.getMethodSignature(n), null, APDG.label)) {
			RealVector featureVectorCGNode = this.buildFeatureVectorFromCGNode(this.n);
			if (featureVectorCGNode != null) {
				this.apdg.add(featureVectorCGNode);
				Logger.log("cgnl+++ " + n.getMethod().getName());
			} else {
//				Logger.log("cgnl--- " + n.getMethod().getName());
			}
		}
//		if (this.pdgStream.getNodeCount() != 0) {
//			FileSink fileSink = new FileSinkDOT();
//			try {
//				fileSink.writeAll(this.pdgStream, "/home/hpc/pn69xi/ge29kod2/apdg/experiment/graphs/" + this.n.getMethod().getName().toString().replace("<", "").replace(">", "") + this.n.getMethod().hashCode() + ".dot");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
		this.nodeCount = this.apdg.size();
	}

	/**
	 * The APDG is created by iterating over the semantic blocks
	 * of a method. A feature vector is created for each node of the PDG (i.e.
	 * each statement). Information about the topology of semantic blocks
	 * is lost.
	 */
	private void buildAPDGFromSemanticBlocks() {

	}

	/**
	 * Create feature vector for CGNode. It stores its signature and holds
	 * a relation to all statements in its PDG. -> NOT
	 * @param n
	 * @return
	 */
	private RealVector buildFeatureVectorFromCGNode(CGNode n) {
		if (n == null)
			return null;

		RealVector cgnSignatureEmbedding = null;
		String cgnMethodSignature = this.getMethodSignature(this.n);
//		Logger.log("cgns " + cgnMethodSignature);

		if (cgnMethodSignature != null && this.signatureEmbeddingMap.containsKey(cgnMethodSignature)) {
			cgnSignatureEmbedding = this.signatureEmbeddingMap.get(cgnMethodSignature);
		}

		if (cgnSignatureEmbedding == null) {
//			Logger.log("cgns--- " + n);
			return null;
		}
		Logger.log("cgns+++ " + n);

		RealVector featureVector = new ArrayRealVector(FEATURE_DIM); // TODO add feature is_cgn
//		for (Statement s : this.consideredStatements) {
//			int apdgIndex = this.consideredStatements.indexOf(s);
//			featureVector.append(apdgIndex);
//		}

		RealVector stringContantEmbedding = new ArrayRealVector(CONSTANT_EMBEDDING_DIMENSION);

		RealVector features = cgnSignatureEmbedding.append(stringContantEmbedding);
		features = features.append(featureVector);
	
		return features;
	}
	/**
	 * Extracts the feature vector from a statement.
	 * 
	 * @param statements
	 * @param s
	 * @return
	 */
	private RealVector buildFeatureVectorFromStatement(List<Statement> statements, Statement s) {
		if (statements == null || s == null)
			return null;

		SSAInstruction ssa = this.statementToInstrMap.get(s);

		// Init feature vector, excluding embedding vectors for signature and constants
		RealVector featureVector = new ArrayRealVector(FEATURE_DIM);

		// Resolve instruction type of @param s and set one-hot encoding
		int instructionType = -1;
		try {
			instructionType = SAUtil.resolveInstructionType(ssa);
			featureVector.setEntry(instructionType, new Integer(1));
		} catch (Exception e) {
			// Logger.log("WARNING: Instruction type could not be resolved: " + s);
		}

		if (s.getKind().equals(Statement.Kind.PARAM_CALLEE)) {
			featureVector.setEntry(IS_PARAM_CALLEE_INDEX, 1);
		} else if (s.getKind().equals(Statement.Kind.PARAM_CALLER)) {
			featureVector.setEntry(IS_PARAM_CALLER_INDEX, 1);
		} else if (s.getKind().equals(Statement.Kind.EXC_RET_CALLER)) {
			featureVector.setEntry(IS_EXC_RET_CALLER_INDEX, 1);
		} // TODO add NORMAL_RETURN_CALLEE

		// Get method signature from @param s end query embedding.
		// methodSignature is null if instruction is not a method invocation
		// signatureEmbedding will be zeroed if methodSignature is null
		RealVector signatureEmbedding = new ArrayRealVector(SIGNATURE_EMBEDDING_DIMENSION);
		String methodSignature = this.getMethodSignature(s); // TODO Check for <init> methods
		/*if (methodSignature.equals("java.lang.Object.<init>()")) {
			methodSignature = null;
		} else*/ 
		if (methodSignature != null && this.signatureEmbeddingMap.containsKey(methodSignature)) {
			signatureEmbedding = this.signatureEmbeddingMap.get(methodSignature);
//			Logger.log("s+++ " + methodSignature);
		} else if (methodSignature != null) {
//			Logger.log("s--- " + methodSignature);
		}
		assert signatureEmbedding != null;

//		String newType = this.getNewType(s);

		// Get number of paramaters of @param s.
		// Will be zero if instruction is not a method invocation
		int numberOfParameters = this.getNumberOfParameters(s);
		featureVector.setEntry(PARAM_COUNT_INDEX, numberOfParameters);

		// Get string constants used by @param s and query embedding.
		// Sum up all embeddings.
		RealVector stringConstantEmbedding = new ArrayRealVector(CONSTANT_EMBEDDING_DIMENSION);
		Set<String> stringConstantsForStatement = this.getStringConstants(s);
		List<RealVector> stringConstantEmbeddings = this.getStringConstantEmbeddings(stringConstantsForStatement);
		if (stringConstantEmbeddings != null) {
			stringConstantEmbedding = SAUtil.addEmbeddings(stringConstantEmbeddings, stringConstantEmbedding.getDimension());
		}
		assert stringConstantEmbedding != null;

		// Get field name and add it to string constants embedding
		String fieldName = this.getFieldName(s);
		if (fieldName != null && this.constantEmbeddingMap.containsKey(fieldName)) {
			stringConstantEmbedding = stringConstantEmbedding.add(this.constantEmbeddingMap.get(fieldName));
//			Logger.log("f+++ " + fieldName);
		}

		// Get number of string constants used by @param p.
		int numberOfStringConstants = this.getNumberOfStringConstants(s);
		featureVector.setEntry(STRING_CONSTANT_COUNT_INDEX, numberOfStringConstants);

		// Get integer constants used by @param s.
		// Sum up all integers.
		Set<Integer> numericConstantsForStatement = this.getNumericConstants(s);
		Set<Integer> numericConstantsForStatementNormalized = new HashSet<Integer>();
		if (APDG.label.equals(Label.Type.PBE) && numericConstantsForStatement != null && !APDG.IGNORE_LABELS) { // this is a hotfix. embeddings cannot deal with large numbers.
			for (Iterator<Integer> iterInt = numericConstantsForStatement.iterator(); iterInt.hasNext();) {
				Integer i = iterInt.next();
				if (i > 1000) {
					numericConstantsForStatementNormalized.add(1000);
				} else {
					numericConstantsForStatementNormalized.add(i);
				}
			}
			numericConstantsForStatement = numericConstantsForStatementNormalized;
		}
		Integer summedUpIntegers = SAUtil.addIntegerValues(numericConstantsForStatement);
		
		Boolean booleanConstantForStatement = this.getBooleanConstant(s);
		if (booleanConstantForStatement != null && booleanConstantForStatement) {
			if (summedUpIntegers != null)  {
				summedUpIntegers += 1;
			} else { 
				summedUpIntegers = new Integer(1);
			}
		}
		if (summedUpIntegers != null) {
			featureVector.setEntry(NUMERIC_VALUE_INDEX, summedUpIntegers);
		}
		
		//Get number of boolean constants used by @param s
		int numberOfBooleanConstants = this.getNumberOfBooleanConstants(s);

		// Get number of integer constants used by @param s
		int numberOfNumericConstants = this.getNumberOfNumericConstants(s);
		featureVector.setEntry(NUMERIC_CONSTANT_COUNT_INDEX, numberOfNumericConstants + numberOfBooleanConstants);
		
		// Get the statements PDG
		PDG pdg = this.sdg.getPDG(s.getNode());
		
		// Get number of ancestors of @param s
		int ancestors = APDG.getAncestorCount(pdg, s, this.statementToInNodeMap.get(s));
//		featureVector.setEntry(APDG.OFFSPRING_INDEX, ancestors);

		// Get number of offsprings of @param s
		int offsprings = APDG.getOffspringCount(pdg, s, this.statementToOutNodeMap.get(s));
		if (Label.isLabeledForOffsprings(methodSignature, s.getKind(), APDG.label) || APDG.IGNORE_LABELS) // TODO remove this condition again
			featureVector.setEntry(APDG.OFFSPRING_INDEX, ancestors + offsprings);
		else
			featureVector.setEntry(APDG.OFFSPRING_INDEX, ancestors);

		// Get in-nodes of @param s
		List<Statement> inNodes = APDG.getInNodes(pdg, s, true);
		List<Statement> inNodesConsidered = new ArrayList<Statement>();
		for (Statement inNode : inNodes) {
			if (statements.indexOf(inNode) >= 0) {
				inNodesConsidered.add(inNode);
			} else {
//				Logger.log("WARNING: in node not considered " + inNode);
			}
		}
		inNodes = inNodesConsidered;
		
		// Add edges to non-modeled in-nodes
		if (this.statementToInNodeMap.containsKey(s)) {
			inNodes.addAll(this.statementToInNodeMap.get(s));
		}
		
		// Get in-node degree of @param s
		int inNodeDegree = inNodes.size();

		// Get out-nodes of @param s
		List<Statement> outNodes = APDG.getOutNodes(pdg, s, true);
		List<Statement> outNodesConsidered = new ArrayList<Statement>();
		for (Statement outNode : outNodes) {
			if (statements.indexOf(outNode) >= 0) {
				outNodesConsidered.add(outNode);
			} else {
//				Logger.log("WARNING: in node not considered " + outNode);
			}
		}
		outNodes = outNodesConsidered;

		// Add edges to non-modeled out-nodes
		if (this.statementToOutNodeMap.containsKey(s)) {
			outNodes.addAll(this.statementToOutNodeMap.get(s));
		}
		
		// Get out-node degree of @param s
		int outNodeDegree = outNodes.size();
		
		// Set node degree in features
		if (Label.isLabeledForOffsprings(methodSignature, s.getKind(), APDG.label) || APDG.IGNORE_LABELS) // TODO remove this condition again
			featureVector.setEntry(NODE_DEGREE_INDEX, inNodeDegree  + outNodeDegree);
		else
			featureVector.setEntry(NODE_DEGREE_INDEX, inNodeDegree);
		
		// Get out-node index and store reference in feature vector
		for (Statement inNode : inNodes) { // Add references to statements
			int apdgIndex = statements.indexOf(inNode);
			if (apdgIndex < 0) {
				Logger.log("WARNING: not considered in-node " + inNode);
				continue;
			}
			featureVector = featureVector.append(apdgIndex);
		}

		// Get out-node index and store reference in feature vector
		if (Label.isLabeledForOffsprings(methodSignature, s.getKind(), APDG.label) || APDG.IGNORE_LABELS) // TODO remove this condition again
			for (Statement outNode : outNodes) { // Add references to statements
				int apdgIndex = statements.indexOf(outNode);
				if (apdgIndex < 0) {
					Logger.log("WARNING: not considered out-node " + outNode);
					continue;
				}
				featureVector = featureVector.append(apdgIndex);
			}

		// Create feature vector
		RealVector features = signatureEmbedding.append(stringConstantEmbedding);
		features = features.append(featureVector);

		// Get line number of statement
		Integer lineNumber = SAUtil.getLineNumber(s);

		// Get instruction name
		String instructionName = null;
		if (ssa != null)
			instructionName = ssa.toString();

		// Create and append slice label
		if (featureVector != null) { // FIXME will never be null
			Slice slice = new Slice();
			if (instructionType != -1)
				slice.setInstructionType(instructionType);
			slice.setStatementKind(s.getKind().toString());
			if (methodSignature != null)
				slice.setSignature(methodSignature);
			if (lineNumber != null)
				slice.setLineNumber(lineNumber.toString());
			if (instructionName != null)
				slice.setInstructionName(instructionName);
			this.sliceLabels.add(slice);
		}

//		System.out.println(s);
		if (Label.isLabeledSignature(methodSignature, s.getKind(), APDG.label) || true) {
//		if (Label.isLabeledType(this.getNewType(s))) {
//			String stmtId = s.toString().replaceAll(" ", "");
//			String quotedStmtId = "\"" + stmtId + "\"";
//			Node stmtNode = this.pdgStream.getNode(quotedStmtId);
//			if (stmtNode == null) {
//				stmtNode = this.pdgStream.addNode(quotedStmtId);
//			}
//			if (Label.isLabeledSignature(methodSignature, s.getKind(), APDG.label)) {
//				stmtNode.addAttribute("isLabeled");
//			}
			Logger.log(s);
//			Logger.log("Line: " + SAUtil.getLineNumber(s));
//			Logger.log("Instruction type: " + instructionType/* + " : " + (featureVector.getEntry(instructionType) == 1)*/);
//			Logger.log("Method: " + methodSignature);
//			System.out.println("new type: " + newType);
//			Logger.log("Field name: " + fieldName);
			// System.out.println("Is param callee: " +
			// (featureVector.getEntry(IS_PARAM_CALLEE_INDEX) == 1));
			// System.out.println("Is param caller: " +
			// (featureVector.getEntry(IS_PARAM_CALLER_INDEX) == 1));
			// System.out.println("Is exc ret caller: " +
			// (featureVector.getEntry(IS_EXC_RET_CALLER_INDEX) == 1));
//			Logger.log("Strings: " + numberOfStringConstants + " : "
//					+ stringConstantsForStatement);
//			Logger.log("Numbers: " + numberOfNumericConstants + " : "
//					+ numericConstantsForStatement);
//			Logger.log("Boolean: " + booleanConstantForStatement + " : "
//					+ booleanConstantForStatement);
//			Logger.log("Params: " + numberOfParameters);
//			SAUtil.printAllUsedValues(this.statementToInstrMap.get(s), s.getNode());
//			SAUtil.printAllDefValues(this.statementToInstrMap.get(s), s.getNode());
//			 System.out.println("Ancestors: " + ancestors);
//			 System.out.println("Offsprings: " + offsprings);
//			final Collection<Statement> bwSlice;
//			try {
//				bwSlice = Slicer.computeBackwardSlice(this.sdg, s);
//			
//				Graph<Statement> prunedGraph = PDFSlice.pruneSDG(this.sdg, bwSlice);
//				Logger.log(GraphPrint.genericToString(prunedGraph));
//				for (Statement ss : bwSlice) {
//					if (APDG.isConsideredStatement(ss)) {
//						Logger.log(ss + "\n\t->" + SAUtil.jumpHeapAndGetConsideredOutNodesForStatement(bwSlice, this.sdg, ss));
//					}
//				}
//			} catch (IllegalArgumentException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (CancelException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			};
			Logger.log("InNodes:");
			for (Statement ss : inNodes) {
				Logger.log("\t" + ss);
//				String inNodeId = ss.toString().replaceAll(" ", "");
//				String quotedInNodeId = "\"" + inNodeId + "\"";
//				if (this.pdgStream.getNode(quotedInNodeId) == null) {
//					this.pdgStream.addNode(quotedInNodeId);
//				}
//				if (this.pdgStream.getEdge("\"" + inNodeId + stmtId  + "\"") == null){
//					this.pdgStream.addEdge("\"" + inNodeId + stmtId  + "\"", quotedInNodeId, quotedStmtId, true);
//				}
			}
			
			Logger.log("OutNodes:");
			for (Statement ss : outNodes) {
				Logger.log("\t" + ss);
//				String outNodeId = ss.toString().replaceAll(" ", "");
//				String quotedOutNodeId = "\"" + outNodeId + "\"";
//				if (this.pdgStream.getNode(quotedOutNodeId) == null) {
//					this.pdgStream.addNode(quotedOutNodeId);
//				}
//				if (this.pdgStream.getEdge("\"" + stmtId + outNodeId  + "\"") == null) {
//					Logger.log(stmtId);
//					Logger.log(outNodeId);
//					Logger.log("\"" + stmtId + outNodeId  + "\"");
//;					this.pdgStream.addEdge("\"" + stmtId + outNodeId + "\"", quotedStmtId, quotedOutNodeId, true);
//				}
			}
			// System.out.println(featureVector.getDimension());
			// System.out.println(featureVector);
			// System.out.println(stringContantEmbedding.getDimension());
			// System.out.println(stringContantEmbedding);
			// System.out.println(signatureEmbedding.getDimension());
			// System.out.println(signatureEmbedding);
			
		}

		return features;
//		return featureVector;
	}

	private void printSlice(Collection<Statement> slice) {
		if (slice == null)
			return;

		for (Statement s : slice) {
			System.out.println("\t" + s);
		}
	}

	public void setProject(String project) {
		String trimmedProjectName = project.replace("-", "_");
		this.project = trimmedProjectName;
	}

	public void setRepresentation(String representation) {
		this.representation = representation;
	}

	public List<RealVector> getAPDG() {
		return this.apdg;
	}

	public int getNodeCount() {
		return this.nodeCount;
	}

	public String getNodeLables() {
		String s = "";
		List<Integer> nodeLabelList = new ArrayList<Integer>(this.nodeLabels);

		for (int i = 0; i < nodeLabelList.size(); i++) {
			s += nodeLabelList.get(i);
			if (i < nodeLabelList.size() - 1) {
				s += "|";
			}
		}
		return s;
	}

	public String getSlices() {
		String s = this.sliceLabels.size() + " " + this.methodeName;
		if (this.className != null && this.project != null && this.representation != null) {
			s += "||" + this.project + "-X-" + this.representation + "-android-" + "X/" + this.className;
		}
		s += "\n";
		for (int i = 0; i < this.sliceLabels.size(); i++) {
			s += this.sliceLabels.get(i);
			if (i < this.sliceLabels.size() - 1)
				s += "\n";
		}

		return s;
	}

	/**
	 * Format for storing the APDG
	 */
	@Override
	public String toString() {
		String s = this.nodeCount + " " + this.methodeName;
		if (this.className != null && this.project != null && this.representation != null) {
			s += "||" + this.project + "-X-" + this.representation + "-android-" + "X/" + this.className;
		}
		String nodeLabels = this.getNodeLables();
		if (nodeLabels != null) {
			s += "||" + nodeLabels;
		}
		s += "\n";
		for (int i = 0; i < this.apdg.size(); i++) {
			RealVector featureVector = this.apdg.get(i);
			for (int j = 0; j < featureVector.getDimension(); j++) {
				if (j >= SIGNATURE_EMBEDDING_DIMENSION + CONSTANT_EMBEDDING_DIMENSION) { // cast to int
					int entry = (int) featureVector.getEntry(j);
					s += entry;
				} else {
					s += /*i + ":" +*/ featureVector.getEntry(j);
				}
				if (j < featureVector.getDimension() - 1)
					s+= " ";
			}
			s.trim();
			if (i < this.apdg.size() - 1)
				s += "\n";
		}

		return s;
	}
}
