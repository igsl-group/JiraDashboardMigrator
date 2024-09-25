package com.igsl.model.mapping;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class User extends JiraObject<User> {
	private String name;
	private String key;
	private String accountId;
	private String displayName;
	private boolean active;
	private boolean deleted;
	private String emailAddress;

	@Override
	public String getDisplay() {
		return displayName;
	}
	
	@Override
	public String getInternalId() {
		if (accountId != null) {
			return accountId;
		}
		return name;
	}

	@Override
	public String getJQLName() {
		return accountId;
	}
	
	@Override
	public int compareTo(User obj1, boolean exactMatch) {
		// Note: User can only be compared using CSV exported from Cloud User Management
		// As via REST API all emailAddress except yourself will be null
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getEmailAddress(), obj1.getEmailAddress());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<User> util, boolean cloud, Object...	 data) {
		util.method(HttpMethod.GET);
		Paged<User> paging = new Paged<User>(User.class, "startAt", 0, null, 0, null, null);
		if (cloud) {
			util.path("/rest/api/latest/users/search")
				.query("query", ".")
				.query("includeActive", true)
				.query("includeInactive", true)
				.pagination(paging);
		} else {
			util.path("/rest/api/latest/user/search")
				.query("username", ".")
				.query("includeActive", true)
				.query("includeInactive", true)
				.pagination(paging);
		}
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getAccountId() {
		return accountId;
	}

	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}
}
