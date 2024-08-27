package com.igsl.model.mapping;

import java.util.Comparator;

/**
 * Nested member in CustomField
 */
public class Schema implements Comparable<Schema> {
	private static Comparator<String> nullableStringComparator = Comparator.nullsFirst(String::compareTo);
	private String type;
	private String items;
	private String custom;

	@Override
	public int compareTo(Schema o) {
		return nullableStringComparator.compare(this.type, o.type)
				| nullableStringComparator.compare(this.items, o.items)
				| nullableStringComparator.compare(this.custom, o.custom);
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
}
