package com.igsl.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * For REST APIs without pagination, 
 * where the result is a top-level item or array, 
 * optionally under a top level attribute name.
 * @param <T>
 */
public class SinglePage<T> extends Pagination<T> {
	private List<T> values;
	private String attributeName = null;
	
	public SinglePage(Class<T> dataClass) {
		super(dataClass);
	}
	
	public SinglePage(Class<T> dataClass, String attributeName) {
		super(dataClass);
		this.attributeName = attributeName;
	}
	
	@Override
	public void reset() {
		values = null;
	}

	@Override
	public void setup(RestUtil<?> util) {
		// Do nothing
	}

	@Override
	public void setResponse(Response response, ObjectMapper om) throws IOException {
		JsonNode node = om.readTree(response.readEntity(String.class));
		if (node != null) {
			if (attributeName != null) {
				node = node.get(attributeName);
			}
			if (node != null) {
				// Get top-level array or item
				values = new ArrayList<>();
				if (node.isArray()) {
					for (JsonNode item : node) {
						T value = om.treeToValue(item, dataClass);
						values.add(value);
					}
				} else {
					T value = om.treeToValue(node, dataClass);
					values.add(value);
				}
			}
		}
	}

	@Override
	public boolean hasMore() {
		return false;
	}

	@Override
	public List<T> getObjects() {
		return values;
	}

}
