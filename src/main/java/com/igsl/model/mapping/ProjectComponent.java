package com.igsl.model.mapping;

import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class ProjectComponent extends JiraObject<ProjectComponent> {
	public static final String PARAM_PROJECTID = "projectId";
	
	private String id;
	private String name;
	private String project;
	private String description;
	
	@Override
	public int compareTo(ProjectComponent obj1) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getName(), obj1.getName()) &
					STRING_COMPARATOR.compare(getProject(), obj1.getProject());
		}
		return 1;
	}
	@Override
	public void setupRestUtil(RestUtil<ProjectComponent> util, boolean cloud, Map<String, Object> data) {
		String projectId = String.valueOf(data.get(PARAM_PROJECTID));
		util.pathTemplate("projectId", projectId)
			.method(HttpMethod.GET);
		if (cloud) {
			util.path("/rest/api/latest/project/{projectId}/component")
				.pagination(new Paged<ProjectComponent>(ProjectComponent.class));
		} else {
			util.path("/rest/api/latest/project/{projectId}/components")
				.pagination(new SinglePage<ProjectComponent>(ProjectComponent.class, null));
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
	public String getProject() {
		return project;
	}
	public void setProject(String project) {
		this.project = project;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
}
