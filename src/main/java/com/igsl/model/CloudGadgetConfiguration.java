package com.igsl.model;

import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class CloudGadgetConfiguration extends HashMap<String, String> {
	private static final long serialVersionUID = 1L;

	public static CloudGadgetConfiguration create(List<DataCenterGadgetConfiguration> list) {
		CloudGadgetConfiguration result = null;
		if (list != null) {
			result = new CloudGadgetConfiguration();
			for (DataCenterGadgetConfiguration item : list) {
				result.put(item.getUserPrefKey(), item.getUserPrefValue());
			}
		}
		return result;
	}
}
