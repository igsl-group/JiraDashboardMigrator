package com.igsl.model.mapping;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.config.Config;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class AgileBoard extends JiraObject<AgileBoard> {
	public static final String TYPE_SCRUM = "scrum";	// AgileBoard type that allows Sprint
	
	private String id;
	private String name;
	private String type;
	// Name and type could be identical, so also grab filter name
	private String filterName;

	@Override
	public int compareTo(AgileBoard obj1) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getName(), obj1.getName()) &
					STRING_COMPARATOR.compare(getFilterName(), obj1.getFilterName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<AgileBoard> util, boolean cloud, Map<String, Object> data) {
		util.path("/rest/agile/1.0/board")
			.method(HttpMethod.GET)
			.pagination(new Paged<AgileBoard>(AgileBoard.class));
	}
	
	// Override to get agile board configuration
	@Override
	protected List<AgileBoard> getObjects(
			Config config, Class<AgileBoard> dataClass, Map<String, Object> data, boolean cloud)
			throws IOException, IllegalStateException, URISyntaxException {
		RestUtil<AgileBoard> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		if (data == null) {
			data = new HashMap<>();
		}
		setupRestUtil(util, cloud, data);
		List<AgileBoard> result = util.requestAllPages();
		// Get filter name
		AgileBoardConfig conf = new AgileBoardConfig();
		for (AgileBoard board : result) {
			Map<String, Object> confData = new HashMap<>();
			confData.put(AgileBoardConfig.PARAM_BOARDID, board.getId());
			List<AgileBoardConfig> confList = conf.getObjects(config, AgileBoardConfig.class, confData, cloud);
			if (confList.size() == 1) {
				board.setFilterName(confList.get(0).getFilter().getName());
			}
		}
		return result;
	}
	
	/**
	 * Only SCRUM type boards can have sprints
	 */
	public boolean canHasSprint() {
		return TYPE_SCRUM.equals(type);
	}
	
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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getFilterName() {
		return filterName;
	}

	public void setFilterName(String filterName) {
		this.filterName = filterName;
	}
}
