package com.igsl.model.mapping;

/**
 * JQL function list based on:
 * Server: https://confluence.atlassian.com/jiracoreserver/advanced-searching-functions-reference-939937722.html
 * Cloud: https://support.atlassian.com/jira-software-cloud/docs/jql-functions/
 * 
 * Cloud-only JQL functions are commented.
 * Server-only JQL functions are marked as obsolete.
 */
public enum JQLFunction {
	APPROVED("approved"),
	APPROVER("approver", new JQLFuncArg(MappingType.USER, true, true)),
	BREACHED("breached"),
	CASCADE_OPTION("cascadeOption", 
			// Only two levels supported, but no harm in supporting more here
			new JQLFuncArg(MappingType.CUSTOM_FIELD_OPTION, true, true)),
	//CHOICE_OPTION("choiceOption", new JQLFuncArg(MappingType.CUSTOM_FIELD_OPTION, true, true)),
	CLOSED_SPRINTS("closedSprints"),
	COMPLETED("completed"),
	COMPONENTS_LEAD_BY_USER("componentsLeadByUser", new JQLFuncArg(MappingType.USER, true, false)),
	CURRENT_LOGIN("currentLogin"),
	CURRENT_USER("currentUser"),
	CUSTOMER_DETAIL("customerDetail"),
	EARLIEST_UNRELEASED_VERSION("earliestUnreleasedVersion", new JQLFuncArg(MappingType.PROJECT, true, false)),
	ELAPSED("elapsed"),
	END_OF_DAY("endOfDay", new JQLFuncArg(false, false)),
	END_OF_MONTH("endOfMonth", new JQLFuncArg(false, false)),
	END_OF_WEEK("endOfWeek", new JQLFuncArg(false, false)),
	END_OF_YEAR("endOfYear", new JQLFuncArg(false, false)),
//	ENTITLEMENT_DETAIL("entitlementDetail", 
//			new JQLFuncArg(MappingType.CUSTOM_FIELD, true, false), 
//			new JQLFuncArg(true, false)),
//	ENTITLEMENT_PRODUCT("entitlementProduct", new JQLFuncArg(true, false)),
	EVER_BREACHED("everbreached"),
	FUTURE_SPRINTS("futureSprints"),
	ISSUE_HISTORY("issueHistory"),
	ISSUES_WITH_REMOVE_LINKS_BY_GLOBAL_ID("issuesWithRemoteLinksByGlobalId"),
	LAST_LOGIN("lastLogin"),
	LATEST_RELEASED_VERSION("latestReleasedVersion", new JQLFuncArg(MappingType.PROJECT, true, false)),
	LINKED_ISSUES("linkedIssues", new JQLFuncArg(true, false), new JQLFuncArg(false, true)),
	MEMBERS_OF("membersOf", new JQLFuncArg(MappingType.GROUP, true, false)),
	MY_APPROVAL("myApproval"),
	MY_PENDING("myPending"),
	NOW("now"),
	OPEN_SPRINTS("openSprints"),
//	ORGANIZATION_DETAIL("organizationDetail"),
//	ORGANIZATION_MEMBERS("organizationMembers"),
	OUTDATED("outdated", true),
//	PARENT_EPIC("parentEpic"),
	PAUSED("paused"),
	PENDING("pending"),
	PENDING_BY("pendingBy", new JQLFuncArg(MappingType.USER, true, true)),
	PROJECTS_LEAD_BY_USER("projectsLeadByUser", new JQLFuncArg(MappingType.USER, true, false)),
	PROJECTS_WHERE_USER_HAS_PERMISSION("projectsWhereUserHasPermission", new JQLFuncArg(true, false)),
	PROJECTS_WHERE_USER_HAS_ROLE("projectsWhereUserHasRole", new JQLFuncArg(MappingType.ROLE, true, false)),
	RELEASED_VERSIONS("releasedVersions", new JQLFuncArg(MappingType.PROJECT, true, false)),
	REMAINING("remaining"),
	RUNNING("running"),
	STANDARD_ISSUE_TYPES("standardIssueTypes"),
	START_OF_DAY("startOfDay", new JQLFuncArg(false, false)),
	START_OF_MONTH("startOfMonth", new JQLFuncArg(false, false)),
	START_OF_WEEK("startOfWeek", new JQLFuncArg(false, false)),
	START_OF_YEAR("startOfYear", new JQLFuncArg(false, false)),
	SUBTASK_ISSUE_TYPES("subtaskIssueTypes"),
	UNRELEASED_VERSIONS("unreleasedVersions", new JQLFuncArg(MappingType.PROJECT, true, false)),
	UPDATED_BY("updatedBy", 
			new JQLFuncArg(MappingType.USER, true, false), 
			new JQLFuncArg(false, false), 
			new JQLFuncArg(false, false)),
	VOTED_ISSUES("votedIssues"),
	WATCHED_ISSUES("watchedIssues"),
	WITHIN_CALENDAR_HOURS("withinCalendarHours");
	public static JQLFunction parse(String name) {
		for (JQLFunction func : JQLFunction.values()) {
			if (func.getFunctionName().equalsIgnoreCase(name)) {
				return func;
			}
		}
		return null;
	}
	private String functionName;
	private JQLFuncArg[] arguments; 
	private boolean obsolete;	
	JQLFunction(String functionName, boolean obsolete) {
		this.functionName = functionName;
		this.obsolete = true;
	}
	JQLFunction(String functionName, JQLFuncArg... arguments) {
		this.functionName = functionName;
		this.arguments = arguments;
	}
	public String getFunctionName() {
		return functionName;
	}
	public JQLFuncArg[] getArguments() {
		return arguments;
	}
	public boolean isObsolete() {
		return obsolete;
	}
}