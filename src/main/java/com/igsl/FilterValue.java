package com.igsl;

import com.atlassian.query.operand.SingleValueOperand;
import com.igsl.model.mapping.Filter;

/**
 * A class to encapsulate JQL value which can be either Long or String
 */
public class FilterValue {
	private Long longValue;
	private String stringValue;
	
	public FilterValue(SingleValueOperand value) {
		this.longValue = value.getLongValue();
		this.stringValue = value.getStringValue();
	}
	
	public FilterValue(Long value) {
		this.longValue = value;
	}
	
	public FilterValue(String value) {
		this.stringValue = value;
	}
	
	public boolean equals(Filter filter) {
		if (filter != null) {
			if (longValue != null && 
				longValue.toString().equals(filter.getId())) {
				// Numeric ID
				return true;
			} else if (	stringValue != null && 
						stringValue.equalsIgnoreCase(filter.getName())) {
				// Filter name
				return true;
			} else if (	stringValue != null &&
						stringValue.equals(filter.getId())) {
				// ID specified as string
				return true;
			}
		}
		return false;
	}
	
	public Long getLongValue() {
		return longValue;
	}
	public String getStringValue() {
		return stringValue;
	}
	
	
}
