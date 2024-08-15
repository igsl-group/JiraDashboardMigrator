package com.igsl.model.mapping;

public enum MappingType {
	STATUS("Status"), 
	PROJECT("Project"), 
	ROLE("Role"), 
	USER("User"), 
	GROUP("Group"), 
	CUSTOM_FIELD("CustomField"), 
	FILTER("Filter"), 
	DASHBOARD("Dashboard"), 
	AGILE_BOARD("AgileBoard"), 
	PROJECT_CATEGORY("ProjectCategory"),
	SPRINT("Sprint"),
	PROJECT_COMPONENT("ProjectComponent");
	
	private static final String EXTENSION = ".json";
	
	private String name;
	private MappingType(String name) {
		this.name = name;
	}
	
	public String getMap() {
		return name + ".Map" + EXTENSION;
	}
	public String getDC() {
		return name + ".DataCenter" + EXTENSION;
	}
	public String getCloud() {
		return name + ".Cloud" + EXTENSION;
	}
	public String getMigrated() {
		return name + ".Migrated" + EXTENSION;
	}
	public String getRemapped() {
		return name + ".Remapped" + EXTENSION;
	}
	public String getList() {
		return name + ".List" + EXTENSION;
	}
}
