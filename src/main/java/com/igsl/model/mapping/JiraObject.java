package com.igsl.model.mapping;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.igsl.config.Config;
import com.igsl.rest.RestUtil;

public abstract class JiraObject<T> implements Comparable<T> {

	protected static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder());
	
	// Compare objects
	public abstract int compareTo(T obj1);
	
	// Setup RestUtil, for cloud or DC
	public abstract void setupRestUtil(RestUtil<T> util, boolean cloud, Map<String, Object> data);

	protected List<T> getObjects(Config config, Class<T> dataClass, Map<String, Object> data, boolean cloud)
			throws IOException, IllegalStateException, URISyntaxException {
		RestUtil<T> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		if (data == null) {
			data = new HashMap<>();
		}
		setupRestUtil(util, cloud, data);
		return util.requestAllPages();
	}
	
	// Get objects from cloud
	// data contains implementation-specific data, can be null
	public List<T> getCloudObjects(Config config, Class<T> dataClass, Map<String, Object> data) 
			throws IOException, IllegalStateException, URISyntaxException {
		return getObjects(config, dataClass, data, true);
	}
	
	// Get objects from DC
	// data contains implementation-specific data, can be null
	public List<T> getServerObjects(Config config, Class<T> dataClass, Map<String, Object> data) 
			throws IOException, IllegalStateException, URISyntaxException {
		return getObjects(config, dataClass, data, false);
	}
}
