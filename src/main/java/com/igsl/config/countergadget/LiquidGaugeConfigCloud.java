package com.igsl.config.countergadget;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.igsl.model.mapping.Filter;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;

@JsonPropertyOrder(alphabetic = true)
public class LiquidGaugeConfigCloud {
	
	private Map<String, String> values = new HashMap<>();
	private static final Pattern FILTER_PATTERN = Pattern.compile("filter-([0-9]+)");
	
	private static final String CHANGED_SOURCE_CONFIG = "filterId";
	private static final String CHANGED_TARGET_CONFIG = "valueFilterId";
	
	private static final String MAX_FILTER_ID = "maxFilterId";
	private static final String FILTER_FOR_MAX = "filterForMax";

	public static LiquidGaugeConfigCloud create(
			LiquidGaugeConfigDC config, Map<MappingType, Mapping> mappings) {
		LiquidGaugeConfigCloud result = new LiquidGaugeConfigCloud();
		for (Map.Entry<String, String> entry : config.getValues().entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (CHANGED_SOURCE_CONFIG.equals(key)) {
				Matcher m = FILTER_PATTERN.matcher(value);
				if (m.matches()) {
					String filterId = m.group(1);
					if (mappings.get(MappingType.FILTER).getMapped().containsKey(filterId)) {
						Filter f = (Filter)
								mappings.get(MappingType.FILTER).getMapped().get(filterId);
						filterId = f.getId();
					}
					result.values.put(CHANGED_TARGET_CONFIG, filterId);
				} else {
					result.values.put(CHANGED_TARGET_CONFIG, value);
				}
			} else {
				result.values.put(key, value);
			}
		}
		// Check filterForMax and maxFilterId, Counter Gadget seems to mind
		if (result.values.get(MAX_FILTER_ID) == null || 
			result.values.get(MAX_FILTER_ID).isBlank()) {
			// Make sure FILTER_FOR_MAX is No
			result.values.put(FILTER_FOR_MAX, "No");
		}
		return result;
	}

	public Map<String, String> getValues() {
		return values;
	}

	public void setValues(Map<String, String> values) {
		this.values = values;
	}
}
