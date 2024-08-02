package com.igsl.logging;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;

/**
 * Same as PatternLayout but with color coding for log levels
 */
public class ColorPatternLayout extends PatternLayout {

	@Override
	public String format(LoggingEvent event) {
		String s = super.format(event);
		Ansi code = Ansi.ansi().fg(Color.DEFAULT).bg(Color.DEFAULT);
		switch (event.getLevel().toInt()) {
		case Priority.DEBUG_INT:
			code.fg(Color.MAGENTA);
			break;
		case Priority.ERROR_INT:
			code.fg(Color.RED).bold();
			break;
		case Priority.FATAL_INT:
			code.fg(Color.RED).bg(Color.YELLOW).bold();
			break;
		case Priority.INFO_INT:
			code.fg(Color.DEFAULT);
			break;
		case Priority.WARN_INT:
			code.fg(Color.YELLOW).bold();
			break;
		}
		return code + s + Ansi.ansi().reset();
	}
}
