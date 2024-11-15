package com.igsl.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.igsl.Log;
import com.igsl.config.Config;
import com.igsl.model.CloudFilter;
import com.igsl.model.CloudPermission;
import com.igsl.model.PermissionTarget;
import com.igsl.model.mapping.Filter;
import com.igsl.rest.RestUtil;

public class EditFilterPermission implements Callable<String> {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final ObjectMapper OM = new ObjectMapper()
			// Indent
			.enable(SerializationFeature.INDENT_OUTPUT)
			// Allow comments
			.configure(Feature.ALLOW_COMMENTS, true)	
			// Allow attributes missing in POJO
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	
	private Config config;
	private boolean add;
	private String accountId;
	private Filter filter;
	
	public EditFilterPermission(Config config, boolean add, String accountId, Filter filter) {
		this.config = config;
		this.add = add;
		this.accountId = accountId;
		this.filter = filter;
	}
	
	/**
	 * @return Empty string if no error, otherwise the error message
	 */
	@Override
	public String call() throws Exception {
		String result = "";
		Log.info(LOGGER, "Processing filter [" + filter.getName() + "] (" + filter.getId() + ")");
		String realOwner = filter.getOwner().getAccountId();		
		RestUtil<Object> util = RestUtil.getInstance(Object.class);
		CloudFilter cf = CloudFilter.create(filter);
		cf.setOwner(null);
		cf.setDescription(null);
		cf.setJql(null);
		cf.setName(null);
		if (add) {
			Log.info(LOGGER, "Filter [" + filter.getName() + "] (" + filter.getId() + ") " +
						"Adding permission");
			PermissionTarget pt = new PermissionTarget();
			pt.setAccountId(accountId);
			CloudPermission cp = new CloudPermission();
			cp.setType("user");
			cp.setUser(pt);
			cf.getEditPermissions().add(cp);
		} else {
			Log.info(LOGGER, "Filter [" + filter.getName() + "] (" + filter.getId() + ") " + 
						"Removing permission");
			CloudPermission found = null;
			for (CloudPermission cp : cf.getEditPermissions()) {
				if ("user".equals(cp.getType()) && 
					accountId.equals(cp.getUser().getAccountId())) {
					found = cp;
					break;
				}
			}
			if (found != null) {
				Log.info(LOGGER, "Filter [" + filter.getName() + "] (" + filter.getId() + ") " + 
						"Removing found permission");
				cf.getEditPermissions().remove(found);
			} else {
				String msg = "Filter [" + filter.getName() + "] (" + filter.getId() + ") " + 
						"[" + accountId + "] is already not allowed to edit";
				Log.warn(LOGGER, msg);
				return result;
			}
		}
		try {
			Map<String, Object> payload = new HashMap<>();
			if (add && !realOwner.equals(accountId)) {
				// Change owner
				payload.clear();
				payload.put("accountId", accountId);
				util.config(config, true)
					.path("/rest/api/latest/filter/{filterId}/owner")
					.pathTemplate("filterId", filter.getId())
					.query("overrideSharePermissions", true)
					.method(HttpMethod.PUT)
					.payload(payload)
					.status(Status.OK.getStatusCode())
					.request();
				Log.info(LOGGER, "Filter [" + filter.getName() + "] (" + filter.getId() + ") owner changed to " + 
					accountId);
			}
			// Update permission
			Log.info(LOGGER, "Filter [" + filter.getName() + "] (" + filter.getId() + ") " + 
					"Payload: [" + OM.writeValueAsString(cf) + "]");
			util.config(config, true)
				.path("/rest/api/latest/filter/{filterId}")
				.pathTemplate("filterId", filter.getId())
				.query("overrideSharePermissions", true)
				.method(HttpMethod.PUT)
				.payload(cf)
				.status(Status.OK.getStatusCode())
				.request();
			Log.info(LOGGER, "Filter [" + filter.getName() + "] (" + filter.getId() + ") permission changed");
			if (add && !realOwner.equals(accountId)) {
				// Change owner back
				payload.clear();
				payload.put("accountId", realOwner);
				util.config(config, true)
					.path("/rest/api/latest/filter/{filterId}/owner")
					.pathTemplate("filterId", filter.getId())
					.query("overrideSharePermissions", true)
					.method(HttpMethod.PUT)
					.payload(payload)
					.status(Status.OK.getStatusCode())
					.request();
				Log.info(LOGGER, "Filter [" + filter.getName() + "] (" + filter.getId() + ") owner reverted to " + 
						filter.getOwner().getAccountId());
			}
		} catch (Exception ex) {
			String msg = "Failed to " + (add? "add " : "remove ") + 
					"[" + accountId + "] to edit " + 
					"filter [" + filter.getName() + "] (" + filter.getId() + "): " + 
					ex.getMessage();
			Log.error(LOGGER, msg, ex);
			result = msg;
		}
		Log.info(LOGGER, 
				"Filter [" + filter.getName() + "] (" + filter.getId() + ") " + 
				(add? "Add" : "Remove") + " [" + accountId + "] Result = [" + result + "]");
		return result;
	}

}
