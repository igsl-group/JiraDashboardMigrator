package com.igsl.config.countergadget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.igsl.model.DataCenterGadgetConfiguration;

@JsonPropertyOrder(alphabetic = true)
public class LiquidGaugeConfigDC {
	
	private Map<String, String> values = new HashMap<>();
	
	public static LiquidGaugeConfigDC parse(List<DataCenterGadgetConfiguration> configList) {
		LiquidGaugeConfigDC result = new LiquidGaugeConfigDC();
		for (DataCenterGadgetConfiguration config : configList) {
			String key = config.getUserPrefKey();
			String value = config.getUserPrefValue();
			result.values.put(key, value);
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
