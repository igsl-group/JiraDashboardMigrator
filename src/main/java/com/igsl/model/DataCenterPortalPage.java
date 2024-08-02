package com.igsl.model;

import java.util.ArrayList;
import java.util.List;

public class DataCenterPortalPage {
	private int id;
	private String pageName;
	private String description;
	private int sequence;
	private int favCount;
	private String layout;
	private int ppVersion;
	private String userDisplayName;
	private String username;
	private String accountId;
	private List<DataCenterPortalPermission> permissions = new ArrayList<DataCenterPortalPermission>();
	private List<DataCenterPortletConfiguration> portlets = new ArrayList<DataCenterPortletConfiguration>();

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getPageName() {
		return pageName;
	}

	public void setPageName(String pageName) {
		this.pageName = pageName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getSequence() {
		return sequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	public int getFavCount() {
		return favCount;
	}

	public void setFavCount(int favCount) {
		this.favCount = favCount;
	}

	public String getLayout() {
		return layout;
	}

	public void setLayout(String layout) {
		this.layout = layout;
	}

	public int getPpVersion() {
		return ppVersion;
	}

	public void setPpVersion(int ppVersion) {
		this.ppVersion = ppVersion;
	}

	public List<DataCenterPortletConfiguration> getPortlets() {
		return portlets;
	}

	public void setPortlets(List<DataCenterPortletConfiguration> portlets) {
		this.portlets = portlets;
	}

	public List<DataCenterPortalPermission> getPermissions() {
		return permissions;
	}

	public void setPermissions(List<DataCenterPortalPermission> permissions) {
		this.permissions = permissions;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getAccountId() {
		return accountId;
	}

	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}

	public String getUserDisplayName() {
		return userDisplayName;
	}

	public void setUserDisplayName(String userDisplayName) {
		this.userDisplayName = userDisplayName;
	}
}
