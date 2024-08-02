package com.igsl.model.mapping;

import java.util.List;

public class DashboardSearchResult {
	private int total;
	private List<Dashboard> dashboards;

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public List<Dashboard> getDashboards() {
		return dashboards;
	}

	public void setDashboards(List<Dashboard> dashboards) {
		this.dashboards = dashboards;
	}

}
