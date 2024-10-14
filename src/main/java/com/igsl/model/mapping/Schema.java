package com.igsl.model.mapping;

import java.util.Comparator;

/**
 * Nested member in CustomField
 */
public class Schema implements Comparable<Schema> {
	public static final Comparator<Schema> COMPARATOR = Comparator.nullsFirst(Schema::compareTo);
	private static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(String::compareTo); 
	private String type;
	private String customId;
	private String items;
	private String custom;

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
