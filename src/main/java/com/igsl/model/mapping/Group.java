package com.igsl.model.mapping;

import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class Group extends JiraObject<Group> {
	private String groupId;
	private String name;
	private String html;

	@Override
	public int compareTo(Group obj1) {
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getName(), obj1.getName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Group> util, boolean cloud, Map<String, Object> data) {
		if (cloud) {
			util.path("/rest/api/latest/group/bulk")
				.method(HttpMethod.GET)
				.pagination(new Paged<Group>(Group.class));
		} else {
			// There is no API to get all groups, wtf
			util.path("/rest/api/latest/groups/picker")
				.method(HttpMethod.GET)
				.query("maxResults", 1000)
				.pagination(new SinglePage<Group>(Group.class, "groups"));
		}
	}
	
	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getHtml() {
		return html;
	}

	public void setHtml(String html) {
		this.html = html;
	}

}
