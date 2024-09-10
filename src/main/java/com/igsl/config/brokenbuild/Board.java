package com.igsl.config.brokenbuild;

import java.util.List;

public class Board {
	private String type;
	private String id;
	private String estimationField;
	private String sprintFilter;
	private int boardId;
	private List<String> customDoneStatuses;
	private String displayName;
	
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getEstimationField() {
		return estimationField;
	}
	public void setEstimationField(String estimationField) {
		this.estimationField = estimationField;
	}
	public String getSprintFilter() {
		return sprintFilter;
	}
	public void setSprintFilter(String sprintFilter) {
		this.sprintFilter = sprintFilter;
	}
	public int getBoardId() {
		return boardId;
	}
	public void setBoardId(int boardId) {
		this.boardId = boardId;
	}
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	public List<String> getCustomDoneStatuses() {
		return customDoneStatuses;
	}
	public void setCustomDoneStatuses(List<String> customDoneStatuses) {
		this.customDoneStatuses = customDoneStatuses;
	}
}
