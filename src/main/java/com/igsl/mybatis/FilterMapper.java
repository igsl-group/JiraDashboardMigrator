package com.igsl.mybatis;

import java.util.List;

import com.igsl.model.DataCenterPortalPage;
import com.igsl.model.mapping.CustomFieldOptionDTO;

public interface FilterMapper {
	public List<Integer> getFilters();

	public List<DataCenterPortalPage> getDashboards();
	
	public List<CustomFieldOptionDTO> getCustomFieldOptions(String customFieldId);
}
