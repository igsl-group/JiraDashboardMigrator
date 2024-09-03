package com.igsl;

public class FilterNotMappedException extends Exception {
	private static final long serialVersionUID = 1L;
	private String referencedFilter;
	public FilterNotMappedException(String referencedFilter) {
		super();
		this.referencedFilter = referencedFilter;
	}
	public String getReferencedFilter() {
		return referencedFilter;
	}
}
