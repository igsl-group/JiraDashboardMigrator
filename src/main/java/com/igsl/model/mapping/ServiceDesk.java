package com.igsl.model.mapping;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.Paged;
import com.igsl.rest.Pagination;
import com.igsl.rest.RestUtil;

public class ServiceDesk extends JiraObject<ServiceDesk> {
	
	private String id;
	private String projectId;
	private String projectKey;
	private String projectName;
	
	@Override
	public String getDisplay() {
		return id;
	}
	
	@Override
	public String getInternalId() {
		return id;
	}

	@Override
	public String getJQLName() {
		return id;
	}
	
	@Override
	public boolean jqlEquals(String value) {
		return false;
	}
	
	@Override
	public int compareTo(ServiceDesk obj1, boolean exactMatch) {
		// Each project can only have 1 customer portal, so compare using project key
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getProjectKey(), obj1.getProjectKey());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<ServiceDesk> util, boolean cloud, Object... data) {
		Pagination<ServiceDesk> p = new Paged<ServiceDesk>(ServiceDesk.class)
				.totalProperty("size")
				.startAtParameter("start")
				.maxResultsProperty("limit");
		util.path("/rest/servicedeskapi/servicedesk")
			.method(HttpMethod.GET)
			.pagination(p);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}
	
}
