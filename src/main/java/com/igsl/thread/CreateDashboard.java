package com.igsl.thread;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.igsl.CloudGadgetConfigurationMapper;
import com.igsl.DashboardMigrator;
import com.igsl.GadgetOrderComparator;
import com.igsl.Log;
import com.igsl.config.Config;
import com.igsl.config.GadgetType;
import com.igsl.model.CloudDashboard;
import com.igsl.model.CloudDashboardMixin;
import com.igsl.model.CloudGadget;
import com.igsl.model.CloudGadgetConfiguration;
import com.igsl.model.CloudGadgetMixin;
import com.igsl.model.CloudPermission;
import com.igsl.model.DataCenterPortalPage;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.PermissionType;
import com.igsl.model.mapping.Group;
import com.igsl.model.mapping.JiraObject;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;
import com.igsl.model.mapping.Project;
import com.igsl.model.mapping.Role;
import com.igsl.model.mapping.User;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class CreateDashboard implements Callable<CreateDashboardResult> {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final ObjectMapper OM = new ObjectMapper()
			// Indent
			.enable(SerializationFeature.INDENT_OUTPUT)
			// Allow comments
			.configure(Feature.ALLOW_COMMENTS, true)	
			// Allow attributes missing in POJO
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	
	private static final String SUCCESS = "Success";
	private static final String SPACER_GADGET_MODULE_KEY = 
			"rest/gadgets/1.0/g/com.atlassian.jira.atlassian-wallboard-plugin:spacer-gadget/gadgets/spacerGadget.xml";
	
	private Config config;
	private DataCenterPortalPage dashboard;
	private String accountId;
	private Path originalDir;
	private Path newDir;
	private Map<MappingType, Mapping> mappings;
	private Map<MappingType, List<JiraObject<?>>> data;
	
	public CreateDashboard(Config config, String accountId, 
			Map<MappingType, Mapping> mappings, 
			Map<MappingType, List<JiraObject<?>>> data, 
			DataCenterPortalPage dashboard, Path originalDir, Path newDir) {
		this.config = config;
		this.accountId = accountId;
		this.mappings = mappings;
		this.data = data;
		this.dashboard = dashboard;
		this.originalDir = originalDir;
		this.newDir = newDir;
	}
	
	private String getGadgetIdentifier(DataCenterPortletConfiguration portlet) {
		StringBuilder sb = new StringBuilder();
		if (portlet != null) {
			sb	.append("(")
				.append(portlet.getPositionSeq())
				.append(",")
				.append(portlet.getColumnNumber())
				.append(")[")
				.append(portlet.getDashboardCompleteKey())
				.append("][")
				.append(portlet.getGadgetXml())
				.append("]");
		}
		return sb.toString();
	}
	
	private String getGadgetIdentifier(CloudGadget cg) {
		StringBuilder sb = new StringBuilder();
		if (cg != null) {
			sb	.append("(")
				.append(cg.getPosition().getRow())
				.append(",")
				.append(cg.getPosition().getColumn())
				.append(")[")
				.append(cg.getModuleKey())
				.append("][")
				.append(cg.getUri())
				.append("]");
		}
		return sb.toString();
	}
	
	private boolean checkStatusCode(Response resp, Status... successCodes) {
		if (successCodes != null) {
			int[] list = new int[successCodes.length];
			for (int i = 0; i < successCodes.length; i++) {
				list[i] = successCodes[i].getStatusCode();
			}
			return checkStatusCode(resp, list);
		}
		return false;
	}
	
	private boolean checkStatusCode(Response resp, int... successCodes) {
		if (successCodes != null) {
			for (int code : successCodes) {
				if ((resp.getStatus() & code) == code) {
					return true;
				}
			}
		}
		return false;
	}
	
	private CloudPermission mapPermission(
			CloudDashboard dashboard, 
			CloudPermission permission, 
			Map<MappingType, Mapping> mappings) throws Exception {
		CloudPermission result = permission.clone();
		PermissionType permissionType = PermissionType.parse(permission.getType());
		switch (permissionType) {
		case GLOBAL: // Fall-thru
		case LOGGED_IN:	// Fall-thru
			// Add permission unchanged
			break;
		case USER_UNKNOWN:
			Log.warn(LOGGER, "Dashboard [" + dashboard.getName() + "] " + 
					"share permission to inaccessible user [" + OM.writeValueAsString(permission) + "] " + 
					"is excluded");
			break;
		case PROJECT_UNKNOWN:	
			// This happens when you share to a project you cannot access. 
			// This type of permissions cannot be created via REST.
			// So this is excluded.
			Log.warn(LOGGER, "Dashboard [" + dashboard.getName() + "] " + 
					"share permission to inaccessible objects [" + OM.writeValueAsString(permission) + "] " + 
					"is excluded");
			break;
		case GROUP:
			if (mappings.get(MappingType.GROUP).getMapped().containsKey(permission.getGroup().getName())) {
				Group grp = (Group) mappings.get(MappingType.GROUP).getMapped()
						.get(permission.getGroup().getName());
				String newId = grp.getName();
				result.getGroup().setName(newId);
			} else {
				String msg = "Dashboard [" + dashboard.getName() + "] " + 
						"is shared to unmapped group [" + permission.getGroup().getName() + "] " + 
						"This share is excluded";
				Log.warn(LOGGER, msg);
			}
			break;
		case PROJECT:	// Fall-through
		case PROJECT_ROLE: 
			if (permission.getRole() == null) {
				// No role, add as project
				if (mappings.get(MappingType.PROJECT).getMapped().containsKey(permission.getProject().getId())) {
					Project p = (Project) mappings.get(MappingType.PROJECT).getMapped()
							.get(permission.getProject().getId());
					String newId = p.getId();
					result.getProject().setId(newId);
				} else {
					String msg = "Dashboard [" + dashboard.getName() + "] " + 
							"is shared to unmapped project [" + permission.getProject().getId() + "] " + 
							"This share is excluded";
					Log.warn(LOGGER, msg);
				}
			} else if (permission.getRole() != null) {
				// Has role, add as project-role
				result.setType(PermissionType.PROJECT_ROLE.toString());
				if (mappings.get(MappingType.PROJECT).getMapped().containsKey(permission.getProject().getId())) {
					Project r = (Project) mappings.get(MappingType.PROJECT).getMapped()
							.get(permission.getProject().getId());
					String newId = r.getId();
					result.getProject().setId(newId);
				} else {
					String msg = "Dashboard [" + dashboard.getName() + "] " + 
							"is shared to unmapped project [" + permission.getProject().getId() + "] " + 
							"This share is excluded";
					Log.warn(LOGGER, msg);
				}
				if (mappings.get(MappingType.ROLE).getMapped().containsKey(permission.getRole().getId())) {
					Role r = (Role) mappings.get(MappingType.ROLE).getMapped()
							.get(permission.getRole().getId());
					String newId = r.getId();
					result.getRole().setId(newId);
				} else {
					String msg = "Dashboard [" + dashboard.getName() + "] " + 
							"is shared to unmapped role [" + permission.getRole().getId() + "] " + 
							"This share is excluded";
					Log.warn(LOGGER, msg);
				}
			}
			break;
		case USER:
			if (mappings.get(MappingType.USER).getMapped().containsKey(permission.getUser().getAccountId())) {
				User u = (User) mappings.get(MappingType.USER).getMapped()
						.get(permission.getUser().getAccountId());
				String newId = u.getAccountId();
				result.getUser().setAccountId(newId);
			} else {
				String msg = "Dashboard [" + dashboard.getName() + "] " + 
						"is shared to unmapped user [" + permission.getUser().getAccountId() + "] " + 
						"This share is excluded";
				Log.warn(LOGGER, msg);
			}
			break;
		}
		return result;
	}
	
	/**
	 * @return Empty string if no error, otherwise the error message
	 */
	@Override
	public CreateDashboardResult call() throws Exception {
		Map<Class<?>, Class<?>> mixinMap = new HashMap<>();
		mixinMap.put(CloudDashboard.class, CloudDashboardMixin.class);
		mixinMap.put(CloudGadget.class, CloudGadgetMixin.class);
		CreateDashboardResult result = new CreateDashboardResult();
		result.setOriginalDashboard(dashboard);
		// Sort gadget by position
		dashboard.getPortlets().sort(new GadgetOrderComparator(true));
		// Save original to file
		CloudDashboard original = CloudDashboard.create(dashboard, true);
		DashboardMigrator.saveFile(
				originalDir.resolve(dashboard.getPageName() + "." + dashboard.getId() + ".json").toString(), 
				original,
				mixinMap);
		RestUtil<CloudDashboard> util = RestUtil.getInstance(CloudDashboard.class)
				.config(config, true);
		// Create dashboard under current user
		CloudDashboard cd = CloudDashboard.create(dashboard, false);
		// Map owner
		if (mappings.get(MappingType.USER).getMapped().containsKey(dashboard.getUsername())) {
			User user = (User) mappings.get(MappingType.USER).getMapped().get(dashboard.getUsername());
			String newAccountId = user.getAccountId();
			cd.setAccountId(newAccountId);
			Log.info(LOGGER, 
					"Dashboard [" + dashboard.getPageName() + "] " + 
					"Owner [" + dashboard.getUsername() + "] => " + 
					"[" + newAccountId + "]");
		} else {
			// Owner not mapped
			String msg = "Dashboard [" + dashboard.getPageName() + "] " + 
					"owned by user [" + dashboard.getUsername() + "] " + 
					"not found in Cloud";
			Log.error(LOGGER, msg);
			result.setCreateDashboardResult(msg);
			return result;
		}
		// Map permission targets
		List<CloudPermission> newEditPermissions = new ArrayList<>();
		for (CloudPermission editPermission : cd.getEditPermissions()) {
			newEditPermissions.add(mapPermission(cd, editPermission, mappings));
		}
		cd.setEditPermissions(newEditPermissions);
		List<CloudPermission> newSharePermissions = new ArrayList<>();
		for (CloudPermission sharePermission : cd.getSharePermissions()) {
			newSharePermissions.add(mapPermission(cd, sharePermission, mappings));
		}
		cd.setSharePermissions(newSharePermissions);
		Log.info(LOGGER, "CloudDashboard: " + OM.writeValueAsString(cd));
		// Check if exists for current user, if it does, delete and recreate
		List<CloudDashboard> list = util
				.path("/rest/api/latest/dashboard/search")
				.query("dashboardName", "\"" + dashboard.getPageName() + "\"")
				.query("accountId", accountId)
				.method(HttpMethod.GET)
				.pagination(new Paged<CloudDashboard>(CloudDashboard.class).maxResults(1))
				.requestNextPage();
		if (list.size() != 0) {
			// Delete dashboard
			Response deleteResp = util
					.path("/rest/api/latest/dashboard/{boardId}")
					.pathTemplate("boardId", list.get(0).getId()) 
					.method(HttpMethod.DELETE)
					.status()
					.request();
			if (!checkStatusCode(deleteResp, Status.NO_CONTENT)) {
				String msg = "Unable to delete dashboard [" + dashboard.getPageName() + "] to recreate it: " + 
						deleteResp.readEntity(String.class);
				Log.error(LOGGER, msg);
				result.setDeleteDashboardResult(msg);
				return result;
			} else {
				result.setDeleteDashboardResult(SUCCESS);
			}
		}
		Log.info(LOGGER, "Creating dashboard [" + dashboard.getPageName() + "]");
		// Create dashboard
		Response createResp = util
				.path("/rest/api/latest/dashboard")
				.query("extendAdminPermissions", true)
				.method(HttpMethod.POST)
				.payload(cd)
				.status()
				.request();
		if (!checkStatusCode(createResp, Status.OK)) {
			String msg = "Failed to create dashboard [" + dashboard.getPageName() + "]: " + 
					createResp.readEntity(String.class);
			Log.error(LOGGER, msg);
			result.setCreateDashboardResult(msg);
			return result;
		}
		CloudDashboard createdDashboard = (CloudDashboard) createResp.readEntity(CloudDashboard.class);
		result.setCreateDashboardResult(SUCCESS);
		result.setCreatedDashboard(createdDashboard);
		// Save dashboard (without gadgets)
		DashboardMigrator.saveFile(
				newDir.resolve(dashboard.getPageName() + "." + dashboard.getId() + ".json").toString(), 
				cd,
				mixinMap);
		// It is impossible to change layout via REST, WTF Atlassian		
		// Dashboard layout can only have 1 to 3 columns. Default is 2 columns.
		// Overflow to next row if layout has 3 columns.
		int maxCol = 1;
		int rowOffset = 0;
		for (DataCenterPortletConfiguration gadget : dashboard.getPortlets()) {
			CreateGadgetResult gadgetResult = new CreateGadgetResult();
			gadgetResult.setOriginalGadget(gadget);
			// Map gadget
			DataCenterPortletConfiguration clone = gadget.clone();
			try {
				CloudGadgetConfigurationMapper.mapConfiguration(clone, mappings);
			} catch (Exception ex) {
				String msg = "Failed to map dashboard [" + dashboard.getPageName() + "] " + 
							"gadget [" + getGadgetIdentifier(clone) + "]: " + ex.getMessage();
				Log.error(LOGGER, msg, ex);
				gadgetResult.setCreateResult(msg);
				result.getCreateGadgetResults().put(getGadgetIdentifier(clone), gadgetResult);
				continue;
			}
			// Add gadgets
			CloudGadget cg = CloudGadget.create(clone, false);
			int originalCol = cg.getPosition().getColumn();
			int originalRow = cg.getPosition().getRow();
			if (originalCol > maxCol) {
				// Overflow to next row as 2nd column
				rowOffset++;
				cg.getPosition().setColumn(1);
			}
			// Add rowOffset
			cg.getPosition().setRow(originalRow + rowOffset);
			// Record gadget in created dashboard
			cd.getGadgets().add(cg);
			// Add gadget
			Log.info(LOGGER, "DC Gadget: " + OM.writeValueAsString(gadget));
			Log.info(LOGGER, "Cloud Gadget: " + OM.writeValueAsString(cg));
			Response gadgetResp = util.path("/rest/api/latest/dashboard/{boardId}/gadget")
					.pathTemplate("boardId", createdDashboard.getId())
					.method(HttpMethod.POST)
					.payload(cg)
					.status()
					.request();
			if (!checkStatusCode(gadgetResp, Status.OK)) {
				String msg = "Failed to add gadget [" + getGadgetIdentifier(cg) + "] to dashboard [" + 
							dashboard.getPageName() + "]: " + gadgetResp.readEntity(String.class);
				Log.warn(LOGGER, msg);
				gadgetResult.setCreateResult(msg);
				// Add empty space instead
				CloudGadget cgSpacer = cg.clone();
				cgSpacer.setModuleKey(SPACER_GADGET_MODULE_KEY);
				cgSpacer.setUri(null);
				Response spacerResp = util.path("/rest/api/latest/dashboard/{boardId}/gadget")
						.pathTemplate("boardId", createdDashboard.getId())
						.method(HttpMethod.POST)
						.payload(cgSpacer)
						.status()
						.request();
				if (checkStatusCode(spacerResp, Status.OK)) {
					msg = "Spacer gadget added in place of [" + getGadgetIdentifier(cg) + "] " + 
							"to dashboard [" + dashboard.getPageName() + "]";
					Log.info(LOGGER, msg);
					msg = gadgetResult.getCreateResult() + "; " + msg;
					gadgetResult.setCreateResult(msg);
				} else {
					msg = "Failed to add spacer gadget in place of [" + getGadgetIdentifier(cg) + "] " + 
							"to dashboard [" + dashboard.getPageName() + "]: " + 
							spacerResp.readEntity(String.class);
					Log.error(LOGGER, msg);
					msg = gadgetResult.getCreateResult() + "; " + msg;
					gadgetResult.setCreateResult(msg);
				}
			} else {
				// Configure gadget
				CloudGadget createdGadget = gadgetResp.readEntity(CloudGadget.class);
				gadgetResult.setCreatedGadget(createdGadget);
				gadgetResult.setCreateResult(SUCCESS);
				// Gadget configuration
				CloudGadgetConfiguration cc = CloudGadgetConfiguration.create(
						clone.getGadgetConfigurations());
				Response configResp;
				GadgetType gadgetType = GadgetType.parse(
						clone.getDashboardCompleteKey(), 
						clone.getGadgetXml());
				if (gadgetType != null) {
					String configType = gadgetType.getConfigType();
					if (configType != null) {
						cg.setConfigurations(new TreeMap<>());
						cg.getConfigurations().put(configType, cc);						
						// Add all properties under propertyKey as JSON
						configResp = util
							.path("/rest/api/latest/dashboard/{boardId}/items/{gadgetId}/properties/{key}")
							.pathTemplate("boardId", createdDashboard.getId())
							.pathTemplate("gadgetId", createdGadget.getId())
							.pathTemplate("key", configType)
							.method(HttpMethod.PUT)
							.payload(cc)
							.status()
							.request();
						if (!checkStatusCode(configResp, Response.Status.OK)) {
							String msg = "Failed to config for gadget [" + getGadgetIdentifier(cg) + "] " + 
									"in dashboard [" + dashboard.getPageName() + "]: " + 
									configResp.readEntity(String.class);
							Log.error(LOGGER, msg);
							gadgetResult.getConfigurationResult().put(configType, msg);
						} else {
							gadgetResult.getConfigurationResult().put(configType, SUCCESS);
						}
					} else {
						cg.setConfigurations(new TreeMap<>());
						// Add property one by one
						for (Map.Entry<String, String> entry : cc.entrySet()) {
							Log.info(LOGGER, "Config: [" + entry.getKey() + "] = [" + entry.getValue() + "]");
							if (entry.getValue() != null && 
								!entry.getValue().isBlank()) {
								// Parse value as JSON first
								try {
									JsonNode json = OM.readTree(entry.getValue());
									util.payload(json);
									cg.getConfigurations().put(entry.getKey(), json);	
								} catch (Exception ex) {
									// If failed, treat as string
									String s = OM.writeValueAsString(entry.getValue());
									util.payload(s);
									cg.getConfigurations().put(entry.getKey(), s);
								}
								configResp = util
										.path("/rest/api/latest/dashboard/{boardId}/items/{gadgetId}/properties/{key}")
										.pathTemplate("boardId", createdDashboard.getId())
										.pathTemplate("gadgetId", createdGadget.getId())
										.pathTemplate("key", entry.getKey())
										.method(HttpMethod.PUT)
										.status()
										.request();
								if (!checkStatusCode(configResp, Response.Status.OK)) {
									String msg = "Failed to config for gadget [" + getGadgetIdentifier(cg) + "] " + 
											"in dashboard [" + dashboard.getPageName() + "]: " + 
											configResp.readEntity(String.class);
									Log.error(LOGGER, msg);
									gadgetResult.getConfigurationResult().put(entry.getKey(), msg);
								} else {
									gadgetResult.getConfigurationResult().put(entry.getKey(), SUCCESS);
								}
							}
						}	// For each property
					}
				} else {
					String msg = "Unrecognized gadget [" + getGadgetIdentifier(cg) + "] " + 
							"in dashboard [" + dashboard.getPageName() + "]";
					Log.warn(LOGGER, msg);
					gadgetResult.setCreateResult(msg);
				}
			}
			result.getCreateGadgetResults().put(getGadgetIdentifier(cg), gadgetResult);
		}	// For all gadgets
		// Change owner. If already exists, it gets renamed automatically.
		Map<String, Object> payload = new HashMap<>();
		payload.put("action", "changeOwner");
		payload.put("entityIds", new String[] {createdDashboard.getId()});
		Map<String, Object> details = new HashMap<>();
		details.put("newOwner", cd.getAccountId());
		details.put("autofixName", true); // API will append timestamp if a dashboard of same name already exist
		payload.put("changeOwnerDetails", details);
		Response changeOwnerResp = util.path("/rest/api/latest/dashboard/bulk/edit")
			.method(HttpMethod.PUT)
			.payload(payload)
			.status()
			.request();
		if (checkStatusCode(changeOwnerResp, Status.OK)) {
			Log.info(LOGGER, "Board [" + createdDashboard.getName() + "](" + createdDashboard.getId() + ") " + 
					"owner changed from [" + dashboard.getUsername() + "] " + 
					"to [" + cd.getAccountId() + "]");
			result.setChangeOwnerResult(SUCCESS);
		} else {
			String msg = "Failed to change owner of [" + createdDashboard.getName() + "]" + 
					"(" + createdDashboard.getId() + ") to " + 
					"[" + cd.getAccountId() + "]";
			Log.warn(LOGGER, msg);
			result.setChangeOwnerResult(msg);
		}
		// Save created dashboard
		DashboardMigrator.saveFile(
				newDir.resolve(dashboard.getPageName() + "." + dashboard.getId() + ".json").toString(), 
				cd,
				mixinMap);
		return result;
	}

}
