package com.igsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.igsl.config.GadgetConfigAddition;
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
			Map<String, String> configMap = new HashMap<>();
			for (DataCenterGadgetConfiguration item : gadget.getGadgetConfigurations()) {
				if (configMap.containsKey(item.getUserPrefKey())) {
					Log.error(LOGGER, "Duplicate gadget configuration found: " + item.getUserPrefKey());
				} else {
					configMap.put(item.getUserPrefKey(), item.getUserPrefValue());
				}
			}
			// Clone configuration list
			Map<String, DataCenterGadgetConfiguration> clonedMap = new HashMap<>();
			Map<String, DataCenterGadgetConfiguration> newMap = new HashMap<>();
			for (DataCenterGadgetConfiguration item : gadget.getGadgetConfigurations()) {
				DataCenterGadgetConfiguration clonedItem = new DataCenterGadgetConfiguration();
				clonedItem.setId(item.getId());
				clonedItem.setUserPrefKey(item.getUserPrefKey());
				clonedItem.setUserPrefValue(item.getUserPrefValue());
				clonedMap.put(item.getUserPrefKey(), clonedItem);
			}
			// Process gadget configurations
			for (DataCenterGadgetConfiguration item : clonedMap.values()) {
				List<GadgetConfigMapping> confList = type.getConfigs(item.getUserPrefKey());
				if (confList.size() == 0) {
					Log.warn(LOGGER, 
								"Gadget [" + gadget.getId() + "] " + 
								"ModuleKey [" + gadget.getDashboardCompleteKey() + "] " + 
								"Uri [" + gadget.getGadgetXml() + "] " + 
								"Key [" + item.getUserPrefKey() + "] " + 
								"has no mapping configured");
				} else {
					for (GadgetConfigMapping conf : confList) {
						// Check conditions
						if (conf.getConditions() != null) {
							boolean conditionMatch = true;
							GadgetConfigCondition failCondition = null;
							for (GadgetConfigCondition cond : conf.getConditions()) {
								String attrName = cond.getAttributeName();
								String actualValue = configMap.get(attrName);
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
								case CONTAIN:
									conditionMatch &= (actualValue.contains(targetValues.get(0)));
									break;
								case START_WITH:
									conditionMatch &= (actualValue.startsWith(targetValues.get(0)));
									break;
								case END_WITH:
									conditionMatch &= (actualValue.endsWith(targetValues.get(0)));
									break;
								}
								if (!conditionMatch) {
									failCondition = cond;
									break;
								}
							}
							if (!conditionMatch) {
								Log.debug(LOGGER, 
										"Gadget [" + gadget.getId() + "] " + 
										"ModuleKey [" + gadget.getDashboardCompleteKey() + "] " + 
										"Uri [" + gadget.getGadgetXml() + "] " + 
										"Key [" + item.getUserPrefKey() + "] " + 
										"Value [" + item.getUserPrefValue() + "] " + 
										"Config [" + conf.getAttributeNameRegex() + "] " + 
										"Condition [ " + failCondition + "] " + 
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
						int targetGroup = conf.getTargetGroup();
						String oldValue = item.getUserPrefValue();
						Pattern pattern = Pattern.compile(conf.getPattern());
						Matcher matcher = pattern.matcher(item.getUserPrefValue());
						StringBuilder newValueBuffer = new StringBuilder();
						while (matcher.find()) {
							// Store all groups
							Map<Integer, String> groupValues = new HashMap<>();
							for (int i = 0; i <= matcher.groupCount(); i++) {
								groupValues.put(i, matcher.group(i));
							}
							String newValue = groupValues.get(targetGroup);
							if (map != null) {
								if (map.getMapped().containsKey(newValue)) {
									newValue = map.getMapped().get(newValue);
									// Update map
									groupValues.put(conf.getTargetGroup(), newValue);
								} else {
									Log.warn(LOGGER, 
											"Gadget: [" + gadget.getId() + "] " + 
											"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
											"Uri: [" + gadget.getGadgetXml() + "] " + 
											"has no mapping for key [" + item.getUserPrefKey() + "] " + 
											"value [" + newValue + "]");
								}
							}
							String replacementConf = conf.getReplacement();
							String replacement = replacementConf
									.replaceAll("(?<!\\\\)\\$" + targetGroup, newValue);
							matcher.appendReplacement(newValueBuffer, replacement);
							// Process additions
							if (conf.getAdditions() != null) {
								for (GadgetConfigAddition addition : conf.getAdditions()) {
									DataCenterGadgetConfiguration newItem = null;
									if (newMap.containsKey(addition.getAttributeName())) {
										newItem = newMap.get(addition.getAttributeName());
									} else {
										newItem = new DataCenterGadgetConfiguration();
										newItem.setUserPrefKey(addition.getAttributeName());
										newItem.setUserPrefValue("");
									}
									// Calculate replacement
									String replacementConfig = addition.getReplacement();
									Pattern replacementPattern = Pattern.compile("(?<!\\\\)\\$([0-9]+)");
									Matcher replacementMatcher = replacementPattern.matcher(replacementConfig);
									StringBuilder replacementValue = new StringBuilder();
									while (replacementMatcher.find()) {
										int groupIndex = Integer.parseInt(replacementMatcher.group(1));
										replacementMatcher.appendReplacement(replacementValue, 
												groupValues.get(groupIndex));
									}
									replacementMatcher.appendTail(replacementValue);
									switch (addition.getMode()) {
									case APPEND:
										if (newItem.getUserPrefValue() != null && 
											!newItem.getUserPrefValue().isEmpty()) {
											newItem.setUserPrefValue(
													newItem.getUserPrefValue() + 											
													((addition.getDelimiter() != null)? addition.getDelimiter() : "") + 
													replacementValue.toString());
											break;
										}
										// Fall-through
									case REPLACE:
										newItem.setUserPrefValue(replacementValue.toString());
										break;
									}
									newMap.put(newItem.getUserPrefKey(), newItem);
								}	// For each addition
							}
						}
						// Add prefix/suffix to new additions
						if (conf.getAdditions() != null) {
							for (GadgetConfigAddition addition : conf.getAdditions()) {
								if (newMap.containsKey(addition.getAttributeName())) {
									DataCenterGadgetConfiguration newItem = newMap.get(addition.getAttributeName());
									newItem.setUserPrefValue(
											((addition.getPrefix() != null)? addition.getPrefix() : "") + 
											newItem.getUserPrefValue() + 
											((addition.getSuffix() != null)? addition.getSuffix() : ""));
									Log.info(LOGGER, 
											"Gadget: [" + gadget.getId() + "] " + 
											"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
											"Uri: [" + gadget.getGadgetXml() + "] " + 
											"Added Key [" + newItem.getUserPrefKey() + "] " + 
											"Added Value [" + newItem.getUserPrefValue() + "] ");
								}
							}
						}
						// Update main attribute value
						matcher.appendTail(newValueBuffer);
						String newValueString = oldValue;
						if (newValueBuffer.length() != 0) {
							newValueString = 
									(conf.getPrefix() != null? conf.getPrefix() : "") + 
									newValueBuffer.toString() + 
									(conf.getSuffix() != null? conf.getSuffix() : "");
							item.setUserPrefValue(newValueString);
						}
						Log.info(LOGGER, 
								"Gadget: [" + gadget.getId() + "] " + 
								"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
								"Uri: [" + gadget.getGadgetXml() + "] " + 
								"Key [" + item.getUserPrefKey() + "] " + 
								"Value [" + oldValue + "] " + 
								"New Value [" + newValueString + "] ");
					} 
				}
			}
			// Set new configuration list
			clonedMap.putAll(newMap);
			List<DataCenterGadgetConfiguration> list = new ArrayList<>();
			for (DataCenterGadgetConfiguration c : clonedMap.values()) {
				list.add(c);
			}
			list.sort(new GadgetConfigurationComparator());
			gadget.setGadgetConfigurations(list);
		} else {
			Log.warn(	LOGGER, 
						"Gadget: [" + gadget.getId() + "] " + 
						"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
						"Uri: [" + gadget.getGadgetXml() + "] " + 
						"is not configured");
		}
	}
}
