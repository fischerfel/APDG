package com.aisec.sa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.ipa.slicer.ValueNumberCarrier;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.IR;
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
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAStoreIndirectInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.graph.traverse.BFSIterator;
import com.ibm.wala.util.strings.Atom;

/**
 * This class stores instruction type and method name counts of a semantic block in an array.
 * If available it also stores java type occurrences and complete method descriptors of the PDG in a bit vector.
 * It uses a hash map for storing array indices of each key. 
 * 
 * TODO Instead of using a hash map do the hashing trick for better performance
 * 
 * @author fischer
 *
 */
public class SemanticVector {
	
	/**
	 * number of instruction types stored in the semantic vector
	 */
	private static final int INSTR_TYPE_COUNT = 33;
	
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
	
	private boolean hasAllowAllHost;
	
	private boolean hasStrictHost;
	
	/**
	 * 
	 */
	private final CGNode n;
	/**
	 * 
	 */
	private final PDG pdg;
	
	/**
	 * Array representing the semantic vector
	 */
	private LinkedList<int[]> semanticVectorList;
	
	/**
	 * Hash map which stores indices of the semantic vector where the counts of the mapped method names are stored 
	 */
	private HashMap<String, Integer> methodNameToIndexMap;
	
	private HashMap<String, Integer> valueToIndexMap;
	
	/**
	 * Hash map which stores indices of the semantic vector where the counts of the mapped instruction types are stored
	 */
	private HashMap<Statement, SSAInstruction> statementToInstrMap;
	
	private DescriptorSet mns;
	
	private ValueSet vs;
	
	/**
	 * Set of all found method names in the PDG statements
	 */
	private Set<String> methodNameSet;
	
	private Set<String> valueSet;
	
	/**	
	 * 
	 * @param n
	 * @param pdg
	 */
	public SemanticVector(CGNode n, PDG pdg) {
		if (n == null || pdg == null)
			throw new IllegalArgumentException();
		this.hasAllowAllHost = false;
		this.hasStrictHost = false;
		
		this.n = n;
		this.pdg = pdg;
		
		this.semanticVectorList = new LinkedList<int[]>();
		
		this.buildStatementToInstructionMap(this.pdg);
	}
	
//	/**
//	 * 
//	 * constructor of the semantic vector
//	 *
//	 * @param n
//	 * @param pdg
//	 * @param vs 
//	 * @param mns 
//	 */
//	public SemanticVector(CGNode n, PDG pdg, MethodNameSet mns, ValueSet vs) {
//		if (n == null || pdg == null || mns == null || vs == null) 
//			throw new IllegalArgumentException();
//		
//		this.n = n;
//		this.pdg = pdg;
//		
//		this.mns = mns;
//		this.vs = vs;
//		
//		this.methodNameSet = mns.getMethodNameSet();
//		this.valueSet = vs.getValueSet();
//		
//		this.semanticVectorList = new LinkedList<int[]>();
//		
//		this.buildStatementToInstructionMap(this.pdg);
////		this.buildMethodIndexMap(this.methodNameSet);
////		this.buildValueIndexMap(this.valueSet);
//		this.methodNameToIndexMap = SAUtil.buildStringToIndexMap(this.methodNameSet);
//		this.valueToIndexMap = SAUtil.buildStringToIndexMap(this.valueSet);
//	}
//	
//	/**
//	 * 
//	 * @param n
//	 * @param pdg
//	 * @param methodNameSet
//	 * @param methodNameToIndexMap
//	 */
//	public SemanticVector(CGNode n, PDG pdg, MethodNameSet mns, ValueSet vs,
//			HashMap<String, Integer> methodNameToIndexMap, HashMap<String, Integer> valueToIndexMap) {
//		if (n == null || pdg == null || mns == null || vs == null
//				|| methodNameToIndexMap == null || valueToIndexMap == null) 
//			throw new IllegalArgumentException();
//		
//		this.n = n;
//		this.pdg = pdg;
//		
//		this.mns = mns;
//		this.vs = vs;
//		
//		this.methodNameSet = mns.getMethodNameSet();
//		this.valueSet = vs.getValueSet();
//		
//		this.methodNameToIndexMap = methodNameToIndexMap;
//		this.valueToIndexMap = valueToIndexMap;
//		
//		this.semanticVectorList = new LinkedList<int[]>();
//		
//		this.buildStatementToInstructionMap(pdg);
//	}
	
