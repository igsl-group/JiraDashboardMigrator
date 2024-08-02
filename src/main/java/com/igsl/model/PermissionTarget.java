package com.igsl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PermissionTarget {
	private String id; // For requesting project and role
	private String accountId; // For requesting user
	private String groupId; // For requesting group

	private String key; // For project ID mapping
	private String name; // For group ID mapping
	private String displayName; // For user ID mapping

	public static PermissionTarget create(PermissionType type, DataCenterPortalPermission permission) {
		PermissionTarget result = new PermissionTarget();
		switch (type) {
		case GROUP:
			result.name = permission.getParam1();
			break;
		case PROJECT_ROLE:
			result.id = permission.getParam2();
			break;
		case PROJECT:
			result.id = permission.getParam1();
			break;
		case USER:
			result.accountId = permission.getParam1();
			break;
		default:
			break;
		}
		return result;
	}

	public static PermissionTarget create(PermissionType type, DataCenterPermission permission) {
		PermissionTarget result = new PermissionTarget();
		switch (type) {
		case GROUP:
			result.groupId = permission.getGroup().getGroupId();
			break;
		case PROJECT_ROLE:
			result.id = permission.getRole().getId();
			break;
		case PROJECT:
			result.id = permission.getProject().getId();
			break;
		case USER:
			result.accountId = permission.getUser().getAccountId();
			break;
		default:
			break;
		}
		return result;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getAccountId() {
		return accountId;
	}

	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}
}
