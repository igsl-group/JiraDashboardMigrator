package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.config.Config;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class Sprint extends JiraObject<Sprint> {
	private String id;
	private String name;
	private String originBoardId;
	private String originalBoardName;
	private String originalBoardFilterName;
	
	@Override
	public int compareTo(Sprint obj1) {
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
	
	@Override
	protected List<Sprint> _getObjects(
			Config config, 
			Class<Sprint> dataClass, 
			boolean cloud,
			Map<MappingType, List<? extends JiraObject<?>>> map, 
			Object... data)
			throws Exception {
		List<Sprint> result = new ArrayList<>();
		RestUtil<Sprint> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		@SuppressWarnings("unchecked")
		List<AgileBoard> boardList = (List<AgileBoard>) map.get(MappingType.AGILE_BOARD);
		for (AgileBoard board : boardList) {
			if (board.canHasSprint()) {
				setupRestUtil(util, cloud, board.getId());
				List<Sprint> list = util.requestAllPages();
				result.addAll(list);
			}
		}
		return result;
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