	public DescriptorSet getMethodNameSet() {
		return mns;
	}

	public void setMethodNameSet(DescriptorSet mns) {
		this.mns = mns;
	}

	public ValueSet getValueSet() {
		return vs;
	}

	public void setValueSet(ValueSet vs) {
		this.vs = vs;
	}
	
	public Set<String> getSetWithValues() {
		return this.valueSet;
	}
	
	public boolean hasAllowAllHost() {
		return this.hasAllowAllHost;
	}
	
	public boolean hasStrictAllowHost() {
		return this.hasStrictHost;
	}
	
	
	/**
	 * 
	 * @param ssa
	 * @return
	 * @throws Exception
	 */
	private int resolveInstructionType(SSAInstruction ssa) throws Exception {
		if (ssa == null) 
			throw new Exception("invalid instruction type");
		
		if (ssa instanceof SSAAbstractInvokeInstruction) {
			return this.resolveInvokeInstructionTarget((SSAAbstractInvokeInstruction) ssa);
		} else if (ssa instanceof SSAInvokeInstruction) {
			return this.resolveInvokeInstructionTarget((SSAInvokeInstruction)ssa);
		} else if (ssa instanceof SSAAddressOfInstruction) {
			return SemanticVector.ADDR_OF;
		} else if (ssa instanceof SSAArrayLengthInstruction) {
			return SemanticVector.ARR_LENGTH;
		} else if (ssa instanceof SSAArrayLoadInstruction) {
			return SemanticVector.ARR_LOAD;
		} else if (ssa instanceof SSAArrayReferenceInstruction) {
			return SemanticVector.ARR_REF;
		} else if (ssa instanceof SSAArrayStoreInstruction) {
			return SemanticVector.ARR_STORE;
		} else if (ssa instanceof SSABinaryOpInstruction) {
			return this.resolveBinaryOp((SSABinaryOpInstruction) ssa);
		} else if (ssa instanceof SSACheckCastInstruction) {
			return SemanticVector.CHECK_CAST;
		} else if (ssa instanceof SSAComparisonInstruction) {
			return SemanticVector.COMPARISON;
		} else if (ssa instanceof SSAConversionInstruction) {
			return SemanticVector.CONVERSION;
		} else if (ssa instanceof SSAPutInstruction ) {
			this.resolveHostnameVerFieldAccess(ssa);
			return SemanticVector.PUT;
		} else if (ssa instanceof SSAGetCaughtExceptionInstruction) {
			return SemanticVector.GET_CAUGHT_EXC;
		} else if (ssa instanceof SSAGetInstruction) {
			this.resolveHostnameVerFieldAccess(ssa);
			if (((SSAGetInstruction) ssa).isStatic())
					return SemanticVector.GET;
			throw new Exception("invalid instruction type for " + ssa.toString());
		} else if (ssa instanceof SSAInstanceofInstruction) {
			return SemanticVector.INST_OF;
		} else if (ssa instanceof SSALoadIndirectInstruction) {
			return SemanticVector.LOAD_IND;
		} else if (ssa instanceof SSALoadMetadataInstruction) {
			return SemanticVector.LOAD_META_DATA;
		} else if (ssa instanceof SSANewInstruction) {
			this.resolveNewInstructionTarget(ssa);
			return SemanticVector.NEW;
		} else if (ssa instanceof SSAReturnInstruction) {
			return SemanticVector.RETURN;
		} else if (ssa instanceof SSAStoreIndirectInstruction) {
			return SemanticVector.STORE_INDIRECT;
		} else if (ssa instanceof SSAThrowInstruction) {
			return SemanticVector.THROW;
		} else if (ssa instanceof SSAUnaryOpInstruction) {
			return SemanticVector.UNARY_OP;
		} else if (ssa instanceof SSAGotoInstruction) {
			return SemanticVector.GOTO;
		} else if (ssa instanceof SSAConditionalBranchInstruction) {
			return SemanticVector.COND_BRANCH;
		} else {
			throw new Exception("invalid instruction type for " + ssa.toString());
		}
	}
	
