package com.igsl.model.mapping;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class Project extends JiraObject<Project> {
	private String id;
	private String name;
	private String key;
	private String projectTypeKey;

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
		return key;
	}
	
	@Override
	public boolean jqlEquals(String value) {
		return 	id.equalsIgnoreCase(value) || 
				name.equalsIgnoreCase(value) || 
				key.equalsIgnoreCase(value);
	}
	
	@Override
	public int compareTo(Project obj1, boolean exactMatch) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getName(), obj1.getName()) |
					STRING_COMPARATOR.compare(getKey(), obj1.getKey());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Project> util, boolean cloud, Object... data) {
		util.method(HttpMethod.GET);
		if (cloud) {
			util.path("/rest/api/latest/project/search")
				.pagination(new Paged<Project>(Project.class));
		} else {
			util.path("/rest/api/latest/project")
				.pagination(new SinglePage<Project>(Project.class, null));
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

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getProjectTypeKey() {
		return projectTypeKey;
	}

	public void setProjectTypeKey(String projectTypeKey) {
		this.projectTypeKey = projectTypeKey;
	}

}
