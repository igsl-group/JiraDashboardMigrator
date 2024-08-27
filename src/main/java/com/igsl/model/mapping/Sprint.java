package com.igsl.model.mapping;

import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class Sprint extends JiraObject<Sprint> {
	
	public static final String PARAM_BOARDID = "boardId";
	
	private String id;
	private String name;
	private String originBoardId;
	private String originalBoardName;
	private String originalBoardFilterName;
	
	@Override
	public int compareTo(Sprint obj1) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getName(), obj1.getName()) & 
					STRING_COMPARATOR.compare(getOriginalBoardName(), obj1.getOriginalBoardName()) & 
					STRING_COMPARATOR.compare(getOriginalBoardFilterName(), obj1.getOriginalBoardFilterName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Sprint> util, boolean cloud, Map<String, Object> data) {
		String boardId = String.valueOf(data.get(PARAM_BOARDID));
		util.path("/rest/agile/1.0/board/{boardId}/sprint")
			.pathTemplate(PARAM_BOARDID, boardId)
			.method(HttpMethod.GET)
			.pagination(new Paged<Sprint>(Sprint.class));
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
