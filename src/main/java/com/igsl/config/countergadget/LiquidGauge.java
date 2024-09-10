package com.igsl.config.countergadget;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igsl.Log;
import com.igsl.config.CustomGadgetConfigMapper;
import com.igsl.model.DataCenterGadgetConfiguration;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;

/**
 * Velocity charts by Broken Build
 */
public class LiquidGauge extends CustomGadgetConfigMapper {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final String CONF_KEY = "itemkey";
	private static final ObjectMapper OM = new ObjectMapper();
	
	@Override
	public void process(DataCenterPortletConfiguration gadget, Map<MappingType, Mapping> mappings) 
			throws Exception {
		LiquidGaugeConfigDC dc = LiquidGaugeConfigDC.parse(gadget.getGadgetConfigurations());
		Log.info(LOGGER, "Before: " + OM.writeValueAsString(dc));
		LiquidGaugeConfigCloud cloud = LiquidGaugeConfigCloud.create(dc, mappings);
		Log.info(LOGGER, "After: " + OM.writeValueAsString(cloud));
		DataCenterGadgetConfiguration config = new DataCenterGadgetConfiguration();
		config.setUserPrefKey(CONF_KEY);
		config.setUserPrefValue(OM.writeValueAsString(cloud.getValues()));
		gadget.getGadgetConfigurations().clear();
		gadget.getGadgetConfigurations().add(config);
	}

}
