package com.igsl.model.mapping;

import java.util.List;

public class GroupPickerResult {
	private int total;
	private List<Group> groups;

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public List<Group> getGroups() {
		return groups;
	}

	public void setGroups(List<Group> groups) {
		this.groups = groups;
	}
}
