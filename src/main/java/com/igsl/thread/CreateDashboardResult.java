package com.igsl.thread;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.igsl.model.CloudDashboard;
import com.igsl.model.CloudPermission;
import com.igsl.model.DataCenterPortalPage;

public class CreateDashboardResult {

	public static final String SUCCESS = "Success";
	private DataCenterPortalPage originalDashboard;
	private CloudDashboard createdDashboard;
	private String createDashboardResult;
	private List<CloudPermission> editPermissionOmitted = new ArrayList<>();
	private List<CloudPermission> sharePermissionOmitted = new ArrayList<>();
	// Gadget id to result message(s)
	private Map<String, CreateGadgetResult> createGadgetResults = new LinkedHashMap<>();
	private String newOwner;
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
	public List<CloudPermission> getEditPermissionOmitted() {
		return editPermissionOmitted;
	}
	public void setEditPermissionOmitted(List<CloudPermission> editPermissionOmitted) {
		this.editPermissionOmitted = editPermissionOmitted;
	}
	public List<CloudPermission> getSharePermissionOmitted() {
		return sharePermissionOmitted;
	}
	public void setSharePermissionOmitted(List<CloudPermission> sharePermissionOmitted) {
		this.sharePermissionOmitted = sharePermissionOmitted;
	}
	public String getNewOwner() {
		return newOwner;
	}
	public void setNewOwner(String newOwner) {
		this.newOwner = newOwner;
	}
}
