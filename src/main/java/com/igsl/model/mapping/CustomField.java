package com.igsl.model.mapping;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class CustomField extends JiraObject<CustomField> {
	private String id;
	private String name;
	private boolean custom;
	private Schema schema;

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
	public int compareTo(CustomField obj1, boolean exactMatch) {
		if (obj1 != null) {
			return 	compareName(getName(), obj1.getName(), exactMatch) | 
					Schema.COMPARATOR.compare(getSchema(), obj1.getSchema());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<CustomField> util, boolean cloud, Object... data) {
		util.path("rest/api/latest/field")
			.method(HttpMethod.GET)
			.pagination(new SinglePage<CustomField>(CustomField.class, null));
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Schema getSchema() {
		return schema;
	}

	public void setSchema(Schema schema) {
		this.schema = schema;
	}

	public boolean isCustom() {
		return custom;
	}

	public void setCustom(boolean custom) {
		this.custom = custom;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
