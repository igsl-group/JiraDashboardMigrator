package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
	public boolean jqlEquals(String value) {
		return 	id.equalsIgnoreCase(value) || 
				name.equalsIgnoreCase(value);
	}
	
	@Override
	public int compareTo(ProjectVersion obj1, boolean exactMatch) {
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

	private static class Process implements Callable<List<ProjectVersion>> {
		private String projectId;
		private boolean cloud;
		private Config config;
		public Process(Config config, boolean cloud, String projectId) {
			this.config = config;
			this.cloud = cloud;
			this.projectId = projectId;
		}
		@Override
		public List<ProjectVersion> call() throws Exception {
			RestUtil<ProjectVersion> util = RestUtil.getInstance(ProjectVersion.class)
					.config(config, cloud);
			util.path("/rest/api/latest/project/{projectId}/version")
				.pathTemplate("projectId", projectId)
				.method(HttpMethod.GET)
				.pagination(new Paged<ProjectVersion>(ProjectVersion.class));
			return util.requestAllPages();
		}
	}
	
	@Override
	protected List<ProjectVersion> _getObjects(
			Config config, 
			Class<ProjectVersion> dataClass, 
			boolean cloud,
			Object... data)
			throws Exception {
		Map<String, ProjectVersion> result = new HashMap<>();
		RestUtil<ProjectVersion> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		List<Project> projectList = DashboardMigrator.readValuesFromFile(
				(cloud? MappingType.PROJECT.getCloud() : MappingType.PROJECT.getDC()), 
				Project.class);
		ExecutorService service = Executors.newFixedThreadPool(config.getThreadCount());
		try {
			Map<Project, Future<List<ProjectVersion>>> futureMap = new HashMap<>();
			for (Project project : projectList) {
				futureMap.put(project, service.submit(new Process(config, cloud, project.getId())));
			}
			while (futureMap.size() != 0) {
				List<Project> toRemove = new ArrayList<>();
				for (Map.Entry<Project, Future<List<ProjectVersion>>> entry : futureMap.entrySet()) {
					try {
						Project project = entry.getKey();
						Future<List<ProjectVersion>> future = entry.getValue();
						List<ProjectVersion> list = future.get(config.getThreadWait(), TimeUnit.MILLISECONDS);
						toRemove.add(project);
						for (ProjectVersion ver : list) {
							ver.setProjectKey(project.getKey());
							result.put(ver.getId(), ver);
						}
					} catch (TimeoutException tex) {
						// Ignore and keep waiting
					}
				}
				for (Project f : toRemove) {
					futureMap.remove(f);
				}
			}
		} finally {
			service.shutdownNow();
		}
//		for (Project project : projectList) {
//			setupRestUtil(util, cloud, project.getId());
//			List<ProjectVersion> list = util.requestAllPages();
//			for (ProjectVersion ver : list) {
//				ver.setProjectKey(project.getKey());
//				result.put(ver.getId(), ver);
//			}
//		}
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
