package com.igsl.mybatis;

import java.util.List;

import com.igsl.model.DataCenterPortalPage;
import com.igsl.model.mapping.CustomFieldOptionDTO;
import com.igsl.model.mapping.Group;
import com.igsl.model.mapping.User;

public interface FilterMapper {
	public List<Integer> getFilters();

	public List<DataCenterPortalPage> getDashboards();
	
	public List<CustomFieldOptionDTO> getCustomFieldOptions(String customFieldId);
	
	public List<User> getUsers();
	
	public List<Group> getGroups();
}
