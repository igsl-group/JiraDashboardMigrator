package com.igsl.config;

public enum GadgetConfigType {
	CONFIG("config"),	// All configuration placed under property key "config"
	SEPARATE(null); // Each configuration placed under its own property key
	private String propertyKey;
	GadgetConfigType(String propertyKey) {
		this.propertyKey = propertyKey;
	}
	public String getPropertyKey() {
		return this.propertyKey;
	}
}