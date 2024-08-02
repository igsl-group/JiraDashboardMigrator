package com.igsl.mybatis;

import java.util.List;

import com.igsl.model.DataCenterPortalPage;

public interface FilterMapper {
	public List<Integer> getFilters();

	public List<DataCenterPortalPage> getDashboards();
}
