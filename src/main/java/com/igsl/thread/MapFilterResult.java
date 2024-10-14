package com.igsl.thread;

import com.igsl.model.mapping.Filter;

public class MapFilterResult {
	private Filter original;
	private Filter target;
	private Exception exception;
	public Exception getException() {
		return exception;
	}
	public Filter getOriginal() {
		return original;
	}
	public Filter getTarget() {
		return target;
	}
	public void setOriginal(Filter original) {
		this.original = original;
	}
	public void setTarget(Filter target) {
		this.target = target;
	}
	public void setException(Exception exception) {
		this.exception = exception;
	}
}