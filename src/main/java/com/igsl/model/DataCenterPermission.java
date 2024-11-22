package com.igsl.model;

public class DataCenterPermission {
	private String type;
	private PermissionTarget project;
	private PermissionTarget group;
	private PermissionTarget role;
	private PermissionTarget user;
	private boolean view;	// Used by Server only
	private boolean edit;	// Used by Server only
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		switch (PermissionType.parse(type)) {
		case GLOBAL:
			sb.append("Public");
			break;
		case GROUP:
			sb.append("Group [").append(group.getName()).append("]");
			break;
		case LOGGED_IN:
			sb.append("Authenticated users");
			break;
		case PROJECT:	// Fall-through
		case PROJECT_ROLE:
			sb	.append("Project (")
				.append(project.getId())
				.append(")");
			if (role != null) {
				sb	.append(" Role (")
					.append(role.getId())
					.append(")");
			}
			break;
		case PROJECT_UNKNOWN:
			sb.append("Unknown project");
			break;
		case USER:
			sb	.append("User (")
				.append(user.getKey())
				.append(")");
			break;
		case USER_UNKNOWN:
			sb.append("Unknown user");
			break;
		}
		sb.append(" ");
		if (view) {
			sb.append("[View]");
		}
		if (edit) {
			sb.append("[Edit]");
		}
		return sb.toString();
	}
	
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
