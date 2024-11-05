package com.igsl.model.mapping;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Nested member in CustomField
 */
public class Schema implements Comparable<Schema> {
	public static final Comparator<Schema> COMPARATOR = Comparator.nullsFirst(Schema::compareTo);
	private static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(String::compareTo); 

	/**
	 * Value of custom that corresponds to the field having options
	 */
	private static final List<String> OPTION_TYPES = Arrays.asList(
			"com.atlassian.jira.plugin.system.customfieldtypes:select",
			"com.atlassian.jira.plugin.system.customfieldtypes:multiselect",
			"com.atlassian.jira.plugin.system.customfieldtypes:cascadingselect",
			"com.atlassian.jira.plugin.system.customfieldtypes:radiobuttons",
			"com.atlassian.jira.plugin.system.customfieldtypes:multicheckboxes"
			);
	
	private String type;
	private String customId;
	private String items;
	private String custom;
	
	@JsonIgnore
	public boolean isOptionField() {
		return (OPTION_TYPES.contains(custom));
	}
	
	@Override
	public int compareTo(Schema o) {
		// items can be different, so ignore it
		return STRING_COMPARATOR.compare(this.type, o.type)
				| STRING_COMPARATOR.compare(this.custom, o.custom);
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getItems() {
		return items;
	}

	public void setItems(String items) {
		this.items = items;
	}

	public String getCustom() {
		return custom;
	}

	public void setCustom(String custom) {
		this.custom = custom;
	}

	public String getCustomId() {
		return customId;
	}

	public void setCustomId(String customId) {
		this.customId = customId;
	}
}