	/**
	 * 
	 * @param aii
	 * @return
	 * @throws Exception 
	 */
	private int resolveInvokeInstructionTarget(SSAAbstractInvokeInstruction aii) throws Exception {
		if (aii == null)
			throw new IllegalArgumentException();
		
		String signature = aii.getDeclaredTarget().getSignature();
//		System.out.println(signature);
		if (signature.contains("java.lang.StringBuilder")) {
			throw new Exception("invalid instruction type for " + aii.toString());
		}
		
		IInvokeInstruction.Dispatch dispatch = (IInvokeInstruction.Dispatch) aii.getCallSite().getInvocationCode();
		switch (dispatch) {
			case SPECIAL:
				return SemanticVector.INV_SPECIAL;
			default:
				return SemanticVector.INV_INSTR;
		}
	}
	
	/**
	 * 
	 * @param bo
	 * @return
	 */
	private int resolveBinaryOp(SSABinaryOpInstruction bo) {
		if (bo == null)
			throw new IllegalArgumentException();
		
		//  ADD, SUB, MUL, DIV, REM, AND, OR, XOR;
		IBinaryOpInstruction.Operator op = (IBinaryOpInstruction.Operator) bo.getOperator();
		switch (op) {
		case ADD:
			return SemanticVector.ADD;
		case SUB:
			return SemanticVector.SUB;
		case MUL:
			return SemanticVector.MUL;
		case DIV:
			return SemanticVector.DIV;
		case REM:
			return SemanticVector.REM;
		case AND:
			return SemanticVector.AND;
		case OR:
			return SemanticVector.OR;
		case XOR:
			return SemanticVector.XOR;
		default:
			return SemanticVector.BINARY_OP;
		}
	}
	
	private void resolveHostnameVerFieldAccess(SSAInstruction ssa) {
//		System.out.println("resolving field access");
		if (ssa instanceof SSAFieldAccessInstruction) {
			SSAFieldAccessInstruction fa = (SSAFieldAccessInstruction) ssa;
			FieldReference fr = fa.getDeclaredField();
//			System.out.println(fr.getName());
			if (fr.getName().equals(Atom.findOrCreateAsciiAtom("ALLOW_ALL_HOSTNAME_VERIFIER"))) {
//				System.out.println(fr.getName());
				
				this.hasAllowAllHost = true;
			} else if (fr.getName().equals(Atom.findOrCreateAsciiAtom("STRICT_HOSTNAME_VERIFIER"))) {
				
				this.hasStrictHost = true;
			}
		}
	}
	
	private void resolveNewInstructionTarget(SSAInstruction ssa) throws Exception {
		if (ssa instanceof SSANewInstruction) {
			SSANewInstruction ni = (SSANewInstruction) ssa;
			String type = ni.getConcreteType().getName().toString();
//			System.out.println("new type: " + type);
			if (type.contains("java/lang/StringBuilder")) {
				throw new Exception("invalid instruction type for " + ni.toString());
			}
		}
	}
	
	/**
	 * 
	 * @param ssa
	 * @param semanticVec
	 */
	private boolean incrementInstructionTypeCount(SSAInstruction ssa, int[] semanticVec) {
		int type;
		try {
			type = this.resolveInstructionType(ssa);
			int typeCount = semanticVec[type];
			semanticVec[type] = typeCount + 1;
			
			return true;
		} catch (Exception e) { 
			e.toString();
			
			return false;
		}
	}
	
	/**
	 * 
	 * @param ssa
	 * @param methodNameCount
	 * @param typeRefVec
	 */
	private boolean incrementMethodCount(SSAInstruction ssa, int[] methodNameCount /*int[] typeRefVec*/) {
		if (ssa == null /*|| typeRefVec == null*/)
			throw new IllegalArgumentException();
		
//		System.out.println("increment type reference count for " +  ssa.toString());
		
		if (ssa instanceof SSAAbstractInvokeInstruction) {
			SSAAbstractInvokeInstruction aii = (SSAAbstractInvokeInstruction) ssa;
			MethodReference mr = aii.getDeclaredTarget();
			
			// always increment method name in semantic vector
			if (mr != null) {
				String methodName = mr.getName().toString();
				return this.doIncrementMethodCount(methodName, methodNameCount);
			}
		}
		
		return false;
	}
	
