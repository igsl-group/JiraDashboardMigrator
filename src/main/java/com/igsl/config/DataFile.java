package com.igsl.config;

public enum DataFile {
	USER_MAP("User", "Map"), 
	USER_CLOUD("User", "Cloud"), 
	USER_DATACENTER("User", "DataCenter"),
	
	STATUS_MAP("Status", "Map"),
	STATUS_CLOUD("Status", "Cloud"),
	STATUS_DATACENTER("Status", "DataCenter"),
	
	FIELD_MAP("Field", "Map"), 
	FIELD_CLOUD("Field", "Cloud"), 
	FIELD_DATACENTER("Field", "DataCenter"),

	PROJECT_MAP("Project", "Map"), 
	PROJECT_CLOUD("Project", "Cloud"), 
	PROJECT_DATACENTER("Project", "DataCenter"),

	GROUP_MAP("Group", "Map"), 
	GROUP_CLOUD("Group", "Cloud"), 
	GROUP_DATACENTER("Group", "DataCenter"),

	ROLE_MAP("Role", "Map"), 
	ROLE_CLOUD("Role", "Cloud"), 
	ROLE_DATACENTER("Role", "DataCenter"),

	FILTER_DATA("Filter", "Data"),
	FILTER_REMAPPED("Filter", "Remapped"), 
	FILTER_DATACENTER("Filter", "DataCenter"),
	FILTER_MIGRATED("Filter", "Migrated"), 
	FILTER_LIST("Filter", "Cloud"),

	DASHBOARD_DATA("Dashboard", "Data"), 
	DASHBOARD_REMAPPED("Dashboard", "Remapped"),
	DASHBOARD_DATACENTER("Dashboard", "DataCenter"), 
	DASHBOARD_MIGRATED("Dashboard", "Migrated"),
	DASHBOARD_LIST("Dashboard", "Cloud");

	private static final String EXTENSION = ".json";
	private String value;

	private DataFile(String category, String type) {
		value = category + "." + type + EXTENSION;
	}

	private DataFile(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return value;
	}
}
