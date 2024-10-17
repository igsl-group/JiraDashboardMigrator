package com.igsl.thread;

import java.util.HashMap;
import java.util.Map;

import com.igsl.model.CloudDashboard;
import com.igsl.model.DataCenterPortalPage;

public class CreateDashboardResult {

	private DataCenterPortalPage originalDashboard;
	private CloudDashboard createdDashboard;
	private String deleteDashboardResult;
	private String createDashboardResult;
	// Gadget id to result message(s)
	private Map<String, CreateGadgetResult> createGadgetResults = new HashMap<>();
	private String changeOwnerResult;
	public String getCreateDashboardResult() {
		return createDashboardResult;
	}
	public void setCreateDashboardResult(String createDashboardResult) {
		this.createDashboardResult = createDashboardResult;
	}
	public Map<String, CreateGadgetResult> getCreateGadgetResults() {
		return createGadgetResults;
	}
	public void setCreateGadgetResults(Map<String, CreateGadgetResult> gadgetResults) {
		this.createGadgetResults = gadgetResults;
	}
	public String getChangeOwnerResult() {
		return changeOwnerResult;
	}
	public void setChangeOwnerResult(String changeOwnerResult) {
		this.changeOwnerResult = changeOwnerResult;
	}
	public DataCenterPortalPage getOriginalDashboard() {
		return originalDashboard;
	}
	public void setOriginalDashboard(DataCenterPortalPage originalDashboard) {
		this.originalDashboard = originalDashboard;
	}
	public CloudDashboard getCreatedDashboard() {
		return createdDashboard;
	}
	public void setCreatedDashboard(CloudDashboard createdDashboard) {
		this.createdDashboard = createdDashboard;
	}
	public String getDeleteDashboardResult() {
		return deleteDashboardResult;
	}
	public void setDeleteDashboardResult(String deleteDashboardResult) {
		this.deleteDashboardResult = deleteDashboardResult;
	}
}
