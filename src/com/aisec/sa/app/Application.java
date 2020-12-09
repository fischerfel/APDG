package com.aisec.sa.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math.linear.RealVector;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.aisec.sa.DescriptorSet;
import com.aisec.sa.SemanticBlock;
import com.aisec.sa.SemanticVector;
import com.aisec.sa.ValueSet;
import com.aisec.sa.apdg.APDG;
import com.aisec.sa.io.APDGIO;
import com.aisec.sa.io.FeatureReader;
import com.aisec.sa.io.JavaFileAggregator;
import com.aisec.sa.io.Label.Type;
import com.aisec.sa.io.SemanticVectorIO;
import com.aisec.sa.io.SliceIO;
import com.aisec.sa.io.SnippetParser;
import com.aisec.sa.io.SnippetToJavaFileCreator;
import com.aisec.sa.util.Logger;
import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.cast.java.ipa.modref.AstJavaModRef;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.client.AbstractAnalysisEngine.EntrypointBuilder;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;

/**
 * This class controls all aspects of the application's execution
 */
public class Application implements IApplication {

	private static final String snippetDirectoryAbsPathArgKey = "-snippets";

	private static final String appDirectoryAbsPathArgKey = "-apps";

	private static final String snippetsOutputDirectoryAbsPathArgKey = "-snippetresults";

	private static final String appsOutputDirectoryAbsPathArgKey = "-appresults";

	private static final String tmpJavaFileOutputDirectoryAbsPathArgKey = "-tmp";

	private static final String eclipseProjectNameArgKey = "-project";

	private static final String srcPath = "/src";

	private static final String SOURCE = "Source";

	private static final String PRIMORDIAL = "Primordial";

	private String javaHomeAbsPath;

	private String walaExclusionFileAbsPath;

	private String androidJarAbsPath;

	private String appJarAbsPath;

	private String outputDirectorySnippetsAbsPath;

	private String outputDirectoryAppsAbsPath;

	private String projectName;

	private List<String> rtJar;

	private String snippetFileName;

	private Set<String> argsKeySet;

	private ValueSet valueSet;

	private DescriptorSet methodNameSet;

	private HashMap<String, Integer> methodNameToIndexMap;

	private HashMap<String, Integer> valueToIndexMap;

	public int cgNodeCounter;

	private String command;

	private String verbosity;

	private String signaturesFileAbsPath;

	private String constantsFileAbsPath;

	private String label;

	class ThreadPerTaskExecutor implements Executor {
	     @Override
		public void execute(Runnable r) {
	         r.run();
	     }
	 }

	private void initJavaHome() {
		this.rtJar = new LinkedList<String>();

		if ("Mac OS X".equals(System.getProperty("os.name"))) {
			rtJar.add(javaHomeAbsPath + "/classes.jar");
			rtJar.add(javaHomeAbsPath + "/ui.jar");
		} else {
//			rtJar.add(javaHomeAbsPath + File.separator + "classes.jar");
			rtJar.add(javaHomeAbsPath + File.separator + "rt.jar");
//			rtJar.add(javaHomeAbsPath + File.separator + "core.jar");
//			rtJar.add(javaHomeAbsPath + File.separator + "vm.jar");
		}
	}

	private void initArgsKeySet() {
		this.argsKeySet = new HashSet<String>();

		this.argsKeySet.add("-snippets");
		this.argsKeySet.add("-apps");
		this.argsKeySet.add("-projects");
		this.argsKeySet.add("-tmp");
		this.argsKeySet.add("-snippetresults");
		this.argsKeySet.add("-appresults");
		this.argsKeySet.add("-project");
		this.argsKeySet.add("-javahome");
		this.argsKeySet.add("-exclusions");
		this.argsKeySet.add("-android");
		this.argsKeySet.add("-minsbsize");
		this.argsKeySet.add("-command");
		this.argsKeySet.add("-verbosity");
		this.argsKeySet.add("-signatures");
		this.argsKeySet.add("-constants");
		this.argsKeySet.add("-label");
	}

	private HashMap<String, String> getArgsMap(String[] args) throws IllegalArgumentException {
		if (args == null || args.length == 0)
			throw new IllegalArgumentException("command line args empty");

		HashMap<String, String> argsMap = new HashMap<String, String>();
		for (int i = 0; i + 1 < args.length; i = i + 2) {
			String key = args[i];
			if (!this.argsKeySet.contains(key))
				throw new IllegalArgumentException("unknown command line arg: " + key);

			String value = args[i + 1];
			argsMap.put(key, value);
		}

		return argsMap;
	}

	private static void createProject(final String projectName) throws CoreException {
		// create a new project with the given name
//		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
//		IProject project = root.getProject(projectName);
//		project.create(null);
//		project.open(null);
//
//		IProjectDescription description = project.getDescription();
//		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
//		project.setDescription(description, null);
//
//		IJavaProject javaProject = JavaCore.create(project);

		IWorkspaceRunnable workspaceRunnable = 
			new IWorkspaceRunnable() {
				
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					// TODO Auto-generated method stub
					// create a new project with the given name
					IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
					IProject project = root.getProject(projectName);
					project.create(null);
					project.open(null);

					IProjectDescription description = project.getDescription();
					description.setNatureIds(new String[] { JavaCore.NATURE_ID });
					project.setDescription(description, null);

					IJavaProject javaProject = JavaCore.create(project);
					IFolder binFolder = project.getFolder("bin");
					binFolder.create(false, true, null);
					javaProject.setOutputLocation(binFolder.getFullPath(), null);

					IFolder sourceFolder = project.getFolder(srcPath);
					sourceFolder.create(false, true, null);
				}
			};
		
