package com.igsl.model;

public class DataCenterGadgetConfiguration {
	private int id;
	private String userPrefKey;
	private String userPrefValue;

	public DataCenterGadgetConfiguration clone() {
		DataCenterGadgetConfiguration result = new DataCenterGadgetConfiguration();
		result.setId(this.getId());
		result.setUserPrefKey(this.getUserPrefKey());
		result.setUserPrefValue(this.getUserPrefValue());
		return result;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getUserPrefKey() {
		return userPrefKey;
	}

	public void setUserPrefKey(String userPrefKey) {
		this.userPrefKey = userPrefKey;
	}

	public String getUserPrefValue() {
		return userPrefValue;
	}

	public void setUserPrefValue(String userPrefValue) {
		this.userPrefValue = userPrefValue;
	}
}
