package com.igsl.model.mapping;

import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class IssueType extends JiraObject<IssueType> {

	private String id;
	private String name;
	private boolean subtask;
	
	@Override
	public int compareTo(IssueType obj1) {
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getName(), obj1.getName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<IssueType> util, boolean cloud, Object... data) {
		util.path("/rest/api/latest/issuetype")
			.method(HttpMethod.GET)
			.pagination(new SinglePage<IssueType>(IssueType.class, null));
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

	public boolean isSubtask() {
		return subtask;
	}

	public void setSubtask(boolean subtask) {
		this.subtask = subtask;
	}

}
