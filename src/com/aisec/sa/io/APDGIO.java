package com.aisec.sa.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;

/**
 * Writes APDG to file
 * @author fischer
 *
 */
public class APDGIO {
	private String apdg;
	private int cgNodeCount;
	private final String outputFileAbsPath;

	/**
	 *
	 * @param outputFileAbsPath
	 */
	public APDGIO(String outputFileAbsPath) {
		if (outputFileAbsPath == null)
			throw new IllegalArgumentException();

		this.outputFileAbsPath = outputFileAbsPath;
		this.apdg = new String();
	}

	public void setCgNodeCount(int cgNodeCount) {
		this.cgNodeCount = cgNodeCount;
	}

	/**
	 * Appends APDG newline separated to the string that is
	 * written to @outputFileAbsPath when flushed.
	 * @param apdg
	 */
	public void write(String apdg) {
		if (apdg == null)
			return;

		this.apdg += apdg + "\n";
	}

	/**
	 * Appends APDG string to file in @outputFileAbsPath
	 */
	public void flush() {
		try {
			if (this.cgNodeCount > 0) {
				PrintWriter writer = new PrintWriter(new FileOutputStream(new File(this.outputFileAbsPath), true));
				writer.println(this.cgNodeCount);
				writer.println(this.apdg);
				writer.close();
			} else {
				System.out.println("cg node count is 0");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}



}
