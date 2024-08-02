package com.igsl.logging;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.apache.log4j.Logger;

public class Log4JHandler extends Handler {

	private Logger log4j;

	public Log4JHandler(Logger log4j) {
		this.log4j = log4j;
	}

	@Override
	public void publish(LogRecord record) {
		if (record != null) {
			log4j.debug(record.getMessage());
		}
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() throws SecurityException {
	}

}
