package com.igsl.model;

public enum PermissionType {
	USER("user"), 
	GROUP("group"), 
	PROJECT("project"), 
	PROJECT_ROLE("projectRole"), 
	GLOBAL("global"),
	LOGGED_IN("loggedin", "authenticated"), 
	UNKNOWN("project-unknown");

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
			if (t.dataCenterType.equals(dataCenterType)) {
				return t;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return this.cloudType;
	}
}