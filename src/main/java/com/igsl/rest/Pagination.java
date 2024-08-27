package com.igsl.rest;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class Pagination<T> {
	protected Class<T> dataClass;
	
	/**
	 * Constructor.
	 * @param dataClass
	 */
	public Pagination(Class<T> dataClass) {
		this.dataClass = dataClass;
	}
	
	/**
	 * Reset to internal state to read first page.
	 */
	public abstract void reset();
	
	/**
	 * Setup RESTUtil2 for next page.
	 */
	public abstract void setup(RestUtil<?> util);
	
	/**
	 * Provides Response for processing. 
	 * Status code is already verified before calling this method.
	 * @param response Response.
	 * @param om ObjectMapper.
	 * @throws JsonProcessingException
	 * @throws JsonMappingException
	 */
	public abstract void setResponse(Response response, ObjectMapper om) 
			throws JsonProcessingException, JsonMappingException, IOException;
	
	/**
	 * Tells if there may be more pages of results.
	 */
	public abstract boolean hasMore();
	
	/**
	 * Retrieve list of items.
	 */
	public abstract List<T> getObjects();
}