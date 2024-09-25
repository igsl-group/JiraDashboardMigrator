package com.igsl;

import java.util.List;

import com.atlassian.query.clause.Clause;

public class NotAllValuesMappedException extends Exception {
	private static final long serialVersionUID = 1L;
	
	private Clause clause;
	private List<String> unmappedValues;
	
	public NotAllValuesMappedException(Clause clause, List<String> unmappedValues) {
		super();
		this.clause = clause;
		this.unmappedValues = unmappedValues;
	}
	
	public Clause getClause() {
		return clause;
	}

	public List<String> getUnmappedValues() {
		return unmappedValues;
	}
	
	@Override
	public String getMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append("Unable to map the following values: ");
		for (String value : unmappedValues) {
			sb.append("[").append(value).append("] ");
		}
		return sb.toString();
	}
}
