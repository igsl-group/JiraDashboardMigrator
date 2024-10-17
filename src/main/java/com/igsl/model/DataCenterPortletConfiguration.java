package com.igsl.model;

import java.util.ArrayList;
import java.util.List;

public class DataCenterPortletConfiguration {
	private int id;
	private int columnNumber;
	private int positionSeq;
	private String gadgetXml;
	private String color;
	private String dashboardCompleteKey;
	private List<DataCenterGadgetConfiguration> gadgetConfigurations;

	public DataCenterPortletConfiguration clone() {
		DataCenterPortletConfiguration result = new DataCenterPortletConfiguration();
		result.setId(this.getId());
		result.setColumnNumber(this.getColumnNumber());
		result.setPositionSeq(this.getPositionSeq());
		result.setGadgetXml(this.getGadgetXml());
		result.setDashboardCompleteKey(this.getDashboardCompleteKey());
		result.setColor(this.getColor());
		result.gadgetConfigurations = new ArrayList<>();
		for (DataCenterGadgetConfiguration conf : this.getGadgetConfigurations()) {
			result.gadgetConfigurations.add(conf.clone());
		}
		return result;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getColumnNumber() {
		return columnNumber;
	}

	public void setColumnNumber(int columnNumber) {
		this.columnNumber = columnNumber;
	}

	public int getPositionSeq() {
		return positionSeq;
	}

	public void setPositionSeq(int positionSeq) {
		this.positionSeq = positionSeq;
	}

	public String getGadgetXml() {
		return gadgetXml;
	}

	public void setGadgetXml(String gadgetXml) {
		this.gadgetXml = gadgetXml;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public String getDashboardCompleteKey() {
		return dashboardCompleteKey;
	}

	public void setDashboardCompleteKey(String dashboardCompleteKey) {
		this.dashboardCompleteKey = dashboardCompleteKey;
	}

	public List<DataCenterGadgetConfiguration> getGadgetConfigurations() {
		return gadgetConfigurations;
	}

	public void setGadgetConfigurations(List<DataCenterGadgetConfiguration> gadgetConfigurations) {
		this.gadgetConfigurations = gadgetConfigurations;
	}
}
