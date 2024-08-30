package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.config.Config;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class ProjectComponent extends JiraObject<ProjectComponent> {
	
	private String id;
	private String name;
	private String project;
	private String description;
	
	@Override
	public int compareTo(ProjectComponent obj1) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getName(), obj1.getName()) |
					STRING_COMPARATOR.compare(getProject(), obj1.getProject());
		}
		return 1;
	}
	@Override
	public void setupRestUtil(RestUtil<ProjectComponent> util, boolean cloud, Object... data) {
		String projectId = String.valueOf(data[0]);
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

	@Override
	protected List<ProjectComponent> _getObjects(
			Config config, 
			Class<ProjectComponent> dataClass, 
			boolean cloud,
			Map<MappingType, List<? extends JiraObject<?>>> map, 
			Object... data)
			throws Exception {
		List<ProjectComponent> result = new ArrayList<>();
		RestUtil<ProjectComponent> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		@SuppressWarnings("unchecked")
		List<Project> projectList = (List<Project>) map.get(MappingType.PROJECT);
		for (Project project : projectList) {
			setupRestUtil(util, cloud, project.getId());
			List<ProjectComponent> list = util.requestAllPages();
			result.addAll(list);
		}
		return result;
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
