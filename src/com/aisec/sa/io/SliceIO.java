package com.aisec.sa.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;

/**
 *
 */
public class SliceIO {
	private String slice;
	private int cgNodeCount;
	private final String outputFileAbsPath;

	/**
	 *
	 *
	 */
	public SliceIO(String outputFileAbsPath) {
		if (outputFileAbsPath == null)
			throw new IllegalArgumentException();

		this.outputFileAbsPath = outputFileAbsPath;
		this.slice = new String();
	}

	public void setCgNodeCount(int cgNodeCount) {
		this.cgNodeCount = cgNodeCount;
	}

	/**
	 *
	 */
	public void write(String slice) {
		if (slice == null)
			return;

		this.slice += slice + "\n"; // FXIME remove last \n
	}

	/**
	 *
	 */
	public void flush() {
		try {
			if (this.cgNodeCount > 0) {
				PrintWriter writer = new PrintWriter(new FileOutputStream(new File(this.outputFileAbsPath), true));
				writer.println(this.cgNodeCount);
				writer.println(this.slice);
				writer.close();
			} else {
				System.out.println("cg node count is 0");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}



}