		ResourcesPlugin.getWorkspace().run(workspaceRunnable, null);
//		IFolder binFolder = project.getFolder("bin");
//		binFolder.create(false, true, null);
//		javaProject.setOutputLocation(binFolder.getFullPath(), null);
//
//		IFolder sourceFolder = project.getFolder(srcPath);
//		sourceFolder.create(false, true, null);
	}

	private void copySourceFileToWorspace(String sourceFileAbsPath, String javaFileName) throws CoreException, FileNotFoundException {
		IWorkspaceRoot myWorkspaceRoot = ResourcesPlugin.getWorkspace()
				.getRoot();

		IProject project = myWorkspaceRoot.getProject(this.projectName);
		// open if necessary
		if (project.exists() && !project.isOpen())
			project.open(null);

		IFolder srcFolder = project.getFolder(srcPath);
		if (srcFolder.exists()) {
			try {
				// create a new file
				IFile newSource = srcFolder.getFile(this.snippetFileName);
				if (newSource.exists())
					newSource.delete(true, null);

				FileInputStream fileStream = new FileInputStream(sourceFileAbsPath);
				newSource.create(fileStream, false, null);
				// create closes the file stream, so no worries.
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	private static IProject copySourceFilesToWorkspace(String javaFileDirectoryPath) throws CoreException {
		Path path = new Path(javaFileDirectoryPath);
		path = (Path) path.removeFileExtension();
		String projectName = path.lastSegment();
		Logger.log(projectName);

		Application.createProject(projectName);

		IWorkspaceRoot myWorkspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

		IProject project = myWorkspaceRoot.getProject(projectName);
		// open if necessary
		if (project.exists() && !project.isOpen())
			project.open(null);

		IFolder srcFolder = project.getFolder(srcPath);
		if (srcFolder.exists()) {
			Collection<File> files = JavaFileAggregator.getFiles(javaFileDirectoryPath);
			if (files == null)
				return null;

			for (File file : files) {
				IFile newSource = srcFolder.getFile(file.getName());
				if (newSource.exists())
					newSource.delete(true, null);

				try {
					SnippetToJavaFileCreator jfc = 
						new SnippetToJavaFileCreator(file.getAbsolutePath(), file.getParent(), file.getName(), null);
					jfc.createJavaFile();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				try {
					FileInputStream fileStream = new FileInputStream(file);
					newSource.create(fileStream, false, null);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
		return project;
	}

	private Collection<String> singleSrc(String sourcePath) {
		return Collections.singletonList(sourcePath);
	}

	private Collection<String> singleTestSrc() {
		return Collections.singletonList(srcPath + File.separator + snippetFileName);
	}

	private static void populateScope(String projectName,
			JDTJavaSourceAnalysisEngine engine, Collection<String> sources,
			List<String> libs) throws IOException {

		boolean foundLib = false;
		for (String lib : libs) {
			File libFile = new File(lib);
			if (libFile.exists()) {
				foundLib = true;
				engine.addSystemModule(new JarFileModule(new JarFile(libFile)));
			}
		}
		assert foundLib : "couldn't find library file from " + libs;

		for (String srcFilePath : sources) {
			engine.addSourceModule(srcFilePath);
		}
	}

	private JDTJavaSourceAnalysisEngine getJDTJavaSourceAnalysisEngine(final String projectName,
			final String[] mainClassDescriptors, final String exclFilePath) throws IOException {

		return this.getJDTJavaSourceAnalysisEngine(projectName, singleTestSrc(), exclFilePath);
	}

	private JDTJavaSourceAnalysisEngine getJDTJavaSourceAnalysisEngine(final String projectName,
			Collection<String> srcFiles, final String exclFilePath) throws IOException {
		if (projectName == null || srcFiles == null || exclFilePath == null)
			throw new IllegalArgumentException();

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
	    IWorkspaceRoot root = workspace.getRoot();
	    IJavaProject project = JavaCore.create(root.getProject(projectName));

		JDTJavaSourceAnalysisEngine engine = new JDTJavaSourceAnalysisEngine(project);/* {
			@Override
			protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
				System.out.println("building default entrypoints");

				return Util.makeMainEntrypoints(JavaSourceAnalysisScope.SOURCE, cha, mainClassDescriptors);
			}
		};*/

		engine.setExclusionsFile(exclFilePath);

		populateScope(projectName, engine, srcFiles, rtJar);

		return engine;
	}

	private JDTJavaSourceAnalysisEngine getJDTJavaSourceAnalysisEngine(final String projectName,
			Collection<String> srcFiles, final String exclFilePath, List<String> primordialModules) throws IOException {
		if (projectName == null || srcFiles == null || exclFilePath == null)
			throw new IllegalArgumentException();

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
	    IWorkspaceRoot root = workspace.getRoot();
	    IJavaProject project = JavaCore.create(root.getProject(projectName));

		JDTJavaSourceAnalysisEngine engine = new JDTJavaSourceAnalysisEngine(project);/* {
			@Override
			protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
				System.out.println("building default entrypoints");

				return Util.makeMainEntrypoints(JavaSourceAnalysisScope.SOURCE, cha, mainClassDescriptors);
			}
		};*/

		engine.setExclusionsFile(exclFilePath);

		populateScope(projectName, engine, srcFiles, primordialModules);

		return engine;
	}

	private EntrypointBuilder getEntrypointBuilderForSnippet(final String classLoaderReference) {
		assert (classLoaderReference == "Source") || (classLoaderReference == "Primordial");

		EntrypointBuilder eb = new EntrypointBuilder() {

			@Override
			public Iterable<Entrypoint> createEntrypoints(AnalysisScope scope,
					IClassHierarchy cha) {
//				System.out.println("building entrypoints for snippet");
				Iterator<IClass> classes = cha.iterator();

				Collection<Entrypoint> entryPoints = new LinkedList<Entrypoint>();
				while (classes.hasNext()) {
					IClass c = classes.next();

					// we want all possible entrypoints of the given snippet
					// ignore all class loader that dont have a source reference
					if (c.getClassLoader().getReference().getName().toString().equals(classLoaderReference)) {
//						System.out.println("\tfound source reference: " + c.getName());
						for (IMethod m : c.getDeclaredMethods()) {
							if (m.isClinit() /*|| m.isInit()*/) {
								continue;
							}
							try {
								entryPoints.add(new DefaultEntrypoint(m, cha));
//								System.out.println("\tsetting entrypoint for snippet: " + m.getSignature());
							} catch (Exception e) {
								// Weird exceptions occur, either due to Java 6, PPA or a bug in WALA
								e.printStackTrace();
							}
						}
					}
				}
				return entryPoints;
			}
		};

		return eb;
	}

	private Collection<Entrypoint> getEntrypointsForAndroidApp(Collection<Entrypoint> entrypoints, AnalysisScope scope, IClassHierarchy cha) {
		if (scope == null || cha == null)
			throw new IllegalArgumentException();

		Iterator<IClass> classes = cha.iterator();

		Collection<Entrypoint> returnedEntrypoints = new HashSet<Entrypoint>();

		while (classes.hasNext()) {
			IClass c = classes.next();

			if (!scope.isApplicationLoader(c.getClassLoader()))
				continue;

			Collection<? extends IClass> implementedInterfaces = c.getDirectInterfaces();
			Set<String> possibleOverrides = HashSetFactory.make();
//			for (IClass clazz : implementedInterfaces) {
//				/*
//				 * only care about overrides from primordial scope. overrides of
//				 * these methods may be directly callable by the android system.
//				 * if this method is an override of a method in the Application
//				 * scope, we should figure out that it is a potential event
//				 * handler in some other way
//				 */
//				if (clazz.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
//					for (IMethod m : clazz.getAllMethods()) {
//						if (!m.isInit() && !m.isStatic()) {
//							possibleOverrides.add(m.getName().toString()+ m.getDescriptor().toString());
//						}
//					}
//				}
//			}

			for (Entrypoint e : entrypoints) {
				for (IMethod m : c.getDeclaredMethods()) {
					/*
					 * for each method defined in the class for (IMethod m :
					 * c.getAllMethods()) { for each method defined in the class
					 * if this method has a name that looks like an event
					 * handler...
					 */
					IMethod eMethod = e.getMethod();
					if (m.getSignature().equals(eMethod.getSignature())
							/* ||handlers.contains(m.getName().toString()) */
							// ... or this method was declared as a custom handler
							/* || possibleOverrides.contains(eMethod.getName().toString() + eMethod.getDescriptor().toString())*/) {
						if (c.getClassLoader().getReference().equals(ClassLoaderReference.Application));

						returnedEntrypoints.add(new DefaultEntrypoint(m, cha));
						Logger.log("Entrypoint " + m.getSignature() + " relates to " + eMethod.getSignature());
					}
				}
			}
		}

		return returnedEntrypoints;
	}

	private Collection<Entrypoint> getEntrypointsForAndroidApp(AnalysisScope scope, IClassHierarchy cha) {
		if (scope == null || cha == null)
			throw new IllegalArgumentException();

		Iterator<IClass> classes = cha.iterator();

		Collection<Entrypoint> entryPoints = new LinkedList<Entrypoint>();

		while (classes.hasNext()) {
			IClass c = classes.next();
			// only application methods should be entrypoints
			for (IMethod m : c.getDeclaredMethods()) {
				if (m.getSignature().contains("clinit")) {
//					System.out.println(m.getSignature());
				}
			}

			if (!scope.isApplicationLoader(c.getClassLoader()))
				continue;

			Collection<? extends IClass> implementedInterfaces = c.getDirectInterfaces();
			Set<String> possibleOverrides = HashSetFactory.make();
			for (IClass clazz : implementedInterfaces) {
				/*
				 * only care about overrides from primordial scope. overrides of
				 * these methods may be directly callable by the android system.
				 * if this method is an override of a method in the Application
				 * scope, we should figure out that it is a potential event
				 * handler in some other way
				 */
				if (clazz.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
					for (IMethod m : clazz.getAllMethods()) {
						if (!m.isInit() && !m.isStatic()) {
							possibleOverrides.add(m.getName().toString()+ m.getDescriptor().toString());
						}
					}
				}
			}
			for (IMethod m : c.getDeclaredMethods()) {
				/*
				 * for each method defined in the class for (IMethod m :
				 * c.getAllMethods()) { for each method defined in the class if
				 * this method has a name that looks like an event handler...
				 *
				 */
				String methodSignature = m.getSignature();
				if (methodSignature.contains("checkServerTrusted") || methodSignature.contains("checkClientTrusted")
						|| methodSignature.contains("getAcceptedIssuers")) {
					entryPoints.add(new DefaultEntrypoint(m, cha));
				} else if (((m.isPublic() || m.isProtected()) && m.getName().toString().startsWith("on"))
						/* ||handlers.contains(m.getName().toString()) */
						// ... or this method was declared as a custom handler
						|| possibleOverrides.contains(m.getName().toString() + m.getDescriptor().toString())) {
					// or this method is an override of an interface method
					assert (c.getClassLoader().getReference().equals(ClassLoaderReference.Application));

					entryPoints.add(new DefaultEntrypoint(m, cha));

//					 entryPoints.add(new SameReceiverEntrypoint(m, cha));
				} else if (c.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
					if (m.getSignature().contains("<clinit>") || m.getSignature().contains("<init>")) {
//						System.out.println(m.getSignature());
						entryPoints.add(new DefaultEntrypoint(m, cha));
					}
				}
//				}
			}
		}

		return entryPoints;
	}

	private Set<IClass> getPotentialClassNames(CallGraph cg, SDG sdg) {
		Set<IClass> classSet = new HashSet<IClass>();

		List<MethodReference> mrList = new LinkedList<MethodReference>();
		for (Iterator<CGNode> itCg = cg.iterator(); itCg.hasNext();) {

			CGNode n = itCg.next();
			IR ir = n.getIR();
			if (ir == null)
				continue;

			for (Iterator<SSAInstruction> irIt = ir.iterateNormalInstructions(); irIt.hasNext();) {
				SSAInstruction ssa = irIt.next();
				if (ssa instanceof SSAInvokeInstruction) {
					SSAInvokeInstruction inv = (SSAInvokeInstruction) ssa;
					mrList.add(inv.getCallSite().getDeclaredTarget());
//					System.out.println(inv.getCallSite().getDeclaredTarget());
				}
			}

//			PDG pdg = sdg.getPDG(n);
//
//			List<Statement> statementList = new ArrayList<Statement>();
//			for (Iterator<Statement> it = pdg.iterator(); it.hasNext();) {
//				Statement s = it.next();
//
//				if (s.getKind().equals(Statement.Kind.NORMAL)
//				/* || s.getKind().equals(Statement.Kind.EXC_RET_CALLER) */
//				|| s.getKind().equals(Statement.Kind.NORMAL_RET_CALLER)
//						|| s.getKind().equals(Statement.Kind.PARAM_CALLER)) {
//					statementList.add(s);
//				}
//
//				DescriptorSet ds = new DescriptorSet();
//
//				ds.buildPackageNameSet(statementList);
//				Set<String> packageSet = ds.getPackageNameSet();
//
//				if (packageSet != null && packageSet.size() > 0) {
//					classSet.add(n.getMethod().getDeclaringClass());
////					System.out.println(n.getMethod().getDeclaringClass());
//				}
//			}
		}

		return classSet;
	}

	private void createSemanticVectorFromCodeSnippet(Set<String> pkgNameSet, String outputFileName, int minSize) throws Exception {
		JDTJavaSourceAnalysisEngine engine =
			this.getJDTJavaSourceAnalysisEngine(projectName, new String[] { "LFoo" }, walaExclusionFileAbsPath);

//		engine.buildAnalysisScope();

//		System.out.println("building class hierarchy");
//		IClassHierarchy cha = engine.buildClassHierarchy();

//		System.out.println("building entrypoints");
		engine.setEntrypointBuilder(this.getEntrypointBuilderForSnippet(SOURCE));

//		System.out.println("building call graph");
		CallGraph cg = engine.buildDefaultCallGraph();

		List<CGNode> cgNodes = new LinkedList<CGNode>();
		for (CGNode n : cg.getEntrypointNodes()) {
			cgNodes.add(n);
//			System.out.println("\tadded entrypoint node " + n.getMethod().getName());
		}

//		System.out.println("building pointer analysis");
		PointerAnalysis pa = engine.getPointerAnalysis();

//		System.out.println("building sdg");
//		ModRef jmr = new AstJavaModRef();
		SDG sdg = new SDG(cg, pa, new AstJavaModRef(), DataDependenceOptions.NO_BASE_NO_HEAP, ControlDependenceOptions.NONE);

		String outputFilesPath = this.outputDirectorySnippetsAbsPath + File.separator + outputFileName;
		SemanticVectorIO svio = new SemanticVectorIO(outputFilesPath + ".svs", outputFilesPath + ".cgn");

		int vecId = 0;
		JSONArray nodes = new JSONArray();

		HashMap<CGNode, List<String> > nodeToConstMap = new HashMap<CGNode, List<String> >();

//		System.out.println("building semantic blocks");
		for (CGNode n : cgNodes) {

//			System.out.println("building constant list " + n.getMethod().getSignature());

			List<String> constants = new ArrayList<String>();

			SymbolTable st = n.getIR().getSymbolTable();
//			DefUse du = new DefUse(n.getIR());
			for (int i = 1; i <= st.getMaxValueNumber(); i++) {
				if (st.isConstant(i)) {
					String valueString = st.getValueString(i);
					int separator = valueString.indexOf(":");
					String value = valueString.substring(separator + 1);
					if (!(value == null || value.equals("#") || value.equals("#null") || value.equals("#0") || value.contains("#continueLabel") || value.contains("#breakLabel"))) {
						constants.add(value);
					}
				}
			}

			String methodName = n.getMethod().getName().toString();
			if (methodName.contains("<init>")) {
				if (constants.isEmpty()) {
//					System.out.println("Continue: " + n.getMethod().getSignature());
					continue;
				}

			} else if (methodName.contains("<clinit>")) {
				continue;	// we check clinit in the second rund
//				if (constants.isEmpty()) {
////					System.out.println("Continue: " + n.getMethod().getSignature());
//					continue;
//				}
			}

			nodeToConstMap.put(n, constants);

//			if (sign.contains("checkServerTrusted")
//					|| sign.contains("checkClientTrusted")
//					|| sign.contains("getAcceptedIssures")) {
//				if (constants.size() == 1)
//					constants = new LinkedList<String>();
//			}
//				if (du.getDef(i) == null) {
////					System.out.println("\tv" + i + " is const or param");
//					System.out.println("\9t\t" + st.getValueString(i));
//					System.out.println("\t\tused by:");
//					for (Iterator<SSAInstruction> it = du.getUses(i); it.hasNext();) {
//						SSAInstruction ssa = it.next();
//						System.out.println("\t\t" + ssa.toString(st));
//					}
//				} else {
//					System.out.println("\tv" + i + " is a defined variable");
//					System.out.println("\t\t" + du.getDef(i).toString(st));
//					System.out.println("\t\tused by");
//					for (Iterator<SSAInstruction> it = du.getUses(i); it.hasNext();) {
//						SSAInstruction ssa = it.next();
//						System.out.println("\t\t" + ssa.toString(st));
//					}
//				}
//			}


			SemanticBlock sb = new SemanticBlock(n, sdg, minSize);
			sb.buildSemanticBlocks();

			SemanticVector sv = new SemanticVector(n, sb.getPdg()/*, this.methodNameSet, this.valueSet,
					this.methodNameToIndexMap, this.valueToIndexMap*/);

//			System.out.println("iterating new semantic vector");
			int[] svArray;
			try {
				svArray = sv.buildSemanticVector(sb.getSemanticBlocks(), false, pkgNameSet, false, n);
				if (svArray == null) {
//					System.out.println("sv array is null continue " + n.getMethod().getSignature());
					continue;
				}

				DescriptorSet ds = new DescriptorSet();
				ds.buildMethodAndTypeNameSet(sb.getSemanticBlocks());
				Set<String> methodNameSet = ds.getMethodNameSet();
				Set<String> typeNameSet = ds.getTypeNameSet();
//				Set<String> valueSet = sv.getSetWithValues();

				if (sv.hasAllowAllHost()) {
					constants.add("ALLOW_ALL_HOSTNAME_VERIFIER");
//					System.out.println("ALLOW_ALL_HOSTNAME_VERIFIER");
				} else if (sv.hasStrictAllowHost()) {
					constants.add("STRICT_HOSTNAME_VERIFIER");
				}

				if (svArray != null && svArray.length != 0) {
					svio.writeToGZIPVecFile(svArray, n, true);

					JSONObject node = svio.createJSON(vecId, svArray, n, methodNameSet, typeNameSet, constants);
					if (node != null)
						nodes.add(node);
//					svio.writeToNodesFile(true, vecId, svArray, n, methodNameSet, typeNameSet, valueSet);

//				System.out.println(n.getMethod().getSignature());
//				System.out.println(svArray);
//				for (int[] svArray : svArray) {
//					List<Integer> svList = new ArrayList<Integer>();
//					for (int i = 0; i < svArray.length; i++) {
//						svList.add(svArray[i]);
//					}
//				}
//					System.out.println(svList);
////					 System.out.println(sv.getNonZeroIndecesToCountMap(svArray));
//				}

					vecId++;
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

//			ThreeDCFG threedCFG = new ThreeDCFG(n.getIR(), null);
//			threedCFG.iterateBasicBlocks();
		}

		// second run ***********************************************************************

		for (CGNode n : cgNodes) {

			String sign = n.getMethod().getName().toString();
			if (!sign.contains("<clinit>")) {
				continue; // we check only clinit
			}

			// System.out.println("building constant list " +
			// n.getMethod().getSignature());

			List<String> constants = new ArrayList<String>();
			SymbolTable st = n.getIR().getSymbolTable();
			// DefUse du = new DefUse(n.getIR());
			for (int i = 1; i <= st.getMaxValueNumber(); i++) {
				if (st.isConstant(i)) {
					String valueString = st.getValueString(i);
					int separator = valueString.indexOf(":");
					String value = valueString.substring(separator + 1);
					if (!(value == null || value.equals("#")
							|| value.equals("#null") || value.equals("#0")
							|| value.contains("#continueLabel") || value
							.contains("#breakLabel"))) {
						constants.add(value);
					}
				}
			}

			if (constants.isEmpty()) {
				// System.out.println("Continue: " + n.getMethod().getSignature());
				continue;
			}

			Set<String> constSet = new HashSet<String>(constants);
			Set<String> alreadyFoundConstSet = new HashSet<String>();
			if (nodeToConstMap.values() != null) {
				for (List<String> foundConstList : nodeToConstMap.values()) {
					if (foundConstList != null) {
						for (String foundConst : foundConstList) {
							alreadyFoundConstSet.add(foundConst);
						}
					}
				}
			}
			constSet.removeAll(alreadyFoundConstSet);
			constants = new ArrayList<String>(constSet);

//			nodeToConstMap.put(n, constants);

			// if (sign.contains("checkServerTrusted")
			// || sign.contains("checkClientTrusted")
			// || sign.contains("getAcceptedIssures")) {
			// if (constants.size() == 1)
			// constants = new LinkedList<String>();
			// }
			// if (du.getDef(i) == null) {
			// // System.out.println("\tv" + i + " is const or param");
			// System.out.println("\9t\t" + st.getValueString(i));
			// System.out.println("\t\tused by:");
			// for (Iterator<SSAInstruction> it = du.getUses(i); it.hasNext();)
			// {
			// SSAInstruction ssa = it.next();
			// System.out.println("\t\t" + ssa.toString(st));
			// }
			// } else {
			// System.out.println("\tv" + i + " is a defined variable");
			// System.out.println("\t\t" + du.getDef(i).toString(st));
			// System.out.println("\t\tused by");
			// for (Iterator<SSAInstruction> it = du.getUses(i); it.hasNext();)
			// {
			// SSAInstruction ssa = it.next();
			// System.out.println("\t\t" + ssa.toString(st));
			// }
			// }
			// }

			SemanticBlock sb = new SemanticBlock(n, sdg, minSize);
			sb.buildSemanticBlocks();

			SemanticVector sv = new SemanticVector(n, sb.getPdg()/*
																 * , this.
																 * methodNameSet
																 * ,
																 * this.valueSet
																 * , this.
																 * methodNameToIndexMap
																 * , this.
																 * valueToIndexMap
																 */);

			// System.out.println("iterating new semantic vector");
			int[] svArray;
			try {
				svArray = sv.buildSemanticVector(sb.getSemanticBlocks(), false,
						pkgNameSet, false, n);
				if (svArray == null) {
					// System.out.println("sv array is null continue " +
					// n.getMethod().getSignature());
					continue;
				}

				DescriptorSet ds = new DescriptorSet();
				ds.buildMethodAndTypeNameSet(sb.getSemanticBlocks());
				Set<String> methodNameSet = ds.getMethodNameSet();
				Set<String> typeNameSet = ds.getTypeNameSet();
				// Set<String> valueSet = sv.getSetWithValues();

				if (sv.hasAllowAllHost()) {
					constants.add("ALLOW_ALL_HOSTNAME_VERIFIER");
					// System.out.println("ALLOW_ALL_HOSTNAME_VERIFIER");
				} else if (sv.hasStrictAllowHost()) {
					constants.add("STRICT_HOSTNAME_VERIFIER");
				}

				if (svArray != null && svArray.length != 0) {
					svio.writeToGZIPVecFile(svArray, n, true);

					JSONObject node = svio.createJSON(vecId, svArray, n,
							methodNameSet, typeNameSet, constants);
					if (node != null)
						nodes.add(node);
					// svio.writeToNodesFile(true, vecId, svArray, n,
					// methodNameSet, typeNameSet, valueSet);

					// System.out.println(n.getMethod().getSignature());
					// System.out.println(svArray);
					// for (int[] svArray : svArray) {
					// List<Integer> svList = new ArrayList<Integer>();
					// for (int i = 0; i < svArray.length; i++) {
					// svList.add(svArray[i]);
					// }
					// }
					// System.out.println(svList);
					// //
					// System.out.println(sv.getNonZeroIndecesToCountMap(svArray));
					// }

					vecId++;
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// ThreeDCFG threedCFG = new ThreeDCFG(n.getIR(), null);
			// threedCFG.iterateBasicBlocks();
		}
		svio.writeToNodesFile(false, nodes);
		svio.closeSvOutput();
	}

	private void createSemanticVectorFromApp(String outputFileName, int minSize) throws Exception,
			ClassHierarchyException, IllegalArgumentException,
			CallGraphBuilderCancelException {
//		System.out.println("building scope");

		AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
		scope.addToScope(scope.getPrimordialLoader(), new JarFile(new File(androidJarAbsPath)));
		scope.addToScope(scope.getApplicationLoader(),new JarFile(appJarAbsPath));

		IClassHierarchy cha = ClassHierarchy.make(scope);

		Collection<Entrypoint> entryPoints = this.getEntrypointsForAndroidApp(scope, cha);

		AnalysisOptions options = new AnalysisOptions(scope, entryPoints);
		options.setReflectionOptions(ReflectionOptions.NO_METHOD_INVOKE);

		AnalysisCache cache = new AnalysisCache();

		CallGraphBuilder builder = null;
		CallGraph cg = null;
		try {
//			System.out.println("building 0-1 CFA call graph");
			builder = com.ibm.wala.ipa.callgraph.impl.Util
					.makeZeroOneContainerCFABuilder(options, cache, cha, scope);

			cg = builder.makeCallGraph(options, null);
		} catch (Exception e) {
//			System.out.println("0-1 CFA failed, trying RTA");
			builder = com.ibm.wala.ipa.callgraph.impl.Util
					.makeRTABuilder(options, cache, cha, scope);

			cg = builder.makeCallGraph(options, null);
		}

		PointerAnalysis pa = builder.getPointerAnalysis();

//		System.out.println("building sdg");
		SDG sdg = new SDG(cg, pa, DataDependenceOptions.NO_BASE_NO_HEAP, ControlDependenceOptions.NONE);

		String outFilesPath = this.outputDirectoryAppsAbsPath + File.separator + outputFileName;
		SemanticVectorIO svio = new SemanticVectorIO(outFilesPath + ".svs", outFilesPath + ".cgn");

		int vecId = 0;
		JSONArray nodes = new JSONArray();

		Set<IClass> foundClasses = new HashSet<IClass>();
		Set<String> foundMethods = new HashSet<String>();

//		System.out.println("building semantic vectors");
		for (Iterator<CGNode> cgnIt = cg.iterator(); cgnIt.hasNext();) {
			CGNode n = cgnIt.next();

//			if (n.getMethod().getSignature().contains("Crypto.<clinit>()"))
//				System.out.println(n.getMethod().getSignature());

			if (n.getMethod().getSignature().contains("com.ibm.wala.") || n.getMethod().getSignature().contains("com.google.android.")) {
				continue;
			}

			PDG pdg = sdg.getPDG(n);

			SemanticBlock sb = new SemanticBlock(n, sdg, minSize);
			sb.buildSemanticBlocks();

			SemanticVector sv = new SemanticVector(n, pdg/*, this.methodNameSet, this.valueSet,
					this.methodNameToIndexMap, this.valueToIndexMap*/);

			List<ArrayList<Statement> > sbList = sb.getSemanticBlocks();
			boolean found = false;
			for (List<Statement> semBlock : sbList) {
				DescriptorSet ds = new DescriptorSet();
				Map<String, Integer> packageToIndexMap = ds.getPkgWhitelistSet();
				Set<String> packageSet = new HashSet<String>();

				ds.buildPackageNameSet(semBlock);
				packageSet = ds.getPackageNameSet();

//				System.out.println("package set: " + packageSet);

				// critical since we filter out methods which do not use whitelisted pkgs
				String signature = n.getMethod().getSignature();
				if (!packageSet.isEmpty()
						|| signature.contains("checkClientTrusted") || signature.contains("checkServerTrusted") || signature.contains("getAcceptedIssuers")) {
					found = true;
				}
			}
			if (!found)
				continue;

			int[] svArray;
			try {
//				System.out.println("sv for " + n.getMethod().getSignature());
				svArray = sv.buildSemanticVector(sb.getSemanticBlocks(), true, null, false, n);
				if (svArray == null) {
//					System.out.println("sv null for " + n.getMethod().getSignature());
					continue;
				}

//				System.out.println("first " + n.getMethod().getSignature());
				IClass c = n.getMethod().getDeclaringClass();

				foundClasses.add(n.getMethod().getDeclaringClass());
				foundMethods.add(n.getMethod().getSignature());

//				System.out.println(n.getMethod().getSignature());
				IR ir = n.getIR();
				List<String> constants = new ArrayList<String>();

				if (ir != null) {
					SymbolTable st = ir.getSymbolTable();
					for (int i = 1; i <= st.getMaxValueNumber(); i++) {
						if (st.isConstant(i)) {
//							if (n.getMethod().getSignature().contains("Crypto.<clinit>()"))
//								System.out.println(st.getValueString(i));
							String valueString = st.getValueString(i);
							int separator = valueString.indexOf(":");
							String value = valueString.substring(separator + 1);
							if (value != null && !value.equals("#null")) {
								constants.add(value);
							}
						}
					}
				}

				DescriptorSet ds = new DescriptorSet();
				ds.buildMethodAndTypeNameSet(sb.getSemanticBlocks());

				Set<String> methodNameSet = ds.getMethodNameSet();
				Set<String> typeNameSet = ds.getTypeNameSet();
				Set<String>	valueSet = sv.getSetWithValues();
//				System.out.println(valueSet);

				if (sv.hasAllowAllHost()) {
					constants.add("ALLOW_ALL_HOSTNAME_VERIFIER");
//					System.out.println("ALLOW_ALL_HOSTNAME_VERIFIER");
				} else if (sv.hasStrictAllowHost()) {
					constants.add("STRICT_HOSTNAME_VERIFIER");
				}


				if (svArray != null && svArray.length != 0) {
					// svio.writeToFile(svArrayList, true);
					svio.writeToGZIPVecFile(svArray, n, true);

					JSONObject node = svio.createJSON(vecId, svArray, n, methodNameSet, typeNameSet, constants);
					if (node != null)
						nodes.add(node);
//					svio.writeToNodesFile(true, vecId, svArray, n, methodNameSet, typeNameSet, valueSet);

//					System.out.println(svArray);
//					System.out.println(n.getMethod().getSignature());
//					for (int[] svArray : svArrayList) {
//						List<Integer> svList = new ArrayList<Integer>();
//						for (int i = 0; i < svArray.length; i++) {
//							svList.add(svArray[i]);
//						}
//						System.out.println(svList);
//					}

					vecId++;

				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		for (Iterator<CGNode> cgnIt = cg.iterator(); cgnIt.hasNext();) {
			CGNode n = cgnIt.next();

//			if (n.getMethod().getSignature().contains("Crypto.<clinit>()"))
//				System.out.println(n.getMethod().getSignature());

			if (n.getMethod().getSignature().contains("com.ibm.wala.") || n.getMethod().getSignature().contains("com.google.android.")) {
				continue;
			}

			boolean possibleNestedClass = false;
			IClass possibleOwnerClass = null;
			for (Iterator<IClass> clIt = foundClasses.iterator(); clIt.hasNext();) {
				IClass clazz = clIt.next();
				if (n.getMethod().getDeclaringClass().getName().toString().contains(clazz.getName().toString())) {
					possibleNestedClass = true;
					possibleOwnerClass = clazz;
					break;
				}
			}

			if (possibleNestedClass && possibleOwnerClass != null) {
				if((!foundClasses.contains(possibleOwnerClass) || foundMethods.contains(n.getMethod().getSignature()))) {
					continue;
				}
			} else if((!foundClasses.contains(n.getMethod().getDeclaringClass()) || foundMethods.contains(n.getMethod().getSignature()))) {
				continue;
			}

//			System.out.println("second \t" + n.getMethod().getSignature());

			PDG pdg = sdg.getPDG(n);

			SemanticBlock sb = new SemanticBlock(n, sdg, minSize);
			sb.buildSemanticBlocks();

			SemanticVector sv = new SemanticVector(n, pdg/*, this.methodNameSet, this.valueSet,
					this.methodNameToIndexMap, this.valueToIndexMap*/);

			int[] svArray;
			try {
//				System.out.println("sv for " + n.getMethod().getSignature());
				svArray = sv.buildSemanticVector(sb.getSemanticBlocks(), true, null, true, n);
				if (svArray == null)
					continue;

//				System.out.println(n.getMethod().getSignature());
				IR ir = n.getIR();
				List<String> constants = new ArrayList<String>();

				if (ir != null) {
					SymbolTable st = ir.getSymbolTable();
					for (int i = 1; i <= st.getMaxValueNumber(); i++) {
//						if (n.getMethod().getSignature().contains("Crypto.<clinit>()"))
//							System.out.println(st.getValueString(i));
						if (st.isConstant(i)) {
//							if (n.getMethod().getSignature().contains("Crypto.<clinit>()"))
//								System.out.println(st.getValueString(i));
							String valueString = st.getValueString(i);
//							System.out.println(valueString);
							int separator = valueString.indexOf(":");
							String value = valueString.substring(separator + 1);
							if (value != null && !value.equals("#null")) {
								constants.add(value);
							}
						}
					}
				}

				DescriptorSet ds = new DescriptorSet();
				ds.buildMethodAndTypeNameSet(sb.getSemanticBlocks());

				Set<String> methodNameSet = ds.getMethodNameSet();
				Set<String> typeNameSet = ds.getTypeNameSet();
				Set<String>	valueSet = sv.getSetWithValues();
//				System.out.println(valueSet);

				if (sv.hasAllowAllHost()) {
					constants.add("ALLOW_ALL_HOSTNAME_VERIFIER");
//					System.out.println("ALLOW_ALL_HOSTNAME_VERIFIER");
				} else if (sv.hasStrictAllowHost()) {
					constants.add("STRICT_HOSTNAME_VERIFIER");
				}

				if (svArray != null && svArray.length != 0) {
					// svio.writeToFile(svArrayList, true);
					svio.writeToGZIPVecFile(svArray, n, true);

					JSONObject node = svio.createJSON(vecId, svArray, n, methodNameSet, typeNameSet, constants);
					if (node != null)
						nodes.add(node);
//					svio.writeToNodesFile(true, vecId, svArray, n, methodNameSet, typeNameSet, valueSet);

//					System.out.println(svArray);
//					System.out.println(n.getMethod().getSignature());
//					for (int[] svArray : svArrayList) {
//						List<Integer> svList = new ArrayList<Integer>();
//						for (int i = 0; i < svArray.length; i++) {
//							svList.add(svArray[i]);
//						}
//						System.out.println(svList);
//					}

					vecId++;

				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		svio.writeToNodesFile(false, nodes);
		svio.closeSvOutput();
	}

	/**
	 * Create map that maps simple methods names to its FQN signature.
	 * FQNs are read from .csv file which must not contain duplicates.
	 *
	 * @param signaturesFilesInpuAbsPath
	 * @return
	 * @throws IOException
	 */
	private Map<String, String> createUniqueSignatureSet(String signaturesFilesInpuAbsPath) throws IOException {
		if (signaturesFilesInpuAbsPath == null)
			throw new IllegalArgumentException();

		HashMap<String, String> uniqueSignatures = new HashMap<String, String>();

		File csvFile = new File(signaturesFilesInpuAbsPath);
		FileReader fileReader = new FileReader(csvFile);
		CSVParser csvParser = CSVParser.parse(fileReader, CSVFormat.DEFAULT);
		for (CSVRecord csvRecord : csvParser) {
			String signature = csvRecord.get("methods");
			String simpleName = org.eclipse.jdt.core.Signature.getSimpleName(signature);
			uniqueSignatures.put(simpleName, signature);
		}

		return uniqueSignatures;
	}

	private Set<String> createUniqueConstantsSet(String constantsFilesInpuAbsPath) throws IOException {
		if (constantsFilesInpuAbsPath == null)
			throw new IllegalArgumentException();

		HashSet<String> uniqueConstants = new HashSet<String>();

		File csvFile = new File(constantsFilesInpuAbsPath);
		FileReader fileReader = new FileReader(csvFile);
		CSVParser csvParser = CSVParser.parse(fileReader, CSVFormat.DEFAULT);
		for (CSVRecord csvRecord : csvParser) {
			String constant = csvRecord.get("constants");
			uniqueConstants.add(constant);
		}

		return uniqueConstants;
	}

	/**
	 *
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws CancelException
	 */
	private void createAPDGFromSnippet() throws IOException, IllegalArgumentException, CancelException {
		JDTJavaSourceAnalysisEngine engine =
			this.getJDTJavaSourceAnalysisEngine(projectName, new String[] { "LFoo" }, walaExclusionFileAbsPath);
		engine.setEntrypointBuilder(this.getEntrypointBuilderForSnippet(SOURCE));

		CallGraph cg = engine.buildDefaultCallGraph();
		List<CGNode> cgNodes = new LinkedList<CGNode>();
		for (CGNode n : cg.getEntrypointNodes()) {
			cgNodes.add(n);
		}
		PointerAnalysis pa = engine.getPointerAnalysis();
//		SDG sdg = new SDG(cg, pa, new AstJavaModRef(), DataDependenceOptions.NO_HEAP, ControlDependenceOptions.NONE);
		SDG sdg = new SDG(cg, pa, new AstJavaModRef(), DataDependenceOptions.NO_BASE_NO_HEAP, ControlDependenceOptions.NONE);

		if (cgNodes.size() > 0) {
			for (CGNode n : cgNodes) {
				APDG apdg = new APDG(sdg, n, sdg.getPDG(n), null, null, null);
				System.out.println(apdg);
			}
		}
	}

	/**
	 *
	 * @param engine
	 * @param apdgIo
	 * @param projectName
	 * @param signatureMap
	 * @param constantMap
	 * @param signatureFileAbsPath
	 * @return
	 * @throws Exception
	 */
	private Collection<Entrypoint> createAPDGFromSnippet(final JDTJavaSourceAnalysisEngine engine, APDGIO apdgIo, SliceIO sliceIo,
			String projectName, Map<String, RealVector> constantMap, Map<String, RealVector> signatureMap, String signatureFileAbsPath) throws Exception {
		CallGraph cg;
		// PPA throws assertions, urgh!
		try {
//			CallGraphBuilder cgb = engine.defaultCallGraphBuilder();
//			AnalysisOptions ao = new AnalysisOptions();
//			ao.setTraceStringConstants(true);
//			CallGraph cgTmp = cgb.makeCallGraph(ao, null);
			cg = engine.buildDefaultCallGraph();
		} catch (AssertionError e) {
			e.printStackTrace();
			throw new AssertionException("Assertion failed in third party lib");
		}

		Collection<CGNode> cgNodes = new LinkedList<CGNode>();
		for (CGNode n : cg.getEntrypointNodes()) {
			cgNodes.add(n);
		}

		PointerAnalysis pa = engine.getPointerAnalysis();

		SDG sdg = new SDG(cg, pa, new AstJavaModRef(), DataDependenceOptions.FULL  , ControlDependenceOptions.NONE);

		Collection<Entrypoint> entrypoints = new HashSet<Entrypoint>();
		if (cgNodes.size() > 0) {
//			this.cgNodeCounter += cgNodes.size();
			for (CGNode n : cgNodes) {
				APDG apdg = new APDG(sdg, n, sdg.getPDG(n), constantMap, signatureMap, signatureFileAbsPath);
				if (apdg.getNodeCount() == 0) {
					continue;
				} else {
					this.cgNodeCounter++;
				}
//				apdg.setUniqueSignatures(signatures);
				apdg.setRepresentation("source");
				apdg.setProject(projectName);
				apdgIo.write(apdg.toString());
				sliceIo.write(apdg.getSlices());
				entrypoints.add(new DefaultEntrypoint(n.getMethod(), engine.getClassHierarchy()));
			}
		}

		return entrypoints;
	}

	/**
	 * Inputs a directory containing arbitrary Java source code projects.
	 * First, it creates an Eclipse for each project. Second, it extracts all
	 * Java files from the input projects and copies them into the Eclipse projects
	 * accordingly. From each Eclipse project a @JDTJavaSourceAnalysisEngine and a
	 * @EntrypointBuilder is created. Finally, the APDG is build and stored, and the
	 * list of @Entrypoint is returned.
	 *
	 * @param projectsDirectoryAbsPath
	 * @return
	 * @throws Exception
	 */
	private Map<String, Collection<Entrypoint>> createAPDGFromSourceCodeProjects(String projectsDirectoryAbsPath) throws Exception {
		if (projectsDirectoryAbsPath == null)
			throw new IllegalArgumentException();

		File project = new File(projectsDirectoryAbsPath);
		if (!project.isDirectory() || project.isHidden()) {
			throw new IllegalArgumentException();
		}
		IProject iProject = Application.copySourceFilesToWorkspace(project.getAbsolutePath());

		Map<String, RealVector> signatureMap = null;
		Map<String, RealVector> constantMap = null;
		if (this.signaturesFileAbsPath != null) {
			FeatureReader fr = new FeatureReader(this.signaturesFileAbsPath, 64);
			signatureMap = fr.getFeatureMap();
//			System.out.println(signatureMap.keySet().size() + " signatures");
		}
		if (this.constantsFileAbsPath != null) {
			FeatureReader fr = new FeatureReader(this.constantsFileAbsPath, 2);
			constantMap = fr.getFeatureMap();
//			System.out.println(constantMap.keySet().size() + " constant");
		}

		this.cgNodeCounter = 0;
		Map<String, Collection<Entrypoint>> projectToEntrypoints = new HashMap<String, Collection<Entrypoint>>();
		Logger.log(iProject);
		String outputApdgAbsPath = projectsDirectoryAbsPath + File.separator + iProject.getName().replace("-", "_") + ".txt";
		String outputSliceAbsPath = projectsDirectoryAbsPath + File.separator + iProject.getName().replace("-", "_") + "_slices.txt";

		IFolder src = iProject.getFolder(srcPath);
		Collection<Entrypoint> entrypoints = new HashSet<Entrypoint>();
		if (src.exists()) {
			IResource[] files = src.members();
			Logger.log("Project " + iProject.getName() + " has " + files.length + " files");
			APDGIO apdgIo = new APDGIO(outputApdgAbsPath);
			SliceIO sliceIo = new SliceIO(outputSliceAbsPath);
			for (IResource file : files) {
				String signatureFileAbsPath = projectsDirectoryAbsPath + File.separator + file.getName().replace(".java", ".json");
				try {
					JDTJavaSourceAnalysisEngine engine = this.getJDTJavaSourceAnalysisEngine(iProject.getName(),
							singleSrc(file.getProjectRelativePath().toString()), walaExclusionFileAbsPath);
					engine.setEntrypointBuilder(this.getEntrypointBuilderForSnippet(SOURCE));
					Collection<Entrypoint> returnedEntrypoints = this.createAPDGFromSnippet(engine, apdgIo, sliceIo, file.getName().replace(".java", "")/*iProject.getName()*/, constantMap, signatureMap, signatureFileAbsPath);
					entrypoints.addAll(returnedEntrypoints);
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
			}
			apdgIo.setCgNodeCount(this.cgNodeCounter);
			sliceIo.setCgNodeCount(this.cgNodeCounter);
			apdgIo.flush();
			sliceIo.flush();
			this.cgNodeCounter = 0;
			projectToEntrypoints.put(iProject.getName(), entrypoints);
		}

		return projectToEntrypoints;
	}

	private Map<String, Collection<Entrypoint>> createAPDGFromApp(Collection<Entrypoint> entryPoints) throws Exception {
		Map<String, RealVector> signatureMap = null;
		Map<String, RealVector> constantMap = null;
		if (this.signaturesFileAbsPath != null) {
			FeatureReader fr = new FeatureReader(this.signaturesFileAbsPath, 64);
			signatureMap = fr.getFeatureMap();
		}
		if (this.constantsFileAbsPath != null) {
			FeatureReader fr = new FeatureReader(this.constantsFileAbsPath, 2);
			constantMap = fr.getFeatureMap();
		}

		AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
		scope.addToScope(scope.getPrimordialLoader(), new JarFile(new File(androidJarAbsPath)));
		scope.addToScope(scope.getApplicationLoader(),new JarFile(appJarAbsPath));

		IClassHierarchy cha = ClassHierarchy.make(scope);

		boolean useInputEntrypoints = false;
		Collection<Entrypoint> appEntrypoints = new HashSet<Entrypoint>();
		if (entryPoints == null) {
			appEntrypoints = this.getEntrypointsForAndroidApp(scope, cha);
			Logger.log("Entrypoints is null");
		} else {
			appEntrypoints = this.getEntrypointsForAndroidApp(entryPoints, scope, cha);
			useInputEntrypoints = true;
		}

		AnalysisOptions options = new AnalysisOptions(scope, entryPoints);
		options.setReflectionOptions(ReflectionOptions.NO_METHOD_INVOKE);

		AnalysisCache cache = new AnalysisCache();

		CallGraphBuilder builder = null;
		CallGraph cg = null;
		try {
			builder = com.ibm.wala.ipa.callgraph.impl.Util
					.makeZeroOneContainerCFABuilder(options, cache, cha, scope);

			cg = builder.makeCallGraph(options, null);
		} catch (Exception e) {
			builder = com.ibm.wala.ipa.callgraph.impl.Util
					.makeRTABuilder(options, cache, cha, scope);

			cg = builder.makeCallGraph(options, null);
		}

		PointerAnalysis pa = builder.getPointerAnalysis();

//		SDG sdg = new SDG(cg, pa, DataDependenceOptions.NO_HEAP, ControlDependenceOptions.NONE);
		SDG sdg = new SDG(cg, pa, DataDependenceOptions.NO_BASE_PTRS, ControlDependenceOptions.NONE);

		Path path = new Path(appJarAbsPath);
		path = (Path) path.removeFileExtension();
		String projectName = path.lastSegment();
		APDGIO apdgIo = new APDGIO(path.removeLastSegments(1) + File.separator + projectName.replace("-", "_") + ".txt");
		if (useInputEntrypoints) {
			int nodeCount = 0;
			for (Entrypoint e : appEntrypoints) {
				for (Iterator<CGNode> cgnIt = cg.iterator(); cgnIt.hasNext();) {
					CGNode n = cgnIt.next();
					if (e.getMethod().getSignature().equals(n.getMethod().getSignature())) {
						APDG apdg = new  APDG(sdg, n, sdg.getPDG(n), constantMap, signatureMap, null);
						if (apdg.getNodeCount() == 0)
							continue;
						else
							nodeCount++;
						apdg.setRepresentation("application");
						apdg.setProject(projectName);
						apdgIo.write(apdg.toString());
//						nodeCount++;
						break;
					}
				}
			}
			apdgIo.setCgNodeCount(nodeCount);
		} else {
//			apdgIo.setCgNodeCount(cg.getNumberOfNodes());
			int nodeCount = 0;
			for (Iterator<CGNode> cgnIt = cg.iterator(); cgnIt.hasNext();) {
				CGNode n = cgnIt.next();
				APDG apdg = new APDG(sdg, n, sdg.getPDG(n), constantMap, signatureMap, null);
				if (apdg.getNodeCount() == 0)
					continue;
				else
					nodeCount++;
				apdg.setRepresentation("application");
				apdg.setProject(projectName);
				apdgIo.write(apdg.toString());
			}
			apdgIo.setCgNodeCount(nodeCount);
		}
		apdgIo.flush();

		Map<String, Collection<Entrypoint>> projectToEntrypoints = new HashMap<String, Collection<Entrypoint>>();
		projectToEntrypoints.put(projectName, appEntrypoints);

		return projectToEntrypoints;
	}

	private static void checkEntrypoints(Collection<Entrypoint> project1, Collection<Entrypoint> project2) throws Exception {
		Set<String> entryPoints1 = new HashSet<String>();
		Set<String> entryPoints2 = new HashSet<String>();
		for (Entrypoint e : project1)
			entryPoints1.add(e.getMethod().getSignature());
		for (Entrypoint e : project2)
			entryPoints2.add(e.getMethod().getSignature());

		int set1Size = entryPoints1.size();
		Logger.log("Set 1 size: " + set1Size);
		Logger.log("Set 2 size: " + entryPoints2.size());
		entryPoints1.retainAll(entryPoints2);
		Logger.log("Intersection size: " + entryPoints1.size());
		assert (entryPoints1.size() <= set1Size);
		if (!(entryPoints1.size() <= set1Size))
			throw new EntrypointException();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.IApplicationContext)
	 */
	@Override
	public Object start(IApplicationContext context) throws Exception {
		System.out.println("start analysis");
		long startApp = System.nanoTime();

		final Map<?, ?> args = context.getArguments();
		final String[] appArgs = (String[]) args.get("application.args");
		this.initArgsKeySet();
		HashMap<String, String> argsMap = this.getArgsMap(appArgs);
		String tmpSnippetDirectoryAbsPath = argsMap.get("-snippets");
		String tmpProjectDirectoryAbsPath = argsMap.get("-projects");
		String tmpAppDirectoryAbsPath = argsMap.get("-apps");
		String tmpJavaFileOutputDirectoryAbsPath = argsMap.get("-tmp");

		this.outputDirectorySnippetsAbsPath = argsMap.get("-snippetresults");
		this.outputDirectoryAppsAbsPath = argsMap.get("-appresults");
		this.projectName = argsMap.get("-project");
		this.javaHomeAbsPath = argsMap.get("-javahome");
		this.androidJarAbsPath = argsMap.get("-android");
		this.walaExclusionFileAbsPath = argsMap.get("-exclusions");
		this.command = argsMap.get("-command");
		this.verbosity  = argsMap.get("-verbosity");
		this.signaturesFileAbsPath = argsMap.get("-signatures");
		this.constantsFileAbsPath = argsMap.get("-constants");
		this.label = argsMap.get("-label");

		if (this.verbosity == null)
			Logger.setVerbosity(Logger.NON_VERBOSE);
		else
			Logger.setVerbosity(Logger.VERBOSE);
		
		if (this.label != null) {
			APDG.label = Type.valueOf(this.label);
			Logger.log(APDG.label);
		}

		if (tmpSnippetDirectoryAbsPath != null) {
			if (this.command.equals("sv")) {
				if (this.outputDirectorySnippetsAbsPath == null
						|| this.outputDirectorySnippetsAbsPath.length() == 0) {
					throw new IllegalArgumentException("-snippetresults");
				}
			}
			if (tmpJavaFileOutputDirectoryAbsPath == null
					|| tmpJavaFileOutputDirectoryAbsPath.length() == 0) {
				throw new IllegalArgumentException("-tmp");
			}
			if (this.projectName == null || this.projectName.length() == 0) {
				throw new IllegalArgumentException("-project");
			}
			if (this.javaHomeAbsPath == null
					|| this.javaHomeAbsPath.length() == 0)
				throw new IllegalArgumentException("-javahome");
		}
		if (tmpProjectDirectoryAbsPath != null) {
			if (this.javaHomeAbsPath == null
					|| this.javaHomeAbsPath.length() == 0)
				throw new IllegalArgumentException("-javahome");
		}
		if (tmpAppDirectoryAbsPath != null) {
			if (this.command.equals("sv")) {
				if (this.outputDirectoryAppsAbsPath == null
						|| this.outputDirectoryAppsAbsPath.length() == 0) {
					throw new IllegalArgumentException("-appresults");
				}
			}
			if (this.androidJarAbsPath == null
					|| this.androidJarAbsPath.length() == 0) {
				throw new IllegalArgumentException("-android");
			}
		}

		String minSBSizeString = argsMap.get("-minsbsize");
		Integer minSBSize = new Integer(minSBSizeString);

		this.initJavaHome();

		File[] snippetDirectory = null;
		File[] appDirectory = null;

		// first create the project for snippet access through WALAs JDT frontend
		// TODO catch exception and goto app analysis
		if (tmpSnippetDirectoryAbsPath != null) {
			snippetDirectory = new File(tmpSnippetDirectoryAbsPath).listFiles();
			if (snippetDirectory.length != 0) {
				try {
					Application.createProject(this.projectName);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}

		// Init the snippets to be processed by WALAs JDT frontend
		// and create semantic vectors
		if (snippetDirectory != null) {
			int errors = 0;
			List<String> errorSnippets = new ArrayList<String>();
			for (File snippetFile : snippetDirectory) {
				// directories will be ignored
				String snippetFileName = snippetFile.getName();
				int suffixIndex = snippetFileName.lastIndexOf(".");
				if (snippetFile.isDirectory()
						|| snippetFile.isHidden()
						|| !snippetFileName.substring(suffixIndex).equals(
								".java"))
					continue;

				String outputFileName = snippetFileName.substring(0, suffixIndex);

				try {
					// first create a well formed java file from the given code
					// snippet
					// @link{snippetFile} and store it at
					// @link{tmpJavaFileOutputDirectoryAbsPath}
					// with the given name
					this.snippetFileName = snippetFileName;
					SnippetToJavaFileCreator jfc = new SnippetToJavaFileCreator(
							snippetFile.getAbsolutePath(),
							tmpJavaFileOutputDirectoryAbsPath,
							this.snippetFileName, null);
					jfc.createJavaFile();

					// second copy the source file to workspace in order to use
					// it
					// with the WALA JDT frontend which accesses file through
					// the projects workspace.
					// remember that this plugin creates a new accessible
					// workspace
					this.copySourceFileToWorspace(
							tmpJavaFileOutputDirectoryAbsPath + File.separator
									+ this.snippetFileName, null);
				} catch (Exception e) {
					// could not create a well formed java file from
					// the given code snippet, continue with next snippet
					System.err.println("exception in creation java file");
					e.printStackTrace();
					errors++;
					continue;
				}

				// create semantic vectors from snippet
				if (minSBSize >= 0) {
					try {
						// we create the semantic vectors by accessing the
						// snippet
						// from
						// which is now stored in the created workspace
						long start = System.nanoTime();

//						this.createSemanticVectorFromCodeSnippet(
//								null, outputFileName, minSBSize);
						this.createAPDGFromSnippet();

						long stop = System.nanoTime();
						System.out.println("sv creation time: "
								+ TimeUnit.NANOSECONDS.toMillis(stop - start));
					} catch (Exception e) {
						// could not create semantic vector from snippet
						// continue to next snippet
						System.err.println("exception in creation sv");
						e.printStackTrace();
						errors++;
						errorSnippets.add(this.snippetFileName);
//						continue;
					}
				}
			}
			System.out.println("finished snippets with " + errors + " errors");
			System.out.println(errorSnippets);
		}

		Map<String, Collection<Entrypoint>> projectToEntrypoint = null;
		if (this.command.equals("apdg")) {
			if (tmpProjectDirectoryAbsPath != null) {
				projectToEntrypoint = createAPDGFromSourceCodeProjects(tmpProjectDirectoryAbsPath);
			}
		}

		// create semantic vectors from apps
		if (tmpAppDirectoryAbsPath != null)
			appDirectory = new File(tmpAppDirectoryAbsPath).listFiles();

		if (minSBSize >= 0 && appDirectory != null) {
			int errors = 0;

			long astart = System.nanoTime();
			for (File appFile : appDirectory) {
				String appFileName = appFile.getName();
				int appSuffixIndex = appFileName.lastIndexOf(".");
				if (appFile.isDirectory() || appFile.isHidden()
						|| !appFileName.substring(appSuffixIndex).equals(
						".jar"))
					continue;

				String outputFileName = appFileName.substring(0, appSuffixIndex);
				this.appJarAbsPath = appFile.getAbsolutePath();

				try {
					long start = System.nanoTime();

					if (this.command.equals("apdg")) {
						if (projectToEntrypoint != null) {
							if (!projectToEntrypoint.containsKey(outputFileName)) {
//								System.err.println(appFileName + " has no given entrypoints");
								continue;
							}

							IPath path = new Path(this.appJarAbsPath);
							String projectName = path.removeFileExtension().lastSegment();
							Collection<Entrypoint> entrypoints = projectToEntrypoint.get(projectName);
							if (entrypoints == null) {
								Logger.log("Entrypoints emtpy for " + projectName);
//								this.createAPDGFromApp(entrypoints);
								continue;
							} else {
								checkEntrypoints(this.createAPDGFromApp(entrypoints).get(projectName), entrypoints);
							}
						} /*else {
							this.createAPDGFromApp(null);
						}*/
					} else if (this.command.equals("sv")) {
						this.createSemanticVectorFromApp(outputFileName /* + ".svs" */, minSBSize);
					}

					long stop = System.nanoTime();
				} catch (Exception e) {
					e.printStackTrace();
					errors++;
					continue;
				}
			}
			long astop = System.nanoTime();

			System.out.println("finished with " + errors + " errors");
			System.out.println("app analysis time " + TimeUnit.NANOSECONDS.toMillis(astop - astart));
		}
		long stopApp = System.nanoTime();

		System.out.println("runtime " + TimeUnit.NANOSECONDS.toMillis(stopApp - startApp));

	    return IApplication.EXIT_OK;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	@Override
	public void stop() {
		// nothing to do
	}
}
