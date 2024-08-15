package com.igsl.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.igsl.Log;
import com.igsl.model.mapping.MappingType;

public class GadgetConfigMapping {
	private static final Logger LOGGER = LogManager.getLogger();
	private String attributeNameRegex;
	private List<GadgetConfigCondition> conditions;
	private String pattern;
	private MappingType mappingType;
	private int targetGroup;
	private String replacement;
	private String prefix;
	private String suffix;
	private List<GadgetConfigAddition> additions;
	private Pattern attributeNamePattern;
	public GadgetConfigMapping() {}
	public void setAttributeNameRegex(String attributeNameRegex) {
		this.attributeNameRegex = attributeNameRegex;
		try {
			this.attributeNamePattern = Pattern.compile(attributeNameRegex);
		} catch (PatternSyntaxException psex) {
			Log.error(LOGGER, "Attribute name pattern " + attributeNameRegex + " is invalid", psex);
		}
	}
	// Generated
	public void setMappingType(MappingType mappingType) {
		this.mappingType = mappingType;
	}
	public void setTargetGroup(int targetGroup) {
		this.targetGroup = targetGroup;
	}
	public void setReplacement(String replacement) {
		this.replacement = replacement;
	}
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}
	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}
	public Pattern getAttributeNamePattern() {
		return attributeNamePattern;
	}
	public String getAttributeNameRegex() {
		return attributeNameRegex;
	}
	public String getPrefix() {
		return prefix;
	}
	public String getSuffix() {
		return suffix;
	}
	public String getPattern() {
		return pattern;
	}
	public MappingType getMappingType() {
		return mappingType;
	}
	public int getTargetGroup() {
		return targetGroup;
	}
	public String getReplacement() {
		return replacement;
	}
	public List<GadgetConfigCondition> getConditions() {
		return conditions;
	}
	public void setConditions(List<GadgetConfigCondition> conditions) {
		this.conditions = conditions;
	}
	public List<GadgetConfigAddition> getAdditions() {
		return additions;
	}
	public void setAdditions(List<GadgetConfigAddition> additions) {
		this.additions = additions;
	}
}