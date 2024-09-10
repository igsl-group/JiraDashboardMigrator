package com.igsl.config.brokenbuild;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class VelocityChartConfig {
	private List<Board> boards;
	private int boardId;
	private String estimationField;
	private String sprintDateField;
	private Map<String, Object> otherProperties = new LinkedHashMap<String, Object>();

	public List<Board> getBoards() {
		return boards;
	}
	public void setBoards(List<Board> boards) {
		this.boards = boards;
	}
	public String getEstimationField() {
		return estimationField;
	}
	public void setEstimationField(String estimationField) {
		this.estimationField = estimationField;
	}
	@JsonAnyGetter
	public Map<String, Object> getUnknown() {
		return otherProperties;
	}
	@JsonAnySetter
	public void setUnknown(String key, Object value) {
		this.otherProperties.put(key, value);
	}
	public String getSprintDateField() {
		return sprintDateField;
	}
	public void setSprintDateField(String sprintDateField) {
		this.sprintDateField = sprintDateField;
	}
	public int getBoardId() {
		return boardId;
	}
	public void setBoardId(int boardId) {
		this.boardId = boardId;
	}
}
