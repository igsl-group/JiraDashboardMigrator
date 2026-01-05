package com.igsl.model.mapping;

import java.util.List;

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
	public String getDisplay() {
		return name;
	}
	
	@Override
	public String getInternalId() {
		return id;
	}
	
	@Override
	public String getAdditionalDetails() {
		return "Filter name: " + filterName;
	}

	@Override
	public String getJQLName() {
		return name;
	}
		
	@Override
	public boolean jqlEquals(String value) {
		return false;
	}
	
	@Override
	public int compareTo(AgileBoard obj1, boolean exactMatch) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getName(), obj1.getName()) |
					STRING_COMPARATOR.compare(getFilterName(), obj1.getFilterName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<AgileBoard> util, boolean cloud, Object... data) {
		util.path("/rest/agile/1.0/board")
			.method(HttpMethod.GET)
			.pagination(new Paged<AgileBoard>(AgileBoard.class));
	}
	
	// Override to get agile board configuration
	@Override
	protected List<AgileBoard> _getObjects(
			Config config, Class<AgileBoard> dataClass, boolean cloud, 
			Object... data)
			throws Exception {
		RestUtil<AgileBoard> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		setupRestUtil(util, cloud, data);
		List<AgileBoard> result = util.requestAllPages();
		// Get agile board config, and therefore, filter name
		for (AgileBoard board : result) {
			List<AgileBoardConfig> confList = 
					JiraObject.getObjects(config, AgileBoardConfig.class, cloud, board.getId());
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
