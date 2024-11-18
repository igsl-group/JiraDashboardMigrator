package com.igsl.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Maps Data Center color# to Cloud colors
public enum Color {
	BLUE("blue", "color1"), RED("red", "color2"), YELLOW("yellow", "color3"), GREEN("green", "color4"),
	CYAN("cyan", "color5"), PURPLE("purple", "color6"), GRAY("gray", "color7"), WHITE("white", "color8");

	private String cloudValue;
	private String dataCenterValue;

	private Color(String c, String d) {
		this.cloudValue = c;
		this.dataCenterValue = d;
	}

	public static Color parse(String s) {
		for (Color c : Color.values()) {
			if (c.dataCenterValue.equals(s)) {
				return c;
			}
		}
		return BLUE;
	}

	@JsonCreator
	public static Color forValue(String value) {
		for (Color c : Color.values()) {
			if (c.cloudValue.equals(value)) {
				return c;
			}
		}
		return BLUE;
	}

	@JsonValue
	@Override
	public String toString() {
		return this.cloudValue;
	}
}