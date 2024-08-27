package com.igsl.model.mapping;

import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;

import com.igsl.rest.RestUtil;

public class UserPicker extends JiraObject<UserPicker> {
	public static final String PARAM_QUERY = "query";
	
	private List<User> users;
	private int total;

	@Override
	public int compareTo(UserPicker obj1) {
		// No meaning for this class, it is only a wrapper for User.
		return 1;
	}

	@Override
	public void setupRestUtil(RestUtil<UserPicker> util, boolean cloud, Map<String, Object> data) {
		util.path("/rest/api/3/user/picker")
			.method(HttpMethod.GET)
			.query("query", data.get(PARAM_QUERY))
			.query("maxResults", 1);
	}
	
	public List<User> getUsers() {
		return users;
	}

	public void setUsers(List<User> users) {
		this.users = users;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

}
