package com.igsl.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Paged<T> extends Pagination<T> {

	public static final String DEFAULT_START_AT = "startAt";
	public static final String DEFAULT_MAX_RESULTS = "maxResults";
	public static final String DEFAULT_TOTAL = "total";
	public static final String DEFAULT_VALUES = "values";
	
	private String startAtParameterName = DEFAULT_START_AT;
	private String maxResultsParameterName = DEFAULT_MAX_RESULTS;
	private String totalParameterName = DEFAULT_TOTAL;
	private String valuesParameterName = DEFAULT_VALUES;

	private Integer initialStartAt = 0;
	private boolean hasMore;
	private Integer total;
	private Integer maxResults;
	private Integer startAt = 0;
	private Response response;
	private List<T> values;
	
	/**
	 * Constructor.
	 * @param dataClass
	 */
	public Paged(Class<T> dataClass) {
		super(dataClass);
	}
	
	public Paged(Class<T> dataClass, 
			String startAt, int startAtValue, 
			String maxResults, int maxResultsValue, 
			String total, String values) {
		super(dataClass);
		startAtParameter(startAt);
		maxResultsProperty(maxResults);
		totalProperty(total);
		valuesProperty(values);
	}
	
	/**
	 * Set start at query parameter name.
	 * Default is {@value #DEFAULT_START_AT}.
	 * @param startAtParameterName
	 * @return
	 */
	public Paged<T> startAtParameter(String startAtParameterName) {
		this.startAtParameterName = startAtParameterName;
		return this;
	}
	
	/**
	 * Set initial start at value.
	 * Default is 0.
	 * @param startAt
	 * @return
	 */
	public Paged<T> startAt(Integer startAt) {
		this.startAt = startAt;
		this.initialStartAt = startAt;
		return this;
	}
	
	/**
	 * Set query parameter name for max no. of results.
	 * Default is {@value #DEFAULT_MAX_RESULTS}.
	 * @param maxResultsParameterName
	 */
	public Paged<T> maxResultsProperty(String maxResultsParameterName) {
		this.maxResultsParameterName = maxResultsParameterName;
		return this;
	}
	
	/**
	 * Set max no. of results.
	 * Default is {@literal null}.
	 * @param maxResults
	 */
	public Paged<T> maxResults(Integer maxResults) {
		this.maxResults = maxResults;
		return this;
	}
	
	/**
	 * Set JSON property name for total no. of items.
	 * Default is {@value Paged#DEFAULT_TOTAL}.
	 * @param totalParameterName
	 */
	public Paged<T> totalProperty(String totalParameterName) {
		this.totalParameterName = totalParameterName;
		return this;
	}
	
	/**
	 * Set JSON property name containing value or array of values.
	 * Default is {@value #DEFAULT_VALUES}.
	 * @param valuesParameterName
	 */
	public Paged<T> valuesProperty(String valuesParameterName) {
		this.valuesParameterName = valuesParameterName;
		return this;
	}

	@Override
	public void reset() {
		this.response = null;
		this.startAt = this.initialStartAt;
		this.total = null;
		this.values = null;
	}

	@Override
	public void setup(RestUtil<?> util) {
		if (this.startAtParameterName != null && 
			!this.startAtParameterName.isBlank() &&
			this.startAt != null) {
			util.query(this.startAtParameterName, this.startAt);
		}
		if (this.maxResultsParameterName != null && 
			!this.maxResultsParameterName.isBlank() && 
			this.maxResults != null) {
			util.query(this.maxResultsParameterName, this.maxResults);
		}
	}

	@Override
	public void setResponse(Response response, ObjectMapper om) 
			throws JsonProcessingException, IllegalArgumentException, IOException {
		this.response = response;
		// Parse response
		String jsonString = this.response.readEntity(String.class);		
		JsonNode root = om.readTree(jsonString);
		// Total
		total = null;
		if (this.totalParameterName != null && !this.totalParameterName.isBlank()) {
			JsonNode totalNode = root.get(totalParameterName);
			if (totalNode != null && totalNode.isInt()) {
				total = totalNode.asInt();
			}
		}
		// Values
		if (valuesParameterName != null && !valuesParameterName.isBlank()) {
			JsonNode valuesNode = root.get(valuesParameterName);
			if (valuesNode != null) {
				this.values = new ArrayList<>();
				if (valuesNode.isArray()) {
					// Array
					for (JsonNode arrayItem : valuesNode) {
						T item = om.treeToValue(arrayItem, dataClass);
						this.values.add(item);
					}
				} else {
					// Single item
					T item = om.treeToValue(valuesNode, dataClass);
					this.values.add(item);
				}
	 		} else {
	 			this.values = null;
	 		}
		} else {
			// Root should be array of values
			if (root.isArray()) {
				this.values = new ArrayList<>();
				for (JsonNode arrayItem : root) {
					T item = om.treeToValue(arrayItem, dataClass);
					this.values.add(item);
				}
			} else if (root.isObject()) {
				// Single item
				this.values = new ArrayList<>();
				T item = om.treeToValue(root, dataClass);
				this.values.add(item);
			} else {
				this.values = null;
			}
		}
		// Modify startAt
		int size = 0;
		if (this.values != null) {
			size = this.values.size();
		}
		this.startAt += size;
		// Has more
		if (this.total != null) {
			this.hasMore = this.startAt < total;
		} else {
			this.hasMore = (size != 0);
		}
	}
	
	@Override
	public boolean hasMore() {
		return this.hasMore;
	}

	@Override
	public List<T> getObjects() {
		return this.values;
	}
}
