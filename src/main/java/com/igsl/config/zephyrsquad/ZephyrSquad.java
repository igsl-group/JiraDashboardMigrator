package com.igsl.config.zephyrsquad;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.atlassian.jira.project.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igsl.DashboardMigrator;
import com.igsl.Log;
import com.igsl.config.CustomGadgetConfigMapper;
import com.igsl.config.GadgetType;
import com.igsl.model.DataCenterGadgetConfiguration;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;
import com.igsl.model.mapping.ProjectVersion;
import com.igsl.model.mapping.Status;

/**
 * Velocity charts by Broken Build
 */
public class ZephyrSquad extends CustomGadgetConfigMapper {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final String CONF_KEY = "itemkey";
	private static final ObjectMapper OM = new ObjectMapper();
	private static final Pattern PATTERN = Pattern.compile("([0-9]+)");
	
	@Override
	public void process(DataCenterPortletConfiguration gadget, Map<MappingType, Mapping> mappings) 
			throws Exception {
		Log.info(LOGGER, "Before: " + OM.writeValueAsString(gadget.getGadgetConfigurations()));
		Map<String, String> configData = new HashMap<>();
		for (DataCenterGadgetConfiguration config : gadget.getGadgetConfigurations()) {
			configData.put(config.getUserPrefKey(), config.getUserPrefValue());
		}
		// statusNames -> statuses
		String statuses = configData.get("statusNames");
		if (statuses != null) {
			Matcher matcher = PATTERN.matcher(statuses);
			StringBuilder replacement = new StringBuilder();
			while (matcher.matches()) {
				String statusId = matcher.group(1);
				if (mappings.get(MappingType.STATUS).getMapped().containsKey(statusId)) {
					Status s = (Status) mappings.get(MappingType.STATUS).getMapped().get(statusId);
					String newStatusId = s.getId();
					matcher.appendReplacement(replacement, newStatusId);
				}
			}
			matcher.appendTail(replacement);
			configData.remove("statusNames");
			configData.put("statuses", replacement.toString());
		}
		// version -> version, versionName
		String version = configData.get("version");
		try {
			if (version != null) {
				String newVersion = version;
				String versionName = "";
				List<ProjectVersion> versionList = DashboardMigrator.readValuesFromFile(
						MappingType.PROJECT_VERSION.getDC(), ProjectVersion.class);
				if (mappings.get(MappingType.PROJECT_VERSION).getMapped().containsKey(version)) {
					ProjectVersion v = (ProjectVersion) 
							mappings.get(MappingType.PROJECT_VERSION).getMapped().get(version);
					newVersion = v.getId();
					// Find versionName
					for (ProjectVersion projectVersion : versionList) {
						if (version.equals(projectVersion.getId())) {
							versionName = projectVersion.getName();
							break;
						}
					}
				}
				configData.put("version", newVersion);
				configData.put("versionName", versionName);
			}
		} catch (Exception ex) {
			Log.error(LOGGER, "Failed to load project version list", ex);
		}
		// projectId -> projectKey, projectName, project
		String projectId = configData.get("projectId");
		try {
			if (projectId != null) {
				String projectKey = "";
				String projectName = "";
				String project = projectId;
				List<Project> projectList = DashboardMigrator.readValuesFromFile(
						MappingType.PROJECT.getDC(), Project.class);
				if (mappings.get(MappingType.PROJECT_VERSION).getMapped().containsKey(projectId)) {
					com.igsl.model.mapping.Project p = (com.igsl.model.mapping.Project)
							mappings.get(MappingType.PROJECT_VERSION).getMapped().get(projectId);
					project = p.getId();
					// Find versionName
					for (Project projectItem : projectList) {
						if (projectId.equals(projectItem.getId())) {
							projectName = projectItem.getName();
							projectKey = projectItem.getKey();
							break;
						}
					}
				}
				configData.put("projectKey", projectKey);
				configData.put("projectName", projectName);
				configData.put("project", project);
			}
		} catch (Exception ex) {
			Log.error(LOGGER, "Failed to load project list", ex);
		}
		
		// groupFld -> groupField
		String groupFld = configData.get("groupFld");
		if (groupFld != null) {
			configData.put("groupField", groupFld);
		}
		
		// Note: Test Progress uses config, but all others use itemkey
		DataCenterGadgetConfiguration newConfig = new DataCenterGadgetConfiguration();
		GadgetType gadgetType = GadgetType.parse(gadget.getDashboardCompleteKey(), gadget.getGadgetXml());
		newConfig.setUserPrefKey(gadgetType.getConfigType());
		newConfig.setUserPrefValue(OM.writeValueAsString(configData));
		gadget.getGadgetConfigurations().clear();
		gadget.getGadgetConfigurations().add(newConfig);
		Log.info(LOGGER, "After: " + OM.writeValueAsString(gadget.getGadgetConfigurations()));
	}

}
