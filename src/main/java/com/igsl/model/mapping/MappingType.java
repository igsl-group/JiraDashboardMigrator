package com.igsl.model.mapping;

public enum MappingType {
	STATUS("Status", Status.class), 
	PROJECT("Project", Project.class), 
	PROJECT_CATEGORY("ProjectCategory", ProjectCategory.class),
	PROJECT_COMPONENT("ProjectComponent", ProjectComponent.class),
	ROLE("Role", Role.class), 
	USER("User", User.class), 
	GROUP("Group", Group.class), 
	CUSTOM_FIELD("CustomField", CustomField.class), 
	FILTER("Filter", Filter.class), 
	DASHBOARD("Dashboard", Dashboard.class), 
	AGILE_BOARD("AgileBoard", AgileBoard.class), 
	SPRINT("Sprint", Sprint.class);
	
	private static final String EXTENSION = ".json";

	private Class<?> dataClass;
	private String name;
	
	private MappingType(String name, Class<?> dataClass) {
		this.name = name;
		this.dataClass = dataClass;
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

	public Class<?> getDataClass() {
		return dataClass;
	}

	public String getName() {
		return name;
	}
}
