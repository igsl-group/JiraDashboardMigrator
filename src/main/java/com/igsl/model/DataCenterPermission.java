package com.igsl.model;

public class DataCenterPermission {
	private String type;
	private PermissionTarget project;
	private PermissionTarget group;
	private PermissionTarget role;
	private PermissionTarget user;
	private boolean view;	// Used by Server only
	private boolean edit;	// Used by Server only
	
	public DataCenterPermission clone() {
		DataCenterPermission result = new DataCenterPermission();
		result.setType(this.getType());
		if (this.getProject() != null) {
			result.setProject(this.getProject().clone());
		}
		if (this.getGroup() != null) {
			result.setGroup(this.getGroup().clone());
		}
		if (this.getRole() != null) {
			result.setRole(this.getRole().clone());
		}
		if (this.getUser() != null) {
			result.setUser(this.getUser().clone());
		}
		result.setEdit(this.isEdit());
		result.setView(this.isView());
		return result;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public PermissionTarget getProject() {
		return project;
	}

	public void setProject(PermissionTarget project) {
		this.project = project;
	}

	public PermissionTarget getGroup() {
		return group;
	}

	public void setGroup(PermissionTarget group) {
		this.group = group;
	}

	public PermissionTarget getRole() {
		return role;
	}

	public void setRole(PermissionTarget role) {
		this.role = role;
	}

	public boolean isView() {
		return view;
	}

	public void setView(boolean view) {
		this.view = view;
	}

	public boolean isEdit() {
		return edit;
	}

	public void setEdit(boolean edit) {
		this.edit = edit;
	}

	public PermissionTarget getUser() {
		return user;
	}

	public void setUser(PermissionTarget user) {
		this.user = user;
	}
}
