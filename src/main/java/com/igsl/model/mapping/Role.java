package com.igsl.model.mapping;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class Role extends JiraObject<Role> {
	private String id;
	private String name;
	private String description;

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
		return null;
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
	public int compareTo(Role obj1, boolean exactMatch) {
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getName(), obj1.getName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Role> util, boolean cloud, Object... data) {
		util.path("/rest/api/latest/role")
			.method(HttpMethod.GET)
			.pagination(new SinglePage<Role>(Role.class, null));
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

}
