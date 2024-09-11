package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.DashboardMigrator;
import com.igsl.config.Config;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class ProjectVersion extends JiraObject<ProjectVersion> {
	private String id;
	private String name;
	private String description;
	private String projectKey;

	@Override
	public int compareTo(ProjectVersion obj1) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getName(), obj1.getName()) | 
					STRING_COMPARATOR.compare(getProjectKey(), obj1.getProjectKey());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<ProjectVersion> util, boolean cloud, Object... data) {
		String projectId = String.valueOf(data[0]);
		util.path("/rest/api/latest/project/{projectId}/version")
			.pathTemplate("projectId", projectId)
			.method(HttpMethod.GET)
			.pagination(new Paged<ProjectVersion>(ProjectVersion.class));
	}

	@Override
	protected List<ProjectVersion> _getObjects(
			Config config, 
			Class<ProjectVersion> dataClass, 
			boolean cloud,
			Object... data)
			throws Exception {
		// Multiple boards can return the same sprints
		// So store them in a map to eliminate duplicates
		Map<String, ProjectVersion> result = new HashMap<>();
		RestUtil<ProjectVersion> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		List<Project> projectList = DashboardMigrator.readValuesFromFile(
				(cloud? MappingType.PROJECT.getCloud() : MappingType.PROJECT.getDC()), 
				Project.class);
		for (Project project : projectList) {
			setupRestUtil(util, cloud, project.getId());
			List<ProjectVersion> list = util.requestAllPages();
			for (ProjectVersion ver : list) {
				ver.setProjectKey(project.getKey());
				result.put(ver.getId(), ver);
			}
		}
		List<ProjectVersion> list = new ArrayList<>();
		list.addAll(result.values());
		return list;
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

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

}
