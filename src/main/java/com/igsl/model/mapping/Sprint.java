package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.ws.rs.HttpMethod;

import com.igsl.DashboardMigrator;
import com.igsl.config.Config;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class Sprint extends JiraObject<Sprint> {
	private String id;
	private String name;
	private String originBoardId;
	private String originalBoardName;
	private String originalBoardFilterName;
	// Note: start and endDate can be different in DC and Cloud after migration
	
	@Override
	public String getDisplay() {
		return name;
	}
	
	@Override
	public String getInternalId() {
		return id;
	}

	@Override
	public String getJQLName() {
		return id;
	}
	
	@Override
	public boolean jqlEquals(String value) {
		return id.equals(value) || 
				name.equals(value);
	}
	
	@Override
	public int compareTo(Sprint obj1, boolean exactMatch) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getName(), obj1.getName()) |
					STRING_COMPARATOR.compare(getOriginalBoardName(), obj1.getOriginalBoardName()) | 
					STRING_COMPARATOR.compare(getOriginalBoardFilterName(), obj1.getOriginalBoardFilterName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Sprint> util, boolean cloud, Object... data) {
		String boardId = String.valueOf(data[0]);
		util.path("/rest/agile/1.0/board/{boardId}/sprint")
			.pathTemplate("boardId", boardId)
			.method(HttpMethod.GET)
			.pagination(new Paged<Sprint>(Sprint.class));
	}
	
	public static class Process implements Callable<List<Sprint>> {
		private Config config;
		private boolean cloud;
		private String boardId;
		public Process(Config config, boolean cloud, String boardId) {
			this.config = config;
			this.cloud = cloud;
			this.boardId = boardId;
		}
		@Override
		public List<Sprint> call() throws Exception {
			RestUtil<Sprint> util = RestUtil.getInstance(Sprint.class)
					.config(config, cloud);
			util.path("/rest/agile/1.0/board/{boardId}/sprint")
				.pathTemplate("boardId", boardId)
				.method(HttpMethod.GET)
				.pagination(new Paged<Sprint>(Sprint.class));
			return util.requestAllPages();
		}		
	}
	
	@Override
	protected List<Sprint> _getObjects(
			Config config, 
			Class<Sprint> dataClass, 
			boolean cloud,
			Object... data)
			throws Exception {
		// Multiple boards can return the same sprints
		// So store them in a map to eliminate duplicates
		Map<String, Sprint> result = new HashMap<>();
		RestUtil<Sprint> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		List<AgileBoard> boardList = DashboardMigrator.readValuesFromFile(
				(cloud? MappingType.AGILE_BOARD.getCloud() : MappingType.AGILE_BOARD.getDC()), 
				AgileBoard.class);
		for (AgileBoard board : boardList) {
			if (board.canHasSprint()) {
				setupRestUtil(util, cloud, board.getId());
				List<Sprint> list = util.requestAllPages();
				for (Sprint sp : list) {
					sp.setOriginalBoardName(board.getName());
					sp.setOriginalBoardFilterName(board.getFilterName());
					result.put(sp.getId(), sp);
				}
			}
		}
		List<Sprint> list = new ArrayList<>();
		list.addAll(result.values());
		return list;
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

	public String getOriginBoardId() {
		return originBoardId;
	}

	public void setOriginBoardId(String originBoardId) {
		this.originBoardId = originBoardId;
	}

	public String getOriginalBoardName() {
		return originalBoardName;
	}

	public void setOriginalBoardName(String originalBoardName) {
		this.originalBoardName = originalBoardName;
	}

	public String getOriginalBoardFilterName() {
		return originalBoardFilterName;
	}

	public void setOriginalBoardFilterName(String originalBoardFilterName) {
		this.originalBoardFilterName = originalBoardFilterName;
	}
}
