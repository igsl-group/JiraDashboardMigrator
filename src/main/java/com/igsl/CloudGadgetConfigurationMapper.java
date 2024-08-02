package com.igsl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.igsl.config.GadgetConfigMapping;
import com.igsl.config.GadgetType;
import com.igsl.model.DataCenterGadgetConfiguration;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.mapping.Mapping;

public class CloudGadgetConfigurationMapper {

	private static final Logger LOGGER = LogManager.getLogger(CloudGadgetConfigurationMapper.class);

	public static void mapConfiguration(DataCenterPortletConfiguration gadget, Mapping project, Mapping role,
			Mapping field, Mapping group, Mapping user, Mapping filter) {
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
			for (DataCenterGadgetConfiguration item : gadget.getGadgetConfigurations()) {
				GadgetConfigMapping conf = type.getConfig(item.getUserPrefKey());
				if (conf != null) {
					Mapping map = null;
					if (conf.getMappingType() != null) {
						switch (conf.getMappingType()) {
						case CUSTOM_FIELD:
							map = field;
							break;
						case FILTER:
							map = filter;
							break;
						case GROUP:
							map = group;
							break;
						case PROJECT:
							map = project;
							break;
						case ROLE:
							map = role;
							break;
						case USER:
							map = user;
							break;
						default:
							break;
						}
					}
					Pattern p = Pattern.compile(conf.getPattern());
					Matcher m = p.matcher(item.getUserPrefValue());
					// Find all matches
					String oldValue = item.getUserPrefValue();
					StringBuilder newValue = new StringBuilder();
					while (m.find()) {
						String o = m.group(conf.getTargetGroup());
						String s = o;
						if (map != null) {
							if (map.getMapped().containsKey(o)) {
								s = map.getMapped().get(o);
							} else {
								Log.warn(LOGGER, 
										"Gadget: " + gadget.getId() + " " + 
										"ModuleKey: " + gadget.getDashboardCompleteKey() + " " + 
										"Uri: " + gadget.getGadgetXml() + " " + 
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
							"Gadget: " + gadget.getId() + " " + 
							"ModuleKey: " + gadget.getDashboardCompleteKey() + " " + 
							"Uri: " + gadget.getGadgetXml() + " " + 
							"Key [" + oldKey + "] " + 
							"New Key [" + item.getUserPrefKey() + "] " + 
							"Value [" + oldValue + "] " + 
							"New Value [" + item.getUserPrefValue() + "] ");
				} else {
					Log.warn(LOGGER, 
								"Gadget: [" + gadget.getId() + "] " + 
								"ModuleKey: [" + gadget.getDashboardCompleteKey() + "] " + 
								"Uri: [" + gadget.getGadgetXml() + "] " + 
								"Key: [" + item.getUserPrefKey() + "] " + 
								"has no mapping configured");
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
