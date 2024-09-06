package com.igsl.model.mapping;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum MappingType {
	STATUS("Status", Status.class, true, true, "status"), 
	PROJECT("Project", Project.class, true, true, "project"), 
	PROJECT_CATEGORY("ProjectCategory", ProjectCategory.class, true, true, "category"),
	PROJECT_COMPONENT("ProjectComponent", ProjectComponent.class, true, true, "component"),
	ROLE("Role", Role.class, true, true, null), 
	USER("User", User.class, true, true, null), 
	GROUP("Group", Group.class, true, true, null), 
	CUSTOM_FIELD("CustomField", CustomField.class, true, true, null), 
	AGILE_BOARD("AgileBoard", AgileBoard.class, true, true, null), 
	SPRINT("Sprint", Sprint.class, true, true, "sprint"),
	ISSUE_TYPE("IssueType", IssueType.class, true, true, "issuetype"),
	FILTER("Filter", Filter.class, false, false, "filter"), 
	DASHBOARD("Dashboard", Dashboard.class, false, false, null);
	
	private static final String EXTENSION = ".json";
	private static final Pattern PATTERN_CUSTOMFIELD = Pattern.compile("^(?:customfield_([0-9]+)|cf\\[([0-9]+)\\])$");
	
	private Class<?> dataClass;	// JiraObject subclass
	private boolean includeServer;	// This type is exported from Server
	private boolean includeCloud;	// This type is exported from Cloud 
	private String nameInJQL;	// Left hand side in filter. Null if not applicable.
	private String name;	// Display name for logging purpose
	
	private MappingType(
			String name, Class<?> dataClass, 
			boolean includeServer, boolean includeCloud, 
			String nameInFilter) {
		this.name = name;
		this.dataClass = dataClass;
		this.includeServer = includeServer;
		this.includeCloud = includeCloud;
		this.nameInJQL = nameInFilter;
	}	
	
	public static String getMappingTypes(boolean cloud) {
		StringBuilder sb = new StringBuilder();
		for (MappingType type : MappingType.values()) {
			if ((cloud && type.isIncludeCloud()) || 
				(!cloud && type.isIncludeServer())) {
				sb.append(",").append(type.toString());
			}
		}
		sb.append("\nNote: " + PROJECT_COMPONENT + " requires " + PROJECT + " to be dumped together or previously");
		sb.append("\nNote: " + SPRINT + " requires " + AGILE_BOARD + " to be dumped together or previously");
		sb.append("\nNote: " + DASHBOARD + " requires " + FILTER + " to be dumped together or previously");
		return sb.toString().substring(1);
	}
	
	public static MappingType parse(String name) {
		if (name != null) {
			for (MappingType type : MappingType.values()) {
				if (type.toString().equals(name)) {
					return type;
				}
			}
		}
		return null;
	}
	
	public static String getFilterProertyCustomFieldId(String name) {
		if (name != null) {
			Matcher matcher = PATTERN_CUSTOMFIELD.matcher(name);
			if (matcher.matches()) {
				return matcher.group(1);
			}
		}
		return null;
	}
	
	public static MappingType parseFilterProperty(String name) {
		if (name != null) {
			for (MappingType type : MappingType.values()) {
				if (type.getNameInJQL() != null && 
					type.getNameInJQL().toLowerCase().equals(name.toLowerCase())) {
					return type;
				}
			}
		}
		return null;
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
	public String getFailed() {
		return name + ".Failed" + EXTENSION;
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

	public String getNameInJQL() {
		return nameInJQL;
	}
}
