package com.igsl.model.mapping;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class Status extends JiraObject<Status> {
	private String id;
	private String name;
	private String description;

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
	public int compareTo(Status obj1, boolean exactMatch) {
		if (obj1 != null) {
			return compareName(getName(), obj1.getName(), exactMatch);
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Status> util, boolean cloud, Object... data) {
		// Status list is returned as a top-level array in a single page
		util.path("/rest/api/latest/status")
			.method(HttpMethod.GET)
			.pagination(new SinglePage<Status>(Status.class, null));
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
}
