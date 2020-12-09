package com.aisec.sa.io;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class aggregates all Java files by walking a
 * a file tree
 * @author fischer
 *
 */
public class JavaFileAggregator {

	/** Returns a @Collection of .java files recursively
	 * found in @directoryAbsPathc
	 *
	 * @param directoryAbsPath
	 * @return
	 */
	public static Collection<File> getFiles(String directoryAbsPath) {
		if (directoryAbsPath == null)
			throw new IllegalArgumentException();

//		Logger.log(directoryAbsPath);

		File directory = new File(directoryAbsPath);
		List<File> javafiles = new ArrayList<File>();
		File [] files = directory.listFiles();
		for (File file : files) {
			if (file.isFile() && file.getName().endsWith(".java")) {
				javafiles.add(file);
			} else if (file.isDirectory()) {
				javafiles.addAll(getFiles(file.getAbsolutePath()));
			}
		}

		return javafiles;
	}
}
