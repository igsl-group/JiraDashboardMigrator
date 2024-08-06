package com.igsl.model.mapping;

public class Project {
	private String id;
	private String name;
	private String key;
	private String projectTypeKey;

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

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getProjectTypeKey() {
		return projectTypeKey;
	}

	public void setProjectTypeKey(String projectTypeKey) {
		this.projectTypeKey = projectTypeKey;
	}
}
