package com.igsl.model.mapping;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class DashboardGadget extends JiraObject<DashboardGadget> {

	private int id;
	private String moduleKey;
	
	@Override
	public int compareTo(DashboardGadget obj1) {
		if (obj1 != null) {
			return Integer.compare(getId(), obj1.getId());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<DashboardGadget> util, boolean cloud, Object... data) {
		String id = String.valueOf(data[0]);
		util.path("/rest/api/latest/dashboard/{boardId}/gadget")
			.pathTemplate("boardId", id)
			.method(HttpMethod.GET)
			.pagination(new SinglePage<DashboardGadget>(DashboardGadget.class, "gadgets"));
	}

	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getModuleKey() {
		return moduleKey;
	}
	public void setModuleKey(String moduleKey) {
		this.moduleKey = moduleKey;
	}
}
