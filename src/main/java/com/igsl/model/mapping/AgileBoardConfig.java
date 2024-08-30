package com.igsl.model.mapping;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.config.Config;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class AgileBoardConfig extends JiraObject<AgileBoardConfig> {
	private Filter filter;

	@Override
	public int compareTo(AgileBoardConfig obj1) {
		if (obj1 != null && obj1.getFilter() != null && getFilter() != null) {
			return getFilter().compareTo(obj1.getFilter());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<AgileBoardConfig> util, boolean cloud, Object... data) {
		String boardId = String.valueOf(data[0]);
		util.path("/rest/agile/1.0/board/{boardId}/configuration")
			.pathTemplate("boardId", boardId)
			.method(HttpMethod.GET)
			.pagination(new SinglePage<AgileBoardConfig>(AgileBoardConfig.class, null));
	}
	
	// Override to get filter name
	@Override
	protected List<AgileBoardConfig> _getObjects(
			Config config, Class<AgileBoardConfig> dataClass, boolean cloud, 
			Map<MappingType, List<? extends JiraObject<?>>> map, 
			Object... data)
			throws Exception {
		RestUtil<AgileBoardConfig> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		setupRestUtil(util, cloud, data);
		List<AgileBoardConfig> result = util.requestAllPages();
		if (result.size() == 1) {
			// Get filter name
			Filter filter = new Filter();
			// Get single filter
			List<Filter> filterList = JiraObject.getObjects(
					config, Filter.class, cloud, map, result.get(0).getFilter().getId());
			if (filterList.size() == 1) {
				result.get(0).getFilter().setName(filterList.get(0).getName());
			}
		}
		return result;
	}
	
	public Filter getFilter() {
		return filter;
	}

	public void setFilter(Filter filter) {
		this.filter = filter;
	}
}
