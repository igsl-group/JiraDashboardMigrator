package com.igsl.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Same as PatternLayout, except stripping out ANSI escape codes so they don't
 * get logged into file
 */
public class NoAnsiPatternLayout extends PatternLayout {

	private static final String ANSI_ESCAPE_CODE = "\033\\[[0-9;]+m";
	private static final Pattern PATTERN = Pattern.compile(ANSI_ESCAPE_CODE);

	@Override
	public String format(LoggingEvent event) {
		String s = super.format(event);
		Matcher m = PATTERN.matcher(s);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(sb, "");
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
