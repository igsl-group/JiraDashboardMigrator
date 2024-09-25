package com.igsl.model.mapping;

import javax.ws.rs.HttpMethod;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class Priority extends JiraObject<Priority> {

	private String id;
	private String name;
	private String description;
	private String statusColor;
	private String iconUrl;
	@JsonProperty("isDefault")
	private boolean defaultPriority;
	
	@Override
	public String getDisplay() {
		return name;
	}
	
	@Override
	public String getInternalId() {
		return id;
	}

	@Override
	public String getJQLName() {
		return name;
	}
	
	@Override
	public int compareTo(Priority obj1, boolean exactMatch) {
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getName(), obj1.getName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Priority> util, boolean cloud, Object... data) {
		util.method(HttpMethod.GET);
		if (cloud) {
			util.path("/rest/api/3/priority/search")
				.method(HttpMethod.GET)
				.pagination(new Paged<Priority>(Priority.class));
		} else {
			util.path("/rest/api/2/priority")
			.method(HttpMethod.GET)
			.pagination(new SinglePage<Priority>(Priority.class, null));
		}
	}

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

	public String getStatusColor() {
		return statusColor;
	}

	public void setStatusColor(String statusColor) {
		this.statusColor = statusColor;
	}

	public String getIconUrl() {
		return iconUrl;
	}

	public void setIconUrl(String iconUrl) {
		this.iconUrl = iconUrl;
	}

	public boolean isDefaultPriority() {
		return defaultPriority;
	}

	public void setDefaultPriority(boolean defaultPriority) {
		this.defaultPriority = defaultPriority;
	}
}
