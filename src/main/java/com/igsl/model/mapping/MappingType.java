package com.igsl.model.mapping;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum MappingType {
	SERVICE_DESK("ServiceDesk", ServiceDesk.class, true, true, null),
	REQUEST_TYPE("RequestType", RequestType.class, true, true, 
			Arrays.asList("request type"), 
			SERVICE_DESK),
	PRIORITY("Priority", Priority.class, true, true, 
			Arrays.asList("priority")),
	STATUS("Status", Status.class, true, true, 
			Arrays.asList("status")), 
	PROJECT("Project", Project.class, true, true, 
			Arrays.asList("project")), 
	PROJECT_CATEGORY("ProjectCategory", ProjectCategory.class, true, true, 
			Arrays.asList("category")),
	PROJECT_COMPONENT("ProjectComponent", ProjectComponent.class, true, true, 
			Arrays.asList("component"), 
			PROJECT),
	PROJECT_VERSION("ProjectVersion", ProjectVersion.class, true, true, 
			Arrays.asList("version"), 
			PROJECT),
	ROLE("Role", Role.class, true, true, null), 
	USER("User", User.class, true, true, 
			Arrays.asList("assignee", "reporter")), 	
	GROUP("Group", Group.class, true, true, null), 
	CUSTOM_FIELD("CustomField", CustomField.class, true, true, null), 
	CUSTOM_FIELD_CONTEXT("CustomFieldContext", CustomFieldContext.class, false, true, null, CUSTOM_FIELD),
	CUSTOM_FIELD_OPTION("CustomFieldOption", CustomFieldOption.class, true, true, null, 
			CUSTOM_FIELD, CUSTOM_FIELD_CONTEXT),
	AGILE_BOARD("AgileBoard", AgileBoard.class, true, true, null), 
	SPRINT("Sprint", Sprint.class, true, true, 
			Arrays.asList("sprint"), 
			AGILE_BOARD),
	ISSUE_TYPE("IssueType", IssueType.class, true, true, 
			Arrays.asList("issuetype", "type")),
	FILTER("Filter", Filter.class, true, true, 
			Arrays.asList("filter")), 
	DASHBOARD("Dashboard", Dashboard.class, true, false, null, FILTER);
	
	private static final String EXTENSION = ".json";
	private static final Pattern PATTERN_CUSTOMFIELD = Pattern.compile("^(?:customfield_([0-9]+)|cf\\[([0-9]+)\\])$");
	
	private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyyMMdd-HHmmss");
	
	private Class<?> dataClass;	// JiraObject subclass
	private boolean includeServer;	// This type is exported from Server
	private boolean includeCloud;	// This type is exported from Cloud 
	private List<String> namesInJQL;	// Left hand side in filter, null if not applicable.
	private String name;	// Display name for logging purpose
	private MappingType[] dependencies;
	
	private MappingType(
			String name, Class<?> dataClass, 
			boolean includeServer, boolean includeCloud, 
			List<String> nameInFilter, 
			MappingType... dependencies) {
		this.name = name;
		this.dataClass = dataClass;
		this.includeServer = includeServer;
		this.includeCloud = includeCloud;
		this.namesInJQL = new ArrayList<>();
		if (nameInFilter != null) {
			this.namesInJQL.addAll(nameInFilter);
		}
		this.dependencies = dependencies;
	}	
	
	public static String getMappingTypes(boolean cloud) {
		StringBuilder sb = new StringBuilder();
		for (MappingType type : MappingType.values()) {
			if ((cloud && type.isIncludeCloud()) || 
				(!cloud && type.isIncludeServer())) {
				sb.append(",").append(type.toString());
			}
		}
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
				List<String> jqlNames = type.getNamesInJQL();
				if (jqlNames != null) {
					for (String jqlName : jqlNames) {
						if (jqlName.toLowerCase().equals(name.toLowerCase())) {
							return type;
						}
					}
				}
			}
		}
		return null;
	}
	
	public String getCSV(Date date) {
		return name + "." + SDF.format(date) + ".csv";
	}
	public String getMapping() {
		return name + ".Mapping.csv";
	}
	public String getOwner() {
		return name + ".Owner" + EXTENSION;
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

	public List<String> getNamesInJQL() {
		return namesInJQL;
	}

	public MappingType[] getDependencies() {
		return dependencies;
	}
}
