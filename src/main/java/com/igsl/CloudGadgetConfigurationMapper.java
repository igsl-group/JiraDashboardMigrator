package com.igsl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.igsl.config.GadgetConfigCondition;
import com.igsl.config.GadgetConfigMapping;
import com.igsl.config.GadgetType;
import com.igsl.model.DataCenterGadgetConfiguration;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;

public class CloudGadgetConfigurationMapper {

	private static final Logger LOGGER = LogManager.getLogger(CloudGadgetConfigurationMapper.class);

	public static void mapConfiguration(DataCenterPortletConfiguration gadget, Map<MappingType, Mapping> mappings) {
		GadgetType type = GadgetType.parse(gadget.getDashboardCompleteKey(), gadget.getGadgetXml());
		if (type != null) {
			// Replace moduleKey and Uri if configured
			if (type.getNewModuleKey() == null) {
				gadget.setDashboardCompleteKey(null);
			} else if (type.getNewModuleKey().isBlank()) {
				// No change
			} else {
				gadget.setDashboardCompleteKey(type.getNewModuleKey());
			}
			if (type.getNewUri() == null) {
				gadget.setGadgetXml(null);
			} else if (type.getNewUri().isBlank()) {
				// No change
			} else {
				gadget.setGadgetXml(type.getNewUri());
			}
			// Organize gadget configurations into a map
			Map<String, String> gadgetMap = new HashMap<>();
			for (DataCenterGadgetConfiguration item : gadget.getGadgetConfigurations()) {
				if (gadgetMap.containsKey(item.getUserPrefKey())) {
					Log.error(LOGGER, "Duplicate gadget configuration found: " + item.getUserPrefKey());
				} else {
					gadgetMap.put(item.getUserPrefKey(), item.getUserPrefValue());
				}
			}
			// Process gadget configurations
			for (DataCenterGadgetConfiguration item : gadget.getGadgetConfigurations()) {
				List<GadgetConfigMapping> confList = type.getConfigs(item.getUserPrefKey());
				if (confList.size() == 0) {
					Log.warn(LOGGER, 
								"Gadget: [" + gadget.getId() + "] " + 
								"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
								"Uri: [" + gadget.getGadgetXml() + "] " + 
								"Key: [" + item.getUserPrefKey() + "] " + 
								"has no mapping configured");
				} else {
					for (GadgetConfigMapping conf : confList) {
						// Check conditions
						if (conf.getConditions() != null) {
							boolean conditionMatch = true;
							GadgetConfigCondition failCondition = null;
							for (GadgetConfigCondition cond : conf.getConditions()) {
								String attrName = cond.getAttributeName();
								String actualValue = gadgetMap.get(attrName);
								List<String> targetValues = cond.getAttributeValue();
								switch (cond.getCondition()) {
								case EQU:
									conditionMatch &= (actualValue.compareTo(targetValues.get(0)) == 0);
									break;
								case GTE:
									conditionMatch &= (actualValue.compareTo(targetValues.get(0)) != -1);
									break;
								case GTR:
									conditionMatch &= (actualValue.compareTo(targetValues.get(0)) == 1);
									break;
								case IN:
									conditionMatch &= (targetValues.contains(actualValue));
									break;
								case LTE:
									conditionMatch &= (actualValue.compareTo(targetValues.get(0)) != 1);
									break;
								case LTR:
									conditionMatch &= (actualValue.compareTo(targetValues.get(0)) == -1);
									break;
								case NEQ:
									conditionMatch &= (actualValue.compareTo(targetValues.get(0)) != 0);
									break;
								}
								if (!conditionMatch) {
									failCondition = cond;
									break;
								}
							}
							if (!conditionMatch) {
								Log.debug(LOGGER, 
										"Gadget: [" + gadget.getId() + "] " + 
										"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
										"Uri: [" + gadget.getGadgetXml() + "] " + 
										"Key [" + item.getUserPrefKey() + "] " + 
										"Value [" + item.getUserPrefValue() + "] " + 
										"Condition [ " + failCondition + "] "+ 
										"Condition does not match");
								continue;
							}
						}
						// Get mapping type, if any
						Mapping map = null;
						if (conf.getMappingType() != null) {
							map = mappings.get(conf.getMappingType());
						}
						// Find value matches
						String oldValue = item.getUserPrefValue();
						Pattern p = Pattern.compile(conf.getPattern());
						Matcher m = p.matcher(item.getUserPrefValue());
						StringBuilder newValue = new StringBuilder();
						while (m.find()) {
							String o = m.group(conf.getTargetGroup());
							String s = o;
							if (map != null) {
								if (map.getMapped().containsKey(o)) {
									s = map.getMapped().get(o);
								} else {
									Log.warn(LOGGER, 
											"Gadget: [" + gadget.getId() + "] " + 
											"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
											"Uri: [" + gadget.getGadgetXml() + "] " + 
											"has no mapping for key [" + item.getUserPrefKey() + "] " + 
											"value [" + item.getUserPrefValue() + "]");
								}
							}
							if (conf.getDelimiter() != null) {
								s = conf.getDelimiter() + s;
							}
							String replacementConf = conf.getReplacement();
							String replacement = replacementConf
									.replaceAll("(?<!\\\\)\\$" + conf.getTargetGroup(), s);
							m.appendReplacement(newValue, replacement);
						}
						m.appendTail(newValue);
						// Replace attribute name if configured
						String oldKey = item.getUserPrefKey();
						if (conf.getNewAttributeNameRegex() != null) {
							item.setUserPrefKey(conf.getNewAttributeName(item.getUserPrefKey()));
						}
						// Replace attribute value
						if (newValue.length() != 0) {
							if (conf.getDelimiter() != null && 
								newValue.toString().startsWith(conf.getDelimiter())) {
								newValue.delete(0, conf.getDelimiter().length());
							}
							String newValueString = 
									(conf.getPrefix() != null? conf.getPrefix() : "") + 
									newValue.toString() + 
									(conf.getSuffix() != null? conf.getSuffix() : "");
							item.setUserPrefValue(newValueString);
						}
						Log.info(LOGGER, 
								"Gadget: [" + gadget.getId() + "] " + 
								"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
								"Uri: [" + gadget.getGadgetXml() + "] " + 
								"Key [" + oldKey + "] " + 
								"New Key [" + item.getUserPrefKey() + "] " + 
								"Value [" + oldValue + "] " + 
								"New Value [" + item.getUserPrefValue() + "] ");
					} 
				}
			}
		} else {
			Log.warn(	LOGGER, 
						"Gadget: [" + gadget.getId() + "] " + 
						"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
						"Uri: [" + gadget.getGadgetXml() + "] " + 
						"is not configured");
		}
	}
}
