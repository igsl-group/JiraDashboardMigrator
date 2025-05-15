package com.igsl.thread;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.atlassian.jira.jql.parser.antlr.JqlLexer;
import com.atlassian.jira.jql.parser.antlr.JqlParser;
import com.atlassian.jira.jql.parser.antlr.JqlParser.query_return;
import com.atlassian.query.clause.AndClause;
import com.atlassian.query.clause.ChangedClause;
import com.atlassian.query.clause.Clause;
import com.atlassian.query.clause.NotClause;
import com.atlassian.query.clause.OrClause;
import com.atlassian.query.clause.TerminalClause;
import com.atlassian.query.clause.WasClause;
import com.atlassian.query.clause.WasClauseImpl;
import com.atlassian.query.operand.EmptyOperand;
import com.atlassian.query.operand.FunctionOperand;
import com.atlassian.query.operand.MultiValueOperand;
import com.atlassian.query.operand.Operand;
import com.atlassian.query.operand.SingleValueOperand;
import com.atlassian.query.order.OrderBy;
import com.atlassian.query.order.OrderByImpl;
import com.atlassian.query.order.SearchSort;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.igsl.DashboardMigrator;
import com.igsl.DashboardMigrator.MyChangedClause;
import com.igsl.DashboardMigrator.MyTerminalClause;
import com.igsl.FilterNotMappedException;
import com.igsl.Log;
import com.igsl.NotAllValuesMappedException;
import com.igsl.config.Config;
import com.igsl.model.CloudFilter;
import com.igsl.model.CloudPermission;
import com.igsl.model.DataCenterPermission;
import com.igsl.model.PermissionTarget;
import com.igsl.model.PermissionType;
import com.igsl.model.mapping.CustomField;
import com.igsl.model.mapping.Filter;
import com.igsl.model.mapping.Group;
import com.igsl.model.mapping.JQLFuncArg;
import com.igsl.model.mapping.JQLFunction;
import com.igsl.model.mapping.JQLKeyword;
import com.igsl.model.mapping.JiraObject;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;
import com.igsl.model.mapping.Project;
import com.igsl.model.mapping.Role;
import com.igsl.model.mapping.User;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class ResetFilter implements Callable<MapFilterResult> {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final ObjectMapper OM = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			// Allow comments
			.configure(Feature.ALLOW_COMMENTS, true)	
			// Allow attributes missing in POJO
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	
	private Config config;
	private Filter filter;
	private String myAccountId;
		
	public ResetFilter(
			Config config,
			String myAccountId,
			Filter filter) {
		this.config = config;
		this.myAccountId = myAccountId;
		this.filter = filter;
	}
	
	private static String getFilterDisplay(Filter fileFilter, Filter cloudFilter) {
		String result = fileFilter.getName() + " (" + fileFilter.getId();
		if (cloudFilter != null) {
			result += "/" + cloudFilter.getId();
		}
		result += ")";
		return result;
	}
	
	@Override
	public MapFilterResult call() throws Exception {
		MapFilterResult result = new MapFilterResult();
		result.setOriginal(filter);
		result.getMessages().add("Processing filter " + getFilterDisplay(filter, null));
		// Verify and map owner
		String originalOwner = filter.getOwner().getAccountId();
		List<DataCenterPermission> editPermissions = filter.getEditPermissions();
		List<DataCenterPermission> sharePermissions = filter.getSharePermissions();
		// Check if filter exists under originalOwner or myAccountId
		RestUtil<Filter> util = RestUtil.getInstance(Filter.class);
		Filter existingFilter = filterExists(config, null, filter.getName(), originalOwner);
		Map<String, Object> payload = new HashMap<>();
		if (existingFilter == null) {
			existingFilter = filterExists(config, null, filter.getName(), myAccountId);
		} else {
			result.getMessages().add("Found filter " + getFilterDisplay(filter, existingFilter));
			// If under originalOwner, change owner to myAccountId
			try {
				payload.clear();
				payload.put("accountId", myAccountId);
				util.config(config, true)
					.path("/rest/api/latest/filter/{filterId}/owner")
					.pathTemplate("filterId", existingFilter.getId())
					.query("overrideSharePermissions", true)
					.method(HttpMethod.PUT)
					.payload(payload)
					.status(Status.OK.getStatusCode())
					.request();
				result.getMessages().add("Changed owner from " + originalOwner + " to " + myAccountId);
			} catch (Exception ex) {
				result.getMessages().add("Failed to change owner from " + originalOwner + " to " + myAccountId + 
						": " + ex.getMessage());
				return result;
			}
		}
		if (existingFilter == null) {
			result.getMessages().add("Missing filter " + getFilterDisplay(filter, existingFilter));
			// Create filter
			try {
				CloudFilter cloudFilter = CloudFilter.create(filter);
				result.getMessages().add("Create payload: " + OM.writeValueAsString(cloudFilter));
				List<Filter> newFilter = util.config(config, true)
					.path("/rest/api/latest/filter")
					.method(HttpMethod.POST)
					.query("overrideSharePermissions", true)
					.payload(cloudFilter)
					.status(Status.OK.getStatusCode())
					.pagination(new SinglePage<Filter>(Filter.class))
					.requestNextPage();
				existingFilter = newFilter.get(0);
				result.getMessages().add("Recreated filter " + getFilterDisplay(filter, existingFilter));
			} catch (Exception ex) {
				result.getMessages().add("Failed to recreated filter " + getFilterDisplay(filter, existingFilter) + 
						": " + ex.getMessage());
			}
		} else {
			result.getMessages().add("Found filter " + getFilterDisplay(filter, existingFilter));
			CloudFilter cloudFilter = CloudFilter.create(existingFilter);
			cloudFilter.getSharePermissions().clear();
			for (DataCenterPermission permission : sharePermissions) {
				cloudFilter.getSharePermissions().add(CloudPermission.create(permission));
			}
			cloudFilter.getEditPermissions().clear();
			for (DataCenterPermission permission : editPermissions) {
				cloudFilter.getEditPermissions().add(CloudPermission.create(permission));
			}
			// If logged in exists in share, delete everything else
			CloudPermission shareAuthenticated = null;
			for (CloudPermission permission : cloudFilter.getSharePermissions()) {
				if (PermissionType.LOGGED_IN.toString().equals(permission.getType())) {
					shareAuthenticated = permission;
					break;
				}
			}
			if (shareAuthenticated != null) {
				cloudFilter.getSharePermissions().clear();
				cloudFilter.getSharePermissions().add(shareAuthenticated);
			}
			// Update edit and share permissions
			cloudFilter.setOwner(null);
			cloudFilter.setDescription(null);
			cloudFilter.setJql(null);
			result.getMessages().add("Update payload: " + OM.writeValueAsString(cloudFilter));
			try {
				util.config(config, true)
					.path("/rest/api/latest/filter/{filterId}")
					.pathTemplate("filterId", existingFilter.getId())
					.query("overrideSharePermissions", true)
					.method(HttpMethod.PUT)
					.payload(cloudFilter)
					.status(Status.OK.getStatusCode())
					.request();
				result.getMessages().add("Updated filter permissions " + getFilterDisplay(filter, existingFilter));
			} catch (Exception ex) {
				result.getMessages().add("Failed to update filter permissions " + 
						getFilterDisplay(filter, existingFilter) + 
						": " + ex.getMessage());
			}
		}		
		// Verify originalOwner is viable, if not, use default owner
		RestUtil<User> userUtil = RestUtil.getInstance(User.class);
		boolean useDefaultUser = false;
		try {
			List<User> userList = userUtil.config(config, true)
					.path("/rest/api/latest/user/search")
					.method(HttpMethod.GET)
					.query("accountId", originalOwner)
					.requestAllPages();
			if (userList.size() != 1) {
				result.getMessages().add(
						"Original user " + originalOwner + " cannot be found, default to " + config.getDefaultOwner());
				useDefaultUser = true;
			}
		} catch (Exception ex) {
			result.getMessages().add(
					"Original user " + originalOwner + " cannot be validated, default to " + config.getDefaultOwner());
			useDefaultUser = true;
		}
		// Reset owner to originalOwner
		String newOwner = (useDefaultUser? config.getDefaultOwner() : originalOwner);
		try {
			payload.clear();
			payload.put("accountId", newOwner);
			util.config(config, true)
				.path("/rest/api/latest/filter/{filterId}/owner")
				.pathTemplate("filterId", existingFilter.getId())
				.query("overrideSharePermissions", true)
				.method(HttpMethod.PUT)
				.payload(payload)
				.status(Status.OK.getStatusCode())
				.request();
			result.getMessages().add("Changed owner from " + myAccountId + " to " + newOwner);
			result.setSuccess(true);
		} catch (Exception ex) {
			result.getMessages().add("Failed to change owner from " + myAccountId + " to " + newOwner + 
					": " + ex.getMessage());
		}
		return result;
	}
	
	/**
	 * Find a filter based on provided criteria.
	 * Return found filter. Returns null if not found.
	 */
	private static Filter filterExists(Config conf, String id, String name, String ownerAccountId) 
			throws Exception {
		Log.info(LOGGER, 
				"filterExists: " +
				"id: [" + id + "] " + 
				"name: [" + name + "] " +
				"owner: [" + ownerAccountId + "]");
		RestUtil<Filter> util = RestUtil.getInstance(Filter.class).config(conf, true);
		util.path("/rest/api/latest/filter/search")
			.method(HttpMethod.GET)
			.pagination(new Paged<Filter>(Filter.class).maxResults(1))
			.query("expand", "owner")
			.query("overrideSharePermissions", true);
		if (id != null) {
			util.query("id", id);
		}
		if (name != null) {
			util.query("filterName", "\"" + name + "\"");
		}
		if (ownerAccountId != null) {
			util.query("accountId", ownerAccountId);
		}
		List<Filter> list = util.requestAllPages();
		Log.info(LOGGER, 
				"filterExists: " +
				"id: [" + id + "] " + 
				"name: [" + name + "] " +
				"owner: [" + ownerAccountId + "] Count = " + list.size());
		if (list.size() == 1) {
			Log.info(LOGGER, 
					"filterExists: " +
					"id: [" + id + "] " + 
					"name: [" + name + "] " +
					"owner: [" + ownerAccountId + "] = true");
			return list.get(0);
		}
		Log.info(LOGGER, 
				"filterExists: " +
				"id: [" + id + "] " + 
				"name: [" + name + "] " +
				"owner: [" + ownerAccountId + "] = false");
		return null;
	}
	
}
