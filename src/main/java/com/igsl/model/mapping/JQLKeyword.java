package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.List;

public enum JQLKeyword {
	AFFECTED_VERSION("affectedVersion"),
	APPROVALS("approvals"),
	ASSIGNEE("assignee"),
	ATTACHMENTS("attachments"),
	CATEGORY("category"),
	CHANGE_GATING_TYPE("change-gating-type"),
	COMMENT("comment"),
	COMPONENT("component"),
	CREATED("created", "createdDate"),
	CREATOR("creator"),
	// Custom field are by display name or cf[#]
	DESCRIPTION("description"),
	DUE("due", "dueDate"),
	ENVIRONMENT("environment"),
	FILTER("filter", "request", "savedFilter", "searchRequest"),
	FIX_VERSION("fixVersion"),
	HIERARCHY_LEVEL("hierarchyLevel"),
	ISSUE_KEY("issueKey", "id", "issue", "key"),
	ISSUE_LINK("issueLink"),
	ISSUE_LINK_TYPE("issueLinkType"),
	LABELS("labels"),
	LAST_VIEWED("lastViewed"),
	LEVEL("level"),
	ORGANIZATIONS("organizations"),
	ORIGINAL_ESTIMATE("originalEstimate", "timeOriginalEstimate"),
	PARENT("parent"),
	PARENT_PROJECT("parentProject"),
	PRIORITY("priority"),
	PROJECT("project"),
	PROJECT_TYPE("projectType"),
	REMAINING_ESTIMATE("remainingEstimate", "timeEstimate"),
	REPORTER("reporter"),
	REQUEST_CHANNEL_TYPE("request-channel-type"),
	REQUEST_LAST_ACTIVITIY_TIME("request-last-activity-time"),
	REQUEST_TYPE("Request Type"),
	RESOLUTION("resolution"),
	RESOLVED("resolved", "resolutionDate"),
	// SLA is by name
	SPRINT("sprint"),
	STATUS("status"),
	SUMMARY("summary"),
	TEXT("text"),
	TEXT_FIELDS("textfields"),
	TIME_SPENT("timeSpent"),
	TYPE("type", "issueType"),
	UPDATED("updated", "updatedDate"),
	VOTER("voter"),
	VOTES("votes"),
	WATCHER("watcher"),
	WATCHERS("watchers"),
	WORK_LOG_COMMENT("worklogComment"),
	WORK_LOG_DATE("worklogDate"),
	WORK_RATIO("workRatio");
	
	private List<String> values;
	JQLKeyword(String... values) {
		this.values = new ArrayList<>();
		for (String value : values) {
			this.values.add(value.toLowerCase());
		}
	}
	@Override
	public String toString() {
		return this.values.get(0);
	}
	public static JQLKeyword parse(String s) {
		for (JQLKeyword kw : JQLKeyword.values()) {
			if (kw.values.contains(s.toLowerCase())) {
				return kw;
			}
		}
		return null;
	}
}
