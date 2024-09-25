package com.igsl.model.mapping;

import com.igsl.rest.RestUtil;

public class Dashboard extends JiraObject<Dashboard> {
	private String id;

	@Override
	public String getDisplay() {
		return id;
	}
	
	@Override
	public String getInternalId() {
		return id;
	}

	@Override
	public String getJQLName() {
		return id;
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public int compareTo(Dashboard obj1, boolean exactMatch) {
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getId(), obj1.getId());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Dashboard> util, boolean cloud, Object... data) {
		// Not used 
	}

}
