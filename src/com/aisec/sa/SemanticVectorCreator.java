package com.aisec.sa;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import com.aisec.sa.io.SemanticVectorIO;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.HashSetFactory;

public class SemanticVectorCreator implements Runnable {
	private int workerId;
	private int minSbSize;
	
	private String androidJarAbsPath;
	private String outputDirectoryAppsAbsPath;
	
	private List<File> appFiles;
	
	public SemanticVectorCreator(int workerId, int minSbSize, String androidJarAbsPath, String outputDirectoryAppsAbsPath, List<File> appFiles) {
		if (androidJarAbsPath == null || outputDirectoryAppsAbsPath == null)
			throw new IllegalArgumentException();
		
		if (appFiles == null || appFiles.size() == 0)
			throw new IllegalArgumentException("app files empty");
		
		if (minSbSize <= 0)
			throw new IllegalArgumentException("minSbSize");
		
		this.workerId = workerId;
		this.minSbSize = minSbSize;
		
		this.androidJarAbsPath = androidJarAbsPath;
		this.outputDirectoryAppsAbsPath = outputDirectoryAppsAbsPath;
		
		this.appFiles = appFiles;
	}
	
	private Collection<Entrypoint> getEntrypointsForAndroidApp(AnalysisScope scope, IClassHierarchy cha) {
		if (scope == null || cha == null)
			throw new IllegalArgumentException();
		
		Iterator<IClass> classes = cha.iterator();

		Collection<Entrypoint> entryPoints = new LinkedList<Entrypoint>();

		while (classes.hasNext()) {
			IClass c = classes.next();
			// only application methods should be entrypoints
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
				 */
				if (((m.isPublic() || m.isProtected()) && m.getName().toString().startsWith("on"))
						// ... or this method was declared as a custom handler
						|| possibleOverrides.contains(m.getName().toString() + m.getDescriptor().toString())) {
					// or this method is an override of an interface method
					assert (c.getClassLoader().getReference().equals(ClassLoaderReference.Application));

					entryPoints.add(new DefaultEntrypoint(m, cha));
				}
			}
		}
		
		return entryPoints;
	}

	private void createSemanticVectorFromApp(String appJarAbsPath, String outputFileName, int minSize)
			throws Exception, ClassHierarchyException,
			IllegalArgumentException, CallGraphBuilderCancelException {
		 System.out.println("building scope");

		AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
		scope.addToScope(scope.getPrimordialLoader(), new JarFile(new File(
				this.androidJarAbsPath)));
		scope.addToScope(scope.getApplicationLoader(), new JarFile(
				appJarAbsPath));

		IClassHierarchy cha = ClassHierarchy.make(scope);
		Collection<Entrypoint> entryPoints = this.getEntrypointsForAndroidApp(
				scope, cha);

		AnalysisOptions options = new AnalysisOptions(scope, entryPoints);
		options.setReflectionOptions(ReflectionOptions.NO_METHOD_INVOKE);

		AnalysisCache cache = new AnalysisCache();
		CallGraphBuilder builder = com.ibm.wala.ipa.callgraph.impl.Util
				.makeRTABuilder(options, cache, cha, scope);

		System.out.println("worker " + this.workerId + " building call graph");
		CallGraph cg = builder.makeCallGraph(options, null);

		 System.out.println("worker " + this.workerId + " building pointer analysis");
		PointerAnalysis pa = builder.getPointerAnalysis();

		 System.out.println("worker " + this.workerId + " building sdg");
		SDG sdg = new SDG(cg, pa, DataDependenceOptions.NO_HEAP,
				ControlDependenceOptions.NONE);

		SemanticVectorIO svio = new SemanticVectorIO(
				this.outputDirectoryAppsAbsPath + File.separator
						+ outputFileName, null);
		
		System.out.println("worker " + this.workerId + " building semantic vectors");
		for (Iterator<CGNode> cgnIt = cg.iterator(); cgnIt.hasNext();) {
			CGNode n = cgnIt.next();
			PDG pdg = sdg.getPDG(n);
			
			SemanticBlock sb = new SemanticBlock(n, sdg, minSize);
			sb.buildSemanticBlocks();
			
			SemanticVector sv = new SemanticVector(n, pdg/*
														 * , this.methodNameSet,
														 * this.valueSet,
														 * this.methodNameToIndexMap
														 * ,
														 * this.valueToIndexMap
														 */);

			int[] svArray;
			try {
				svArray = sv.buildSemanticVector(sb.getSemanticBlocks(), true, null, false, n);
			} catch (Exception e) {
				 e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		int errors = 0;

		long astart = System.nanoTime();
		for (File appFile : this.appFiles) {
			if (appFile.isDirectory() || appFile.isHidden())
				continue;

			String appJarAbsPath = appFile.getAbsolutePath();

			try {
				System.out.println("worker " + this.workerId + " building sv from app " + appFile.getName());
				long start = System.nanoTime();

				this.createSemanticVectorFromApp(appJarAbsPath,
						appFile.getName() + ".svs", this.minSbSize);

				long stop = System.nanoTime();
				System.out.println("worker " + this.workerId + " sv creation time: "
						+ TimeUnit.NANOSECONDS.toMillis(stop - start));
			} catch (Exception e) {
				errors++;
				continue;
			}
		}
		long astop = System.nanoTime();

		System.out.println("worker " + this.workerId + " finished with " + errors + " errors");
		System.out.println("worker " + this.workerId + " app analysis time "
				+ TimeUnit.NANOSECONDS.toMillis(astop - astart));

	}
}
