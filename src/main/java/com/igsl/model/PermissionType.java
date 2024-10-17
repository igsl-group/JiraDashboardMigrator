package com.igsl.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.igsl.Log;

public enum PermissionType {
	USER("user"), 
	GROUP("group"), 
	PROJECT("project"), 
	PROJECT_ROLE("projectRole"), // Used only to create/update filters. For read, roles get bundled under PROJECT.
	GLOBAL("global"), 
	LOGGED_IN("loggedin", "authenticated"), 
	PROJECT_UNKNOWN("project-unknown"), // This seems to be the result of sharing to a project you cannot access
	USER_UNKNOWN("user-unknown");
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	private String dataCenterType;
	private String cloudType;

	private PermissionType(String type) {
		this.dataCenterType = type;
		this.cloudType = type;
	}

	private PermissionType(String dataCenterType, String cloudType) {
		this.dataCenterType = dataCenterType;
		this.cloudType = cloudType;
	}

	public static PermissionType parse(String dataCenterType) {
		for (PermissionType t : PermissionType.values()) {
			if (t.dataCenterType.equals(dataCenterType) || t.cloudType.equals(dataCenterType)) {
				return t;
			}
		}
		Log.error(LOGGER, "Unrecognized permission type: [" + dataCenterType + "]");
		return null;
	}

	@Override
	public String toString() {
		return this.cloudType;
	}
}