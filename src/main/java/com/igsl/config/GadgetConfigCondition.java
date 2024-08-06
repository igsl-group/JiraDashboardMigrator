package com.igsl.config;

import java.util.List;

public class GadgetConfigCondition {
	private String attributeName;
	private GadgetConfigConditionType condition;
	private List<String> attributeValue;
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(attributeName).append(" ").append(condition).append(" ");
		StringBuilder sbValue = new StringBuilder();
		for (String v : attributeValue) {
			sbValue.append(",").append(v);
		}
		if (sbValue.length() != 0) {
			sbValue.delete(0, 1);
		}
		sb.append(sbValue.toString());
		return sb.toString();
	}
	// Generated
	public String getAttributeName() {
		return attributeName;
	}
	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}
	public GadgetConfigConditionType getCondition() {
		return condition;
	}
	public void setCondition(GadgetConfigConditionType condition) {
		this.condition = condition;
	}
	public List<String> getAttributeValue() {
		return attributeValue;
	}
	public void setAttributeValue(List<String> attributeValue) {
		this.attributeValue = attributeValue;
	}
}
