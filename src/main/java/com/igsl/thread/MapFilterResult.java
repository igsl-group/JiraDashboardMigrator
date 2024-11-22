package com.igsl.thread;

import java.util.ArrayList;
import java.util.List;

import com.igsl.model.DataCenterPermission;
import com.igsl.model.mapping.Filter;

public class MapFilterResult {
	private Filter original;
	private Filter target;
	private Exception exception;
	private List<DataCenterPermission> removedSharePermissions = new ArrayList<>();
	private List<DataCenterPermission> removedEditPermissions = new ArrayList<>();
	private String newOwner;
	public Exception getException() {
		return exception;
	}
	public Filter getOriginal() {
		return original;
	}
	public Filter getTarget() {
		return target;
	}
	public void setOriginal(Filter original) {
		this.original = original;
	}
	public void setTarget(Filter target) {
		this.target = target;
	}
	public void setException(Exception exception) {
		this.exception = exception;
	}
	public String getNewOwner() {
		return newOwner;
	}
	public void setNewOwner(String newOwner) {
		this.newOwner = newOwner;
	}
	public List<DataCenterPermission> getRemovedSharePermissions() {
		return removedSharePermissions;
	}
	public void setRemovedSharePermissions(List<DataCenterPermission> removedSharePermissions) {
		this.removedSharePermissions = removedSharePermissions;
	}
	public List<DataCenterPermission> getRemovedEditPermissions() {
		return removedEditPermissions;
	}
	public void setRemovedEditPermissions(List<DataCenterPermission> removedEditPermissions) {
		this.removedEditPermissions = removedEditPermissions;
	}
}