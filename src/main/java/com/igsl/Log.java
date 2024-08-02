package com.igsl;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;

/**
 * VeraCode wants log data to be sanitized to prevent user data from masquerading as log data.
 * This is done by removing newline characters.
 */
public class Log {
	
	private static List<Object> processArguments(Object... args) {
		List<Object> newArgs = new ArrayList<>();
		for (Object o : args) {
			if (o != null && o instanceof String) {
				String s = (String) o;
				newArgs.add(s.replaceAll("[\\r\\n]", ""));
			} else {
				newArgs.add(o);
			}
		}
		return newArgs;
	}
	
	public static void printCount(Logger logger, String title, int count, int total) {
		Ansi bar = null;
		String barString = "\t";
		if (count == total) {
			bar = Ansi.ansi().bg(Color.GREEN).a(barString).reset();
		} else {
			bar = Ansi.ansi().bg(Color.YELLOW).a(barString).reset();
		}
		logger.info(bar + " " + title + count + "/" + total + " " + bar);
	}
	
	public static void error(Logger logger, String format, Object... args) {
		logger.error(format, processArguments(args));
	}

	public static void error(Logger logger, String format, Throwable ex) {
		logger.error(format, ex);
	}
	
	public static void debug(Logger logger, String format, Object... args) {
		logger.debug(format, processArguments(args));
	}

	public static void info(Logger logger, String format, Object... args) {
		logger.info(format, processArguments(args));
	}
	
	public static void warn(Logger logger, String format, Object... args) {
		logger.warn(format, processArguments(args));
	}
	
	public static void trace(Logger logger, String format, Object... args) {
		logger.trace(format, processArguments(args));
	}

	public static void fatal(Logger logger, String format, Object... args) {
		logger.fatal(format, processArguments(args));
	}
}
