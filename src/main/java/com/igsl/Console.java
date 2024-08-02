package com.igsl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * As Eclipse does not support java.io.Console
 * this class is used as a wrapper
 */
public class Console {
	
	public static void println(String format, Object... args) {
		System.out.println(String.format(format, args));
	}
	
	public static String readLine(String format, Object... args) throws IOException {
		if (System.console() != null) {
			return System.console().readLine(format, args);
		} else {
			System.out.print(String.format(format, args));
			// Do not close this stream, or System.in will be closed too
			@SuppressWarnings("resource")
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			return br.readLine();
		}
	}
	
	public static char[] readPassword(String format, Object... args) throws IOException {
		if (System.console() != null) {
			return System.console().readPassword(format, args);
		} else {
			// System.console() can be unavailable, e.g. inside Eclipse.
			// Cannot mask password
			String s = readLine(format, args);
			return s.toCharArray();
		}
	}
}
