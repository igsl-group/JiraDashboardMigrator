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
	public static final String PARAM_BOARDID = "boardId";
	private Filter filter;

	@Override
	public int compareTo(AgileBoardConfig obj1) {
		if (obj1 != null && obj1.getFilter() != null && getFilter() != null) {
			return getFilter().compareTo(obj1.getFilter());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<AgileBoardConfig> util, boolean cloud, Map<String, Object> data) {
		String boardId = String.valueOf(data.get(PARAM_BOARDID));
		util.path("/rest/agile/1.0/board/{boardId}/configuration")
			.pathTemplate(PARAM_BOARDID, boardId)
			.method(HttpMethod.GET)
			.pagination(new SinglePage<AgileBoardConfig>(AgileBoardConfig.class, null));
	}
	
	// Override to get filter name
	@Override
	protected List<AgileBoardConfig> getObjects(
			Config config, Class<AgileBoardConfig> dataClass, Map<String, Object> data, boolean cloud)
			throws IOException, IllegalStateException, URISyntaxException {
		RestUtil<AgileBoardConfig> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		if (data == null) {
			data = new HashMap<>();
		}
		setupRestUtil(util, cloud, data);
		List<AgileBoardConfig> result = util.requestAllPages();
		if (result.size() == 1) {
			// Get filter name
			Filter filter = new Filter();
			// Get single filter
			Map<String, Object> fData = new HashMap<>();
			fData.put(Filter.PARAM_FILTERID, result.get(0).getFilter().getId());
			List<Filter> fList = filter.getObjects(config, Filter.class, fData, cloud);
			if (fList.size() == 1) {
				result.get(0).getFilter().setName(fList.get(0).getName());
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
