package com.igsl.model.mapping;

import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class Filter extends JiraObject<Filter> {
	public static final String PARAM_FILTERID = "filterId";
	private String id;
	private String name;

	@Override
	public int compareTo(Filter obj1) {
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getName(), obj1.getName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Filter> util, boolean cloud, Map<String, Object> data) {
		String filterId = null;
		if (data.containsKey(PARAM_FILTERID)) {
			filterId = String.valueOf(data.get(PARAM_FILTERID));
		}
		if (filterId != null) {
			util.path("/rest/api/latest/filter/{filterId}")
				.pathTemplate(PARAM_FILTERID, filterId)
				.method(HttpMethod.GET)
				.pagination(new SinglePage<Filter>(Filter.class, null));
		} else {
			util.path("/rest/api/latest/filter/search")
				.method(HttpMethod.GET)
				.pagination(new Paged<Filter>(Filter.class));
		}
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
}
