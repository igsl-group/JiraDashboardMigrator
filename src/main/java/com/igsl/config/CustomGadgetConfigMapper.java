package com.igsl.config;

import java.util.Map;

import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;

/**
 * For gadgets with configuration too complex to use the engine
 */
public abstract class CustomGadgetConfigMapper {

	/**
	 * Process gadget configuration
	 * @param gadget Call .getGadgetConfigurations() to get each configuration item and modify them
	 * @param mappings Object mappings between Server and Cloud
	 */
	public abstract void process(
			DataCenterPortletConfiguration gadget, Map<MappingType, Mapping> mappings) throws Exception;
}
