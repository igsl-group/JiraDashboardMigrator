package com.igsl.config;

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
	private String newAttributeNameRegex;
	private String pattern;
	private MappingType mappingType;
	private int targetGroup;
	private String replacement;
	private String prefix;
	private String delimiter;
	private String suffix;
	private Pattern attributeNamePattern;
	public GadgetConfigMapping() {}
	public GadgetConfigMapping(String attributeNameRegex, 
			String newAttributeNameRegex,
			MappingType type, 
			String pattern, int targetGroup,
			String replacement,
			String prefix,
			String delimiter,
			String suffix) {
		this.attributeNameRegex = attributeNameRegex;
		this.newAttributeNameRegex = newAttributeNameRegex;
		this.mappingType = type;
		this.pattern = pattern;
		this.targetGroup = targetGroup;
		this.replacement = replacement;
		this.prefix = prefix;
		this.delimiter = delimiter;
		this.suffix = suffix;
		try {
			this.attributeNamePattern = Pattern.compile(attributeNameRegex);
		} catch (PatternSyntaxException psex) {
			Log.error(LOGGER, "Attribute name pattern " + attributeNameRegex + " is invalid", psex);
		}
	}
	public void setAttributeNameRegex(String attributeNameRegex) {
		this.attributeNameRegex = attributeNameRegex;
		try {
			this.attributeNamePattern = Pattern.compile(attributeNameRegex);
		} catch (PatternSyntaxException psex) {
			Log.error(LOGGER, "Attribute name pattern " + attributeNameRegex + " is invalid", psex);
		}
	}
	public String getNewAttributeName(String originalAttributeName) {
		if (this.getAttributeNamePattern() != null) {
			Matcher m = this.getAttributeNamePattern().matcher(originalAttributeName);
			if (m.matches()) {
				StringBuilder sb = new StringBuilder();
				m.appendReplacement(sb, this.getNewAttributeNameRegex());
				m.appendTail(sb);
				return sb.toString();
			}
		}
		return originalAttributeName;
	}
	// Generated
	public void setNewAttributeNameRegex(String newAttributeNameRegex) {
		this.newAttributeNameRegex = newAttributeNameRegex;
	}
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
	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
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
	public String getNewAttributeNameRegex() {
		return newAttributeNameRegex;
	}
	public String getPrefix() {
		return prefix;
	}
	public String getDelimiter() {
		return delimiter;
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
}