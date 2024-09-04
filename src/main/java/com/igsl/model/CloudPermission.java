package com.igsl.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.igsl.Log;

@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class CloudPermission {
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	private String type;
	private PermissionTarget project;
	private PermissionTarget group;
	private PermissionTarget role;
	private PermissionTarget user;

	public static CloudPermission create(DataCenterPortalPermission permission) {
		CloudPermission result = null;
		if (permission != null) {
			result = new CloudPermission();
			PermissionType cpt = PermissionType.parse(permission.getShareType());
			result.setType(cpt.toString());
			switch (cpt) {
			case GLOBAL:
			case LOGGED_IN:
			case UNKNOWN:
				// No other fields required
				break;
			case GROUP:
				result.group = PermissionTarget.create(cpt, permission);
				break;
			case PROJECT_ROLE:
				result.project = PermissionTarget.create(PermissionType.PROJECT, permission);
				result.role = PermissionTarget.create(PermissionType.PROJECT_ROLE, permission);
				break;
			case PROJECT:
				result.project = PermissionTarget.create(cpt, permission);
				break;
			case USER:
				result.user = PermissionTarget.create(cpt, permission);
				break;
			}
		}
		return result;
	}

	@SuppressWarnings("incomplete-switch")
	public static CloudPermission create(DataCenterPermission permission) {
		CloudPermission result = null;
		if (permission != null) {
			result = new CloudPermission();
			PermissionType cpt = PermissionType.parse(permission.getType());
			if (cpt == null) {
				Log.error(LOGGER, "Unrecognized permission type: [" + permission.getType() + "]");
			} else {
				result.setType(cpt.toString());
				switch (cpt) {
				case GLOBAL:
				case LOGGED_IN:
				case UNKNOWN:
					// No other fields required
					break;
				case GROUP:
					result.group = PermissionTarget.create(cpt, permission);
					break;
				case PROJECT_ROLE: 
					result.project = PermissionTarget.create(PermissionType.PROJECT, permission);
					result.role = PermissionTarget.create(PermissionType.PROJECT_ROLE, permission);
					break;
				case PROJECT:
					result.project = PermissionTarget.create(cpt, permission);
					break;
				case USER:
					result.user = PermissionTarget.create(cpt, permission);
					break;
				}
			}
		}
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

	public PermissionTarget getUser() {
		return user;
	}

	public void setUser(PermissionTarget user) {
		this.user = user;
	}
}
