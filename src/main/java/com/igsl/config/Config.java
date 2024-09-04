package com.igsl.config;

public class Config {
	public static final String DEFAULT_SCHEME = "https";
	
	private String sourceDatabaseURL;
	private String sourceDatabaseUser;
	private String sourceDatabasePassword;

	private String sourceScheme = DEFAULT_SCHEME;
	private String sourceHost;
	private String sourceUser;
	private String sourcePassword;
	
	private String targetScheme = DEFAULT_SCHEME;
	private String targetHost;
	private String targetUser;
	private String targetAPIToken;
	
	private boolean jerseyLog = false;
	
	public static Config getExample() {
		Config c = new Config();
		c.sourceDatabaseURL = "[Connection string to source database, e.g. jdbc:postgresql://127.0.0.1:5432/jiradb]";
		c.sourceDatabaseUser = "[Database user, e.g. postgres]";
		c.sourceDatabasePassword = "[Database password]";

		c.sourceScheme = "[https/http, defaults to https]";
		c.sourceHost = "[IP]:[Port]";
		c.sourceUser = "[Source Jira user, e.g. localadmin]";
		c.sourcePassword = "[Source Jira password]";
		
		c.targetScheme = "[https/http, defaults to https]";
		c.targetHost = "[Cloud Domain].atlassian.net";
		c.targetUser = "[Cloud site user, e.g kc.wong@igsl-group.com]";
		c.targetAPIToken = "[REST API token, generate one at https://id.atlassian.com/manage-profile/security/api-tokens]";
	
		return c;
	}
	
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
