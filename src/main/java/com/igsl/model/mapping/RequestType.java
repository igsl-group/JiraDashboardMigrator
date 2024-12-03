package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.HttpMethod;

import com.igsl.DashboardMigrator;
import com.igsl.config.Config;
import com.igsl.rest.Paged;
import com.igsl.rest.Pagination;
import com.igsl.rest.RestUtil;

public class RequestType extends JiraObject<RequestType> {
	
	private String id;
	private String name;
	private String description;
	private String serviceDeskId;
	private String projectKey;	// Obtained from associated service desk
	
	@Override
	public String getDisplay() {
		return name;
	}
	
	@Override
	public String getInternalId() {
		return id;
	}

	@Override
	public String getAdditionalDetails() {
		return "Project: " + projectKey;
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
	public int compareTo(RequestType obj1, boolean exactMatch) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getProjectKey(), obj1.getProjectKey()) | 
					STRING_COMPARATOR.compare(getName(), obj1.getName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<RequestType> util, boolean cloud, Object... data) {
		String id = String.valueOf(data[0]);
		Pagination<RequestType> p = new Paged<RequestType>(RequestType.class)
				.totalProperty("size")
				.startAtParameter("start")
				.maxResultsProperty("limit");
		util.path("/rest/servicedeskapi/servicedesk/{serviceDeskId}/requesttype")
			.pathTemplate("serviceDeskId", id)
			.method(HttpMethod.GET)
			.pagination(p);
	}
	
	@Override
	protected List<RequestType> _getObjects(
			Config config, 
			Class<RequestType> dataClass, 
			boolean cloud,
			Object... data)
			throws Exception {
		List<RequestType> result = new ArrayList<>();
		RestUtil<RequestType> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		List<ServiceDesk> serviceDeskList = DashboardMigrator.readValuesFromFile(
				(cloud? MappingType.SERVICE_DESK.getCloud() : MappingType.SERVICE_DESK.getDC()), 
				ServiceDesk.class);
		for (ServiceDesk serviceDesk : serviceDeskList) {
			setupRestUtil(util, cloud, serviceDesk.getId());
			List<RequestType> list = util.requestAllPages();
			for (RequestType rt : list) {
				rt.setProjectKey(serviceDesk.getProjectKey());
			}
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getServiceDeskId() {
		return serviceDeskId;
	}

	public void setServiceDeskId(String serviceDeskId) {
		this.serviceDeskId = serviceDeskId;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}
	
}
