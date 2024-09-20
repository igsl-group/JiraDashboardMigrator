package com.igsl.model.mapping;

import java.util.List;

import javax.ws.rs.HttpMethod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.igsl.model.DataCenterPermission;
import com.igsl.model.PermissionTarget;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

/**
 * Filter list is retrieved from database for DC/Server as no REST API exists to list them.
 */
public class Filter extends JiraObject<Filter> {
	private static final Logger LOGGER = LogManager.getLogger();
	
	private String id;
	private String name;
	private String description;
	private String jql;
	private String originalJql;
	private PermissionTarget owner;
	private PermissionTarget originalOwner;
	private List<DataCenterPermission> sharePermissions;
	private String originalName;	// Used to rename filter and back
	
	@Override
	public int compareTo(Filter obj1) {
		if (obj1 != null) {
			return STRING_COMPARATOR.compare(getName(), obj1.getName());
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Filter> util, boolean cloud, Object... data) {
		if (cloud) {
			String filterId = null;
			if (data.length == 1) {
				filterId = String.valueOf(data[0]);
			}
			if (filterId != null) {
				util.path("/rest/api/latest/filter/{filterId}")
					.pathTemplate("filterId", filterId)
					.query("expand", "owner")	// Include owner information
					.query("overrideSharePermissions", true)	// Override permission
					.method(HttpMethod.GET)
					.pagination(new SinglePage<Filter>(Filter.class, null));
			} else {
				util.path("/rest/api/latest/filter/search")
					.method(HttpMethod.GET)
					.query("expand", "owner")	// Include owner information
					.query("overrideSharePermissions", true)	// Override permission
					.pagination(new Paged<Filter>(Filter.class));
			}
		} else {
			// There is no API to list filter in Server
			String filterId = String.valueOf(data[0]);
			util.path("/rest/api/latest/filter/{filterId}")
				.pathTemplate("filterId", filterId)
				.query("expand", "owner")	// Include owner information
				.query("overrideSharePermissions", true)	// Override permission
				.method(HttpMethod.GET)
				.pagination(new SinglePage<Filter>(Filter.class, null));
		}
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getJql() {
		return jql;
	}

	public void setJql(String jql) {
		this.jql = jql;
	}

	public String getOriginalJql() {
		return originalJql;
	}

	public void setOriginalJql(String originalJql) {
		this.originalJql = originalJql;
	}

	public PermissionTarget getOwner() {
		return owner;
	}

	public void setOwner(PermissionTarget owner) {
		this.owner = owner;
	}

	public List<DataCenterPermission> getSharePermissions() {
		return sharePermissions;
	}

	public void setSharePermissions(List<DataCenterPermission> sharePermissions) {
		this.sharePermissions = sharePermissions;
	}

	public String getOriginalName() {
		return originalName;
	}

	public void setOriginalName(String originalName) {
		this.originalName = originalName;
	}

	public PermissionTarget getOriginalOwner() {
		return originalOwner;
	}

	public void setOriginalOwner(PermissionTarget originalOwner) {
		this.originalOwner = originalOwner;
	}
}
