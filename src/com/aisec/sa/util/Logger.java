package com.aisec.sa.util;

public class Logger {

	public static int VERBOSE = 0;
	public static int NON_VERBOSE = 1;
	private static int VERBOSITY = NON_VERBOSE;

	public static void setVerbosity(int v) {
		VERBOSITY = v;
	}

	public static void log(Object s) {
		if (VERBOSITY == VERBOSE)
			System.out.println(s);
	}

	public static void log(int i) {
		if (VERBOSITY == VERBOSE)
			System.out.println(i);
	}
}
