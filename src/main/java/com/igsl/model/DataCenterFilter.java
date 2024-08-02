package com.igsl.model;

import java.util.List;

public class DataCenterFilter {
	private String id;
	private String name;
	private String description;
	private String jql;
	private String originalJql;
	private PermissionTarget owner;
	private List<DataCenterPermission> sharePermissions;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getJql() {
		return jql;
	}

	public void setJql(String jql) {
		this.jql = jql;
	}

	public List<DataCenterPermission> getSharePermissions() {
		return sharePermissions;
	}

	public void setSharePermissions(List<DataCenterPermission> sharePermissions) {
		this.sharePermissions = sharePermissions;
	}

	public PermissionTarget getOwner() {
		return owner;
	}

	public void setOwner(PermissionTarget owner) {
		this.owner = owner;
	}

	public String getOriginalJql() {
		return originalJql;
	}

	public void setOriginalJql(String originalJql) {
		this.originalJql = originalJql;
	}
}
