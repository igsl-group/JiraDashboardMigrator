package com.igsl.config;

public class Config {
	private String sourceDatabaseURL = "[Connection string to source database, e.g. jdbc:postgresql://127.0.0.1:5432/jiradb]";
	private String sourceDatabaseUser = "[Database user, e.g. postgres]";
	private String sourceDatabasePassword = "[Database password]";
	private String sourceRESTBaseURL = "[Base URL for Jira 8.14.1 REST API, e.g. http://localhost:8080/jira]";
	private String sourceUser = "[Source Jira user, e.g. localadmin]";
	private String sourcePassword = "[Source Jira password]";
	private String targetRESTBaseURL = "[Base URL for Jira Cloud REST API, e.g. https://igsl-cms-uat.atlassian.net]";
	private String targetUser = "[Cloud site user, e.g kc.wong@igsl-group.com]";
	private String targetAPIToken = "[REST API token, generate one at https://id.atlassian.com/manage-profile/security/api-tokens]";
	private boolean jerseyLog = false;

	private String sourceScheme = "[https/http, defaults to https]";
	private String sourceHost = "[IP]:[Port]";
	private String targetScheme = "[https/http, defaults to https]";
	private String targetHost = "[Cloud Domain].atlassian.net";
	
	// Generated
	public String getSourceUser() {
		return sourceUser;
	}

	public void setSourceUser(String sourceUser) {
		this.sourceUser = sourceUser;
	}

	public String getSourcePassword() {
		return sourcePassword;
	}

	public void setSourcePassword(String sourcePassword) {
		this.sourcePassword = sourcePassword;
	}

	public String getSourceDatabaseURL() {
		return sourceDatabaseURL;
	}

	public void setSourceDatabaseURL(String sourceDatabaseURL) {
		this.sourceDatabaseURL = sourceDatabaseURL;
	}

	public String getSourceDatabaseUser() {
		return sourceDatabaseUser;
	}

	public void setSourceDatabaseUser(String sourceDatabaseUser) {
		this.sourceDatabaseUser = sourceDatabaseUser;
	}

	public String getSourceDatabasePassword() {
		return sourceDatabasePassword;
	}

	public void setSourceDatabasePassword(String sourceDatabasePassword) {
		this.sourceDatabasePassword = sourceDatabasePassword;
	}

	public String getSourceRESTBaseURL() {
		return sourceRESTBaseURL;
	}

	public void setSourceRESTBaseURL(String sourceRESTBaseURL) {
		this.sourceRESTBaseURL = sourceRESTBaseURL;
	}

	public String getTargetRESTBaseURL() {
		return targetRESTBaseURL;
	}

	public void setTargetRESTBaseURL(String targetRESTBaseURL) {
		this.targetRESTBaseURL = targetRESTBaseURL;
	}

	public String getTargetUser() {
		return targetUser;
	}

	public void setTargetUser(String targetUser) {
		this.targetUser = targetUser;
	}

	public String getTargetAPIToken() {
		return targetAPIToken;
	}

	public void setTargetAPIToken(String targetAPIToken) {
		this.targetAPIToken = targetAPIToken;
	}

	public boolean isJerseyLog() {
		return jerseyLog;
	}

	public void setJerseyLog(boolean jerseyLog) {
		this.jerseyLog = jerseyLog;
	}

	public String getSourceScheme() {
		return sourceScheme;
	}

	public void setSourceScheme(String sourceScheme) {
		this.sourceScheme = sourceScheme;
	}

	public String getSourceHost() {
		return sourceHost;
	}

	public void setSourceHost(String sourceHost) {
		this.sourceHost = sourceHost;
	}

	public String getTargetScheme() {
		return targetScheme;
	}

	public void setTargetScheme(String targetScheme) {
		this.targetScheme = targetScheme;
	}

	public String getTargetHost() {
		return targetHost;
	}

	public void setTargetHost(String targetHost) {
		this.targetHost = targetHost;
	}
}