	/**
	 * 
	 * @param sm
	 * @param valueVec
	 * @return
	 */
	private boolean incrementValueCount(Statement sm, int[] valueVec) {
		if (sm == null)
			throw new IllegalArgumentException();
		
		if (sm instanceof ValueNumberCarrier) {
			ValueNumberCarrier vnc = (ValueNumberCarrier) sm;
			int vn = vnc.getValueNumber();
			
			IR ir = this.n.getIR();
			SymbolTable st = ir.getSymbolTable();
			
			String value = ValueSet.getValueFromValueString(st.getValueString(vn));
			
			if (value != null) {
				return this.doIncrementValueCount(value, valueVec);
			} 
		} else { 
//			System.out.println("\tno vnc " + sm);
		}
		return false;
	}
	
	/**
	 * 
	 * @param key
	 * @param vec
	 */
	private boolean doIncrementMethodCount(String key, int[] vec) {
		if (key == null || vec == null || vec.length == 0)
			throw new IllegalArgumentException();
		
		if (this.methodNameToIndexMap.keySet().contains(key)) {
//			System.out.println("\tincrement count for " + key);
			
			int typeRefIndex = this.methodNameToIndexMap.get(key);
			int typeRefCount = vec[typeRefIndex];
			
			vec[typeRefIndex] = typeRefCount + 1;
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * 
	 * @param key
	 * @param vec
	 */
	private boolean doIncrementValueCount(String key, int[] vec) {
		if (key == null || vec == null || vec.length == 0)
			throw new IllegalArgumentException();
		
		if (this.valueToIndexMap.keySet().contains(key)) {
//			System.out.println("\tincrement count for " + key);
			
			int typeRefIndex = this.valueToIndexMap.get(key);
			int typeRefCount = vec[typeRefIndex];
			
			vec[typeRefIndex] = typeRefCount + 1;
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * 
	 * @param pdg
	 * @param s
	 * @return
	 */
	private int getOutNodeDegree(PDG pdg, Statement s) {
		if (pdg == null || s == null)
			throw new IllegalArgumentException();
		
		// WARNING we had to manually remove an assertion
		// in @link{com.ibm.wala.ipa.slicer.PDG} to prevent an
		// UnimplementedError Exception thrown by this method
		int outNodeDegree = pdg.getSuccNodeCount(s);
		
		return outNodeDegree;
	}
	
	/**
	 * 
	 * @param pdg
	 * @param s
	 * @param ssa
	 * @param maxOutDegreeVec
	 */
	private void updateMaxOutDegreeVec(PDG pdg, Statement s, SSAInstruction ssa, int[] maxOutDegreeVec) {
		int type;
		
		try {
			type = this.resolveInstructionType(ssa);
			int maxOutDegree = maxOutDegreeVec[type];
			int outNodeDegree = this.getOutNodeDegree(pdg, s);
			
			if (outNodeDegree > maxOutDegree) 
				maxOutDegreeVec[type] = outNodeDegree;
		} catch (Exception e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
		}
	}
	
	/**
	 * Get direct offsprings from a node
	 * @param pdg
	 * @param s
	 * @return
	 */
	private List<Statement> getOutNodes(PDG pdg, Statement s) {
		if (pdg == null || s == null)
			throw new IllegalArgumentException();
		
		List<Statement> outNodes = new ArrayList<Statement>();
		for (Iterator<Statement> succIt = pdg.getSuccNodes(s); succIt.hasNext();) {
			outNodes.add(succIt.next());
		}
		
		return outNodes;
	}
	/**
	 * Get number of all reachable direct and indirect offsprings
	 * @param pdg
	 * @param s
	 */
	private int getOffspringCount(PDG pdg, Statement s) {
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
	 * @param pdg
	 */
	private void buildStatementToInstructionMap(PDG pdg) {
		if (pdg == null) 
			throw new IllegalArgumentException();
		
		this.statementToInstrMap = new HashMap<Statement, SSAInstruction>();
		
		for(Iterator<Statement> pdgIt = pdg.iterator(); pdgIt.hasNext();) {
			Statement s = pdgIt.next();
			if (s.getKind().equals(Statement.Kind.NORMAL) 
					/*|| s.getKind().equals(Statement.Kind.EXC_RET_CALLER)*/
					|| s.getKind().equals(Statement.Kind.PARAM_CALLER) 
					|| s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)) {
				StatementWithInstructionIndex ns = (StatementWithInstructionIndex) s;
				this.statementToInstrMap.put(s, ns.getInstruction());
			} else {
//				System.out.println("ignoring non normal statement " + s.toString());
			}
		}
	}
		
	/**
	 * 
	 * @return
	 */
	public final List<int[]> getSemanticVectorList() {
		return this.semanticVectorList;
	}
	
	public HashMap<String, Integer> getValueToIndexMap() {
		return valueToIndexMap;
	}
	
	public void setValueToIndexMap(HashMap<String, Integer> valueToIndexMap) {
		this.valueToIndexMap = valueToIndexMap;
	}

	/**
	 * 
	 * @return
	 */
	public HashMap<String, Integer> getMethodNameToIndexMap() {
		return methodNameToIndexMap;
	}

	/**
	 * 
	 * @param methodNameToIndexMap
	 */
	public void setMethodNameToIndexMap(
			HashMap<String, Integer> methodNameToIndexMap) {
		this.methodNameToIndexMap = methodNameToIndexMap;
	}
	
	/**
	 * 
	 * @param semanticBlockList
	 * @return
	 * @throws Exception
	 */
	public int[] buildSemanticVector(List<ArrayList<Statement> > semanticBlockList, boolean isApp, Set<String> pkgNameSet, boolean isAdditional, CGNode n) throws Exception {
//		System.out.println("semantic block list size : " + semanticBlockList.size());
		
		if (semanticBlockList == null /*|| semanticBlockList.size() == 0*/) 
			throw new IllegalArgumentException("sbList empty");
		
//		if ((pkgNameSet == null || pkgNameSet.isEmpty()) && !isApp)
//			throw new IllegalArgumentException("pkgNameSet missing");
		
		if (this.semanticVectorList.size() != 0)
			this.semanticVectorList = new LinkedList<int[]>();
			
		this.hasAllowAllHost = false;
		this.hasStrictHost = false;
		
		int constInfoCount = 0;
		int pkgCount = 0;
//		int pkgCount = DescriptorSet.getPkgWhitelistLength();
		
		int[] statementTypeVec = new int[SemanticVector.INSTR_TYPE_COUNT];
		int[] maxOutDegreeVec = new int[SemanticVector.INSTR_TYPE_COUNT];
		
		int instructionTypeSize = 2 * SemanticVector.INSTR_TYPE_COUNT;
		
		int semanticVecLength = instructionTypeSize + pkgCount +  constInfoCount;
		int[] semanticVec = new int[semanticVecLength];
		
//		System.out.println(n.getMethod().getSignature());
		
		for (Iterator<ArrayList<Statement>> smlIt = semanticBlockList.iterator(); smlIt.hasNext();) {
			List<Statement> sml = smlIt.next();
			
//			for (Statement s : sml) {
//				System.out.println("\t" + s);
//			}
//			System.out.println("semantic block size: " + sml.size());
			
//			DescriptorSet ds = new DescriptorSet();
//			Map<String, Integer> packageToIndexMap = ds.getPkgWhitelistSet();
//			Set<String> packageSet = new HashSet<String>();
//
//			if (isApp && !isAdditional) {
//				ds.buildPackageNameSet(sml);
//				packageSet = ds.getPackageNameSet();
//			} else if (!isAdditional) {
//				packageSet = pkgNameSet;
//			}
////			System.out.println("package set: " + packageSet);
//			
//			// critical since we filter out methods which do not use whitelisted pkgs
//			String signature = n.getMethod().getSignature();
//			if (packageSet.isEmpty() && !isAdditional 
//					&& !signature.contains("checkClientTrusted") && !signature.contains("checkServerTrusted") && !signature.contains("getAcceptedIssuers")) {
////				System.err.println("Continue with next block");
//				continue;
//			} else {
////				System.out.println("found package");
//				semanticVec = new int[semanticVecLength];
////				System.out.println("semantic block contains whitelisted pkg");
////				System.out.println(packageSet);
//			}
			
//			System.out.println("building new semantic vector");	
//			semanticVec = new int[semanticVecLength];
			
			boolean foundInstructionType = false;
			for (Iterator<Statement> smIt = sml.iterator(); smIt.hasNext();) {
				Statement sm = smIt.next();
//				System.out.println("\t" + sm);
				
				if (sm.getKind().equals(Statement.Kind.NORMAL) 
						/*|| sm.getKind().equals(Statement.Kind.EXC_RET_CALLER)*/	// TODO delete condition since we do not store EXC_RET_CALLER anymore
						|| sm.getKind().equals(Statement.Kind.PARAM_CALLER) 
						|| sm.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)) {
//					System.out.println("\t" + ((StatementWithInstructionIndex) sm).getInstruction().toString());
					
					SSAInstruction ssa = this.statementToInstrMap.get(sm);
					if (ssa == null) {
						// TODO this quits the sv creation for the given app, catch exception earlier
						System.err.println("unknown statement");
						throw new Exception("unknown statement " + sm.toString());
					}
					
					if (this.incrementInstructionTypeCount(ssa, statementTypeVec)) {
						// critical since this decides wether to include a sb in the sv
						foundInstructionType = true;
//						System.out.println("\t\tFound type");
					} else {
//						System.out.println("\t\tType not found for " + ssa.getClass().getName());
					}
					this.updateMaxOutDegreeVec(pdg, sm, ssa, maxOutDegreeVec);
				} 
			}
			
//			IR ir = n.getIR();
//			ValueSet vs = new ValueSet();
//			vs.buildValueSetFromStatements(sml, ir);
//			this.valueSet = vs.getValueSet();
			
//			int maxInt = vs.getMaxInt();
//			int maxStringLength = vs.getMaxStringLength();
			
//			int intConstantCount = vs.getIntConstantCount();
//			int stringConstantCount = vs.getStringConstantCount();
			
			if (!(foundInstructionType /* || foundMethodName || foundConstValue*/)) {
//				System.out.println("nothing found, continue");
				continue;
			} else {
//				System.out.println("semantic block");
//				for (Statement s : sml) {
//					System.out.println("\t" + s.toString());
//				}
			}			
			for (int i = 0; i < semanticVec.length; i++) {
				if (i < statementTypeVec.length)
					semanticVec[i] = statementTypeVec[i];
			}
			
			for (int i = SemanticVector.INSTR_TYPE_COUNT; i < instructionTypeSize; i++) {
				int j = i - SemanticVector.INSTR_TYPE_COUNT;
				if (j >= 0 && j < SemanticVector.INSTR_TYPE_COUNT)
					semanticVec[i] = maxOutDegreeVec[j];
			}
			
//			for (String pkgName : packageSet) {
//				Integer index = packageToIndexMap.get(pkgName);
//				if (index != null)
//					if (instructionTypeSize + index < semanticVecLength) {
//						semanticVec[instructionTypeSize + index] = 1;
////						System.out.println("setting package index: " + (instructionTypeSize + index));
//					}
//				
////				System.out.println("set 1 at " + index + " for " + pkgName);
//			}
			
			// so ugly
//			semanticVec[semanticVecLength - constInfoCount] = maxInt;
//			semanticVec[semanticVecLength - constInfoCount + 1] = maxStringLength;
			
//			int currIntConstCount = semanticVec[semanticVecLength - constInfoCount];
//			int currStringConstCount = semanticVec[semanticVecLength - constInfoCount + 1];
//			semanticVec[semanticVecLength - constInfoCount] = currIntConstCount + intConstantCount;
//			semanticVec[semanticVecLength - constInfoCount + 1] = currStringConstCount  + stringConstantCount;
			
//			this.semanticVectorList.add(semanticVec);
		}
		
		return semanticVec;
	}
	
	public HashMap<Integer, Integer> getNonZeroIndecesToCountMap(int[] semanticVector) {
		if (semanticVector == null)
			throw new IllegalArgumentException();

		HashMap<Integer, Integer> nonZeroIndecesToCountMap = new HashMap<Integer, Integer>();
		for (int i = 0; i < semanticVector.length; i++) {
			if (semanticVector[i] != 0)
				nonZeroIndecesToCountMap.put(i, semanticVector[i]);
		}
		
		return nonZeroIndecesToCountMap;
	}
}