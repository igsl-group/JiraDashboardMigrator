package com.igsl.model.mapping;

public enum MappingType {
	STATUS("Status", Status.class, true, true), 
	PROJECT("Project", Project.class, true, true), 
	PROJECT_CATEGORY("ProjectCategory", ProjectCategory.class, true, true),
	PROJECT_COMPONENT("ProjectComponent", ProjectComponent.class, true, true),
	ROLE("Role", Role.class, true, true), 
	USER("User", User.class, true, true), 
	GROUP("Group", Group.class, true, true), 
	CUSTOM_FIELD("CustomField", CustomField.class, true, true), 
	AGILE_BOARD("AgileBoard", AgileBoard.class, true, true), 
	SPRINT("Sprint", Sprint.class, true, true),
	ISSUE_TYPE("IssueType", IssueType.class, true, true),
	FILTER("Filter", Filter.class, false, false), 
	DASHBOARD("Dashboard", Dashboard.class, false, false);
	
	private static final String EXTENSION = ".json";

	private Class<?> dataClass;
	private boolean includeServer;
	private boolean includeCloud;
	private String name;
	
	private MappingType(
			String name, Class<?> dataClass, 
			boolean includeServer, boolean includeCloud) {
		this.name = name;
		this.dataClass = dataClass;
		this.includeServer = includeServer;
		this.includeCloud = includeCloud;
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

	public boolean isIncludeServer() {
		return includeServer;
	}

		public boolean isIncludeCloud() {
		return includeCloud;
	}
}
