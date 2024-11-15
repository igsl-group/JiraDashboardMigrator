package com.igsl.model.mapping;

import java.util.List;

import javax.ws.rs.HttpMethod;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.igsl.DashboardMigrator;
import com.igsl.config.Config;
import com.igsl.mybatis.FilterMapper;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;

public class Group extends JiraObject<Group> {
	private String groupId;
	private String name;
	private String html;

	@Override
	public String getDisplay() {
		return name;
	}
	
	@Override
	public String getInternalId() {
		if (groupId != null) {
			return groupId;
		}
		return name;
	}

	@Override
	public String getJQLName() {
		return name;
	}
	
	@Override
	public boolean jqlEquals(String value) {
		return 	groupId.equalsIgnoreCase(value) || 
				name.equalsIgnoreCase(value);
	}
	
	@Override
	public int compareTo(Group obj1, boolean exactMatch) {
		if (obj1 != null) {
			return compareName(getName(), obj1.getName(), exactMatch);
		}
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<Group> util, boolean cloud, Object... data) {
		if (cloud) {
			util.path("/rest/api/latest/group/bulk")
				.method(HttpMethod.GET)
				.pagination(new Paged<Group>(Group.class));
		} else {
//			// There is no API to get all groups, wtf
//			util.path("/rest/api/latest/groups/picker")
//				.method(HttpMethod.GET)
//				.query("maxResults", 1000)
//				.pagination(new SinglePage<Group>(Group.class, "groups"));
		}
	}
	
	@Override
	protected List<Group> _getObjects(
			Config config, 
			Class<Group> dataClass, 
			boolean cloud,
			Object... data)
			throws Exception {
		if (cloud) {
			return super._getObjects(config, dataClass, cloud, data);
		} else {
			// Get from database
			SqlSessionFactory factory = DashboardMigrator.setupMyBatis(config);
			try (SqlSession session = factory.openSession()) {
				FilterMapper filterMapper = session.getMapper(FilterMapper.class);
				return filterMapper.getGroups();
			}
		}
	}
	
	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getHtml() {
		return html;
	}

	public void setHtml(String html) {
		this.html = html;
	}

}
