package com.igsl.config;

public class Config {
	public static final String DEFAULT_SCHEME = "https";
	public static final int DEFAULT_CONNECTION_POOL_SIZE = 10;
	public static final int DEFAULT_THREAD_COUNT = 8;
	public static final long DEFAULT_THREAD_WAIT = 1000L;
	public static final long DEFAULT_LIMIT = 10;
	public static final long DEFAULT_PERIOD = 1000;
	
	public static final int DEFAULT_CONNECT_TIMEOUT = 0;
	public static final int DEFAULT_READ_TIMEOUT = 0;
	
	private String defaultOwner;
	
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
	
	private int connectionPoolSize = DEFAULT_CONNECTION_POOL_SIZE;
	private int threadCount = DEFAULT_THREAD_COUNT;
	private long threadWait = DEFAULT_THREAD_WAIT;
	
	private long limit = DEFAULT_LIMIT;
	private long period = DEFAULT_PERIOD;
	
	private int readTimeout = DEFAULT_READ_TIMEOUT;
	private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
	
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
	
		c.connectionPoolSize = DEFAULT_CONNECTION_POOL_SIZE;
		c.threadCount = DEFAULT_THREAD_COUNT;
		c.threadWait = DEFAULT_THREAD_WAIT;
		
		c.limit = DEFAULT_LIMIT;
		c.period = DEFAULT_PERIOD;
		
		c.defaultOwner = "[Cloud account ID to be used as a replacement owner of filters and dashboards if the owner is not a user in Cloud]";

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

	public int getConnectionPoolSize() {
		return connectionPoolSize;
	}

	public void setConnectionPoolSize(int connectionPoolSize) {
		this.connectionPoolSize = connectionPoolSize;
	}

	public int getThreadCount() {
		return threadCount;
	}

	public void setThreadCount(int threadCount) {
		this.threadCount = threadCount;
	}

	public long getThreadWait() {
		return threadWait;
	}

	public void setThreadWait(long threadWait) {
		this.threadWait = threadWait;
	}

	public long getLimit() {
		return limit;
	}

	public void setLimit(long limit) {
		this.limit = limit;
	}

	public long getPeriod() {
		return period;
	}

	public void setPeriod(long period) {
		this.period = period;
	}

	public int getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public String getDefaultOwner() {
		return defaultOwner;
	}

	public void setDefaultOwner(String defaultOwner) {
		this.defaultOwner = defaultOwner;
	}
}
