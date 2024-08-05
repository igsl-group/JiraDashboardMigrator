package com.igsl;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.logging.log4j2.Log4j2Impl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fusesource.jansi.AnsiConsole;
import org.postgresql.Driver;

import com.atlassian.jira.jql.parser.antlr.JqlLexer;
import com.atlassian.jira.jql.parser.antlr.JqlParser;
import com.atlassian.jira.jql.parser.antlr.JqlParser.query_return;
import com.atlassian.query.clause.AndClause;
import com.atlassian.query.clause.ChangedClause;
import com.atlassian.query.clause.ChangedClauseImpl;
import com.atlassian.query.clause.Clause;
import com.atlassian.query.clause.NotClause;
import com.atlassian.query.clause.OrClause;
import com.atlassian.query.clause.TerminalClause;
import com.atlassian.query.clause.TerminalClauseImpl;
import com.atlassian.query.clause.WasClause;
import com.atlassian.query.clause.WasClauseImpl;
import com.atlassian.query.operand.EmptyOperand;
import com.atlassian.query.operand.FunctionOperand;
import com.atlassian.query.operand.MultiValueOperand;
import com.atlassian.query.operand.Operand;
import com.atlassian.query.operand.SingleValueOperand;
import com.atlassian.query.operator.Operator;
import com.atlassian.query.order.OrderBy;
import com.atlassian.query.order.OrderByImpl;
import com.atlassian.query.order.SearchSort;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.igsl.config.Config;
import com.igsl.config.DataFile;
import com.igsl.config.GadgetConfigType;
import com.igsl.config.GadgetType;
import com.igsl.config.Operation;
import com.igsl.model.CloudDashboard;
import com.igsl.model.CloudFilter;
import com.igsl.model.CloudGadget;
import com.igsl.model.CloudGadgetConfiguration;
import com.igsl.model.DataCenterFilter;
import com.igsl.model.DataCenterPermission;
import com.igsl.model.DataCenterPortalPage;
import com.igsl.model.DataCenterPortalPermission;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.PermissionTarget;
import com.igsl.model.PermissionType;
import com.igsl.model.mapping.CustomField;
import com.igsl.model.mapping.Dashboard;
import com.igsl.model.mapping.DashboardSearchResult;
import com.igsl.model.mapping.Filter;
import com.igsl.model.mapping.Group;
import com.igsl.model.mapping.GroupPickerResult;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;
import com.igsl.model.mapping.Project;
import com.igsl.model.mapping.Role;
import com.igsl.model.mapping.SearchResult;
import com.igsl.model.mapping.Status;
import com.igsl.model.mapping.User;
import com.igsl.mybatis.FilterMapper;

/**
 * Migrate dashboard and filter from Jira Data Center 8.14.1 to Jira Cloud. The
 * official Jira Cloud Migration Assistant does not migrate dashboards and
 * filters. This tool will: - Read dashboard and filter data from Jira 8.14.1
 * database (to get a list of them) - Extract information on dashboard and
 * filter using 8.14.1 REST API - Recreate filters on Cloud using REST API -
 * Recreate dashboard on Cloud using REST API
 * 
 * @author kcwong
 */
public class DashboardMigrator {

	private static final String NEWLINE = System.getProperty("line.separator");
	private static final Logger LOGGER = LogManager.getLogger(DashboardMigrator.class);

	private static final ObjectMapper OM = new ObjectMapper()
			.configure(Feature.ALLOW_COMMENTS, true)	// Allow comments
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) // Allow attributes missing in POJO
			.enable(SerializationFeature.INDENT_OUTPUT);
	
	private static SqlSessionFactory setupMyBatis(Config conf) throws Exception {
		PooledDataSource ds = new PooledDataSource();
		ds.setDriver(Driver.class.getCanonicalName());
		ds.setUrl(conf.getSourceDatabaseURL());
		String dbUser = conf.getSourceDatabaseUser();
		if (dbUser == null || dbUser.isEmpty()) {
			dbUser = Console.readLine("DataCenter database user: ");
			conf.setSourceDatabaseUser(dbUser);
		}
		String dbPassword = conf.getSourceDatabasePassword();
		if (dbPassword == null || dbPassword.isEmpty()) {
			dbPassword = new String(Console.readPassword("DataCenter database password for " + dbUser + ": "));
			conf.setSourceDatabasePassword(dbPassword);
		}
		ds.setUsername(dbUser);
		ds.setPassword(dbPassword);
		TransactionFactory transactionFactory = new JdbcTransactionFactory();
		Environment environment = new Environment("development", transactionFactory, ds);
		Configuration configuration = new Configuration(environment);
		configuration.addMapper(FilterMapper.class);
		configuration.setLogImpl(Log4j2Impl.class);
		return new SqlSessionFactoryBuilder().build(configuration);
	}
	
	/**
	 * Overloaded version with default parameter values.
	 */
	private static <T> List<T> restCallWithPaging(
			Client client, 
			URI path, 
			String method, 
			MultivaluedMap<String, Object> headers,
			Map<String, Object> queryParameters, 
			Object data,
			Class<T> objectClass) throws Exception {
		return restCallWithPaging(
				client, path, method, 
				headers, queryParameters, data, 
				null, 0, 
				"startAt", 0,
				null, 
				"values",
				objectClass);
	}
	
	/** Invoke REST API with paging 
	 * 
	 * @param client Client object with authentication already setup.
	 * @param URI REST API full path.
	 * @param method HTTP method.
	 * @param headers MultivaluedMap<String, Object> containing request headers, can be null.
	 * @param queryParameters Map<String, Object> containing query parameters, can be null.
	 * @param data Object containing POST data, can be null.
	 * @param pageSizeParameterName Name of page size parameter. Usually "maxResults". If null, page size is not set.
	 * @param pageSize Page size.
	 * @param pagingParameterName Name of starting index. Usually "startAt". 
	 * @param startingPage Int of first starting index. Usually 0.
	 * @param lastPageAttributeName Name of boolean attribute indicating last page.
	 * 								If null, calls will continue until returned size is 0.
	 * @param valuesAttributeName Name of attribute containing array of values. Usually "values". 
	 * @param objectClass Class<T> indicating which POJO to use to contain the values.
	 */
	private static <T> List<T> restCallWithPaging(
				Client client, URI path, String method, 
				MultivaluedMap<String, Object> headers,
				Map<String, Object> queryParameters, 
				Object data,
				String pageSizeParameterName,
				int pageSize,
				String pagingParameterName,	
				int startingPage,
				String lastPageAttributeName,
				String valuesAttributeName,
				Class<T> objectClass
			) throws Exception {
		List<T> result = new ArrayList<>();
		ObjectReader reader = OM.readerFor(objectClass);
		int pageIndex = startingPage;
		boolean done = false;
		while (!done) {
			WebTarget target = client.target(path);
			if (queryParameters != null) {
				for (Map.Entry<String, Object> entry : queryParameters.entrySet()) {
					if (entry.getValue() instanceof Collection) {
						Collection<?> list = (Collection<?>) entry.getValue();
						target = target.queryParam(entry.getKey(), list.toArray());
					} else if (entry.getValue().getClass().isArray()) {
						Object[] list = (Object[]) entry.getValue();
						target = target.queryParam(entry.getKey(), list);
					} else {
						target = target.queryParam(entry.getKey(), entry.getValue());
					}
				}
			}
			if (pageSizeParameterName != null) {
				target = target.queryParam(pageSizeParameterName, pageSize);
			}
			target = target.queryParam(pagingParameterName, pageIndex);
			Builder builder = target.request(MediaType.APPLICATION_JSON);
			if (headers != null) {
				builder = builder.headers(headers);
			}
			Response resp = null;
			if (HttpMethod.DELETE.equals(method)) {
				resp = builder.delete();
			} else if (HttpMethod.GET.equals(method)) {
				resp = builder.get();
			} else if (HttpMethod.HEAD.equals(method)) {
				resp = builder.head();
			} else if (HttpMethod.OPTIONS.equals(method)) {
				resp = builder.options();
			} else if (HttpMethod.POST.equals(method)) {
				resp = builder.post(Entity.entity(data, MediaType.APPLICATION_JSON));
			} else if (HttpMethod.PUT.equals(method)) {
				resp = builder.put(Entity.entity(data, MediaType.APPLICATION_JSON));
			} else {
				resp = builder.method(method, Entity.entity(data, MediaType.APPLICATION_JSON));
			}
			if (!checkStatusCode(resp, Response.Status.OK)) {
				throw new Exception(resp.readEntity(String.class));
			}
			String s = resp.readEntity(String.class);
			JsonNode root = OM.reader().readTree(s);
			JsonNode values = root.get(valuesAttributeName);
			if (values == null) {
				throw new Exception("Unable to read " + valuesAttributeName + " attribute");
			}
			if (values.getNodeType() != JsonNodeType.ARRAY) {
				throw new Exception("Attribute " + valuesAttributeName + " is not an array");
			}
			// Check if it is last page
			if (lastPageAttributeName != null) {
				JsonNode lastPageNode = root.get(lastPageAttributeName);
				if (lastPageNode == null) {
					throw new Exception("Unable to read " + lastPageAttributeName + " attribute");
				}
				if (lastPageNode.getNodeType() != JsonNodeType.BOOLEAN) {
					throw new Exception("Attribute " + lastPageAttributeName + " is not a boolean");
				}
				done = lastPageNode.asBoolean();
			} else {
				// Check result size
				done = (values.size() == 0);
			}
			List<T> list = new ArrayList<>();
			values.forEach(t -> {
				try {
					T obj = reader.readValue(t);
					list.add(obj);
				} catch (IOException e) {
					throw new RuntimeException(
							"Unable to parse JSON into " + objectClass.getCanonicalName() + ": " + t, e);
				}
			});
			pageIndex += list.size();
			result.addAll(list);
		}
		return result;
	}

	/**
	 * Invoke REST API
	 * 
	 * @param client          Jersey2 client
	 * @param path            URI
	 * @param method          Method as string
	 * @param acceptedTypes   String[]
	 * @param headers         MultivaluedMap<String, Object>
	 * @param queryParameters Map<String, List<Object>>
	 * @param dataType        String, valid for POST and PUT
	 * @param data            Object, valid for POST and PUT
	 * @return Response
	 * @throws Exception
	 */
	private static Response restCall(Client client, URI path, String method, MultivaluedMap<String, Object> headers,
			Map<String, Object> queryParameters, Object data) throws Exception {
		WebTarget target = client.target(path);
		if (queryParameters != null) {
			for (Map.Entry<String, Object> entry : queryParameters.entrySet()) {
				if (entry.getValue() instanceof Collection) {
					Collection<?> list = (Collection<?>) entry.getValue();
					target = target.queryParam(entry.getKey(), list.toArray());
				} else if (entry.getValue().getClass().isArray()) {
					Object[] list = (Object[]) entry.getValue();
					target = target.queryParam(entry.getKey(), list);
				} else {
					target = target.queryParam(entry.getKey(), entry.getValue());
				}
			}
		}
		Builder builder = target.request(MediaType.APPLICATION_JSON);
		if (headers != null) {
			builder = builder.headers(headers);
		}
		if (HttpMethod.DELETE.equals(method)) {
			return builder.delete();
		} else if (HttpMethod.GET.equals(method)) {
			return builder.get();
		} else if (HttpMethod.HEAD.equals(method)) {
			return builder.head();
		} else if (HttpMethod.OPTIONS.equals(method)) {
			return builder.options();
		} else if (HttpMethod.POST.equals(method)) {
			return builder.post(Entity.entity(data, MediaType.APPLICATION_JSON));
		} else if (HttpMethod.PUT.equals(method)) {
			return builder.put(Entity.entity(data, MediaType.APPLICATION_JSON));
		} else {
			return builder.method(method, Entity.entity(data, MediaType.APPLICATION_JSON));
		}
	}

	private static boolean checkStatusCode(Response resp, Response.Status check) {
		if ((resp.getStatus() & check.getStatusCode()) == check.getStatusCode()) {
			return true;
		}
		return false;
	}

	private static List<Project> getCloudProjects(Client client, Config conf) throws Exception {
		List<Project> result = new ArrayList<>();
		int startAt = 0;
		int maxResults = 50;
		boolean isLast = false;
		do {
			Map<String, Object> queryParameters = new HashMap<>();
			queryParameters.put("startAt", startAt);
			queryParameters.put("maxResults", maxResults);
			Response resp = restCall(client,
					new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/project/search"), HttpMethod.GET,
					null, queryParameters, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				SearchResult<Project> searchResult = resp.readEntity(new GenericType<SearchResult<Project>>() {
				});
				isLast = searchResult.getIsLast();
				result.addAll(searchResult.getValues());
				startAt += searchResult.getMaxResults();
			} else {
				throw new Exception(resp.readEntity(String.class));
			}
		} while (!isLast);
		return result;
	}

	private static List<Project> getServerProjects(Client client, Config conf) throws Exception {
		List<Project> result = new ArrayList<>();
		Response resp = restCall(client, new URI(conf.getSourceRESTBaseURL()).resolve("rest/api/latest/project"),
				HttpMethod.GET, null, null, null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			List<Project> searchResult = resp.readEntity(new GenericType<List<Project>>() {
			});
			result.addAll(searchResult);
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
		return result;
	}

	private static List<CustomField> getCustomFields(Client client, String baseURL) throws Exception {
		List<CustomField> result = new ArrayList<>();
		Response resp = restCall(client, new URI(baseURL).resolve("rest/api/latest/field"), HttpMethod.GET, null, null,
				null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			List<CustomField> searchResult = resp.readEntity(new GenericType<List<CustomField>>() {
			});
			for (CustomField cf : searchResult) {
				if (cf.isCustom()) {
					result.add(cf);
				}
			}
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
		return result;
	}

	private static List<User> getServerUsers(Client client, Config conf) throws Exception {
		List<User> result = new ArrayList<>();
		int startAt = 0;
		do {
			Map<String, Object> queryParameters = new HashMap<>();
			queryParameters.put("username", ".");
			queryParameters.put("startAt", startAt);
			queryParameters.put("includeActive", true);
			queryParameters.put("includeInactive", true);
			Response resp = restCall(client,
					new URI(conf.getSourceRESTBaseURL()).resolve("rest/api/latest/user/search"), HttpMethod.GET, null,
					queryParameters, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				List<User> searchResult = resp.readEntity(new GenericType<List<User>>() {
				});
				if (searchResult.size() != 0) {
					result.addAll(searchResult);
					startAt += searchResult.size();
				} else {
					break;
				}
			} else {
				throw new Exception(resp.readEntity(String.class));
			}
		} while (true);
		return result;
	}

	private static List<User> getCloudUsers(Client client, Config conf) throws Exception {
		List<User> result = new ArrayList<>();
		int startAt = 0;
		do {
			Map<String, Object> queryParameters = new HashMap<>();
			queryParameters.put("query", ".");
			queryParameters.put("startAt", startAt);
			queryParameters.put("includeActive", true);
			queryParameters.put("includeInactive", true);
			Response resp = restCall(client,
					new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/users/search"), HttpMethod.GET, null,
					queryParameters, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				List<User> searchResult = resp.readEntity(new GenericType<List<User>>() {
				});
				if (searchResult.size() != 0) {
					result.addAll(searchResult);
					startAt += searchResult.size();
				} else {
					break;
				}
			} else {
				throw new Exception(resp.readEntity(String.class));
			}
		} while (true);
		return result;
	}

	private static List<Status> getCloudStatuses(Client client, String baseURL) throws Exception {
		List<Status> result = restCallWithPaging(
				client, new URI(baseURL).resolve("/rest/api/3/statuses/search"), HttpMethod.GET, 
				null, null, null, 
				Status.class);
		return result;
	}

	private static List<Status> getStatuses(Client client, String baseURL) throws Exception {
		List<Status> result = new ArrayList<>();
		Response resp = restCall(client, new URI(baseURL).resolve("rest/api/latest/status"), HttpMethod.GET, null, null,
				null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			List<Status> searchResult = resp.readEntity(new GenericType<List<Status>>() {
			});
			result.addAll(searchResult);
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
		return result;
	}
	
	private static List<Role> getRoles(Client client, String baseURL) throws Exception {
		List<Role> result = new ArrayList<>();
		Response resp = restCall(client, new URI(baseURL).resolve("rest/api/latest/role"), HttpMethod.GET, null, null,
				null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			List<Role> searchResult = resp.readEntity(new GenericType<List<Role>>() {
			});
			result.addAll(searchResult);
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
		return result;
	}

	private static List<Group> getGroups(Client client, String baseURL) throws Exception {
		List<Group> result = new ArrayList<>();
		Response resp = restCall(client, new URI(baseURL).resolve("rest/api/latest/groups/picker"), HttpMethod.GET,
				null, null, null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			GroupPickerResult searchResult = resp.readEntity(GroupPickerResult.class);
			result.addAll(searchResult.getGroups());
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
		return result;
	}

	private static Config parseConfig(String[] args) {
		Config result = null;
		ObjectReader reader = OM.readerFor(Config.class);
		if (args.length == 2) {
			try (FileReader fr = new FileReader(args[0])) {
				result = reader.readValue(fr);
			} catch (IOException ioex) {
				ioex.printStackTrace();
			}
			Operation o = Operation.parse(args[1]);
			result.setOperation(o);
		}
		return result;
	}

	private static void printHelp() {
		Log.info(LOGGER, "java -jar JiraDashboardMigrator.jar com.igsl.DashboardMigrator <Config File> <Operation>");
		Log.info(LOGGER, "Config file content: ");
		ObjectWriter writer = OM.writerFor(Config.class);
		try {
			Log.info(LOGGER, writer.writeValueAsString(new Config()));
		} catch (JsonProcessingException ex) {
			Log.error(LOGGER, "Error converting Config to JSON", ex);
		}
		Log.info(LOGGER, "Operation: ");
		for (Operation o : Operation.values()) {
			Log.info(LOGGER, o.toString());
		}
	}

	private static <T> T readFile(DataFile file, Class<? extends T> cls) throws IOException, JsonParseException {
		ObjectReader reader = OM.readerFor(cls);
		StringBuilder sb = new StringBuilder();
		for (String line : Files.readAllLines(Paths.get(file.toString()))) {
			sb.append(line).append(NEWLINE);
		}
		return reader.readValue(sb.toString());
	}

	private static <T> List<T> readValuesFromFile(DataFile file, Class<? extends T> cls) 
			throws IOException, JsonParseException {
		ObjectReader reader = OM.readerFor(cls);
		StringBuilder sb = new StringBuilder();
		for (String line : Files.readAllLines(Paths.get(file.toString()))) {
			sb.append(line).append(NEWLINE);
		}
		List<T> result = new ArrayList<>();
		MappingIterator<T> list = reader.readValues(sb.toString());
		while (list.hasNext()) {
			result.add(list.next());
		}
		return result;
	}

	private static void saveFile(DataFile file, Object content) throws IOException {
		try (FileWriter fw = new FileWriter(file.toString())) {
			fw.write(OM.writeValueAsString(content));
		}
		Log.info(LOGGER, "File " + file.toString() + " saved");
	}
	
	private static void dumpDCFilterDashboard(FilterMapper filterMapper, Client dataCenterClient, Config conf) 
			throws Exception {
		Log.info(LOGGER, "Dumping filters and dashboards from Data Center...");
		Log.info(LOGGER, "Processing Filters...");
		List<Integer> filters = filterMapper.getFilters();
		List<DataCenterFilter> filterList = new ArrayList<>();
		for (Integer id : filters) {
			DataCenterFilter filter = getFilter(dataCenterClient, conf.getSourceRESTBaseURL(), id);
			filterList.add(filter);
		}
		Log.info(LOGGER, "Filters found: " + filterList.size());
		saveFile(DataFile.FILTER_DATACENTER, filterList);
		
		Log.info(LOGGER, "Processing Dashboards...");
		List<DataCenterPortalPage> dashboards = filterMapper.getDashboards();
		Log.info(LOGGER, "Dashboards found: " + dashboards.size());
		saveFile(DataFile.DASHBOARD_DATACENTER, dashboards);
	}

	private static void dumpDC(Client dataCenterClient, Config conf) throws Exception {
		Log.info(LOGGER, "Dumping data from Data Center...");
		
		Log.info(LOGGER, "Processing Projects...");
		List<Project> serverProjects = getServerProjects(dataCenterClient, conf);
		Log.info(LOGGER, "Projects found: " + serverProjects.size());
		saveFile(DataFile.PROJECT_DATACENTER, serverProjects);
		
		Log.info(LOGGER, "Processing Users...");
		List<User> serverUsers = getServerUsers(dataCenterClient, conf);
		Log.info(LOGGER, "Users found: " + serverUsers.size());
		saveFile(DataFile.USER_DATACENTER, serverUsers);
		
		Log.info(LOGGER, "Processing Custom Fields...");
		List<CustomField> serverFields = getCustomFields(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Custom fields found: " + serverFields.size());
		saveFile(DataFile.FIELD_DATACENTER, serverFields);
		
		Log.info(LOGGER, "Processing Roles...");
		List<Role> serverRoles = getRoles(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Roles found: " + serverRoles.size());
		saveFile(DataFile.ROLE_DATACENTER, serverRoles);
		
		Log.info(LOGGER, "Processing Statuses...");
		List<Status> serverStatus = getStatuses(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Statuses found: " + serverStatus.size());
		saveFile(DataFile.STATUS_DATACENTER, serverStatus);
		
		Log.info(LOGGER, "Processing Groups...");
		List<Group> serverGroups = getGroups(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Groups found: " + serverGroups.size());
		saveFile(DataFile.GROUP_DATACENTER, serverGroups);
	}
	
	private static void dumpCloud(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Dumping data from Cloud...");
		
		Log.info(LOGGER, "Processing Projects...");
		List<Project> cloudProjects = getCloudProjects(cloudClient, conf);
		Log.info(LOGGER, "Projects found: " + cloudProjects.size());
		saveFile(DataFile.PROJECT_CLOUD, cloudProjects);
		
		Log.info(LOGGER, "Processing Users...");
		List<User> cloudUsers = getCloudUsers(cloudClient, conf);
		Log.info(LOGGER, "Users found: " + cloudUsers.size());
		saveFile(DataFile.USER_CLOUD, cloudUsers);
		
		Log.info(LOGGER, "Processing Custom Fields...");
		List<CustomField> cloudFields = getCustomFields(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Custom Fields found: " + cloudFields.size());
		saveFile(DataFile.FIELD_CLOUD, cloudFields);
		
		Log.info(LOGGER, "Processing Roles...");
		List<Role> cloudRoles = getRoles(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Roles found: " + cloudRoles.size());
		saveFile(DataFile.ROLE_CLOUD, cloudRoles);
		
		Log.info(LOGGER, "Processing Statuses...");
		List<Status> cloudStatus = getCloudStatuses(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Statuses found: " + cloudStatus.size());
		saveFile(DataFile.STATUS_CLOUD, cloudStatus);
		
		Log.info(LOGGER, "Processing Groups...");
		List<Group> cloudGroups = getGroups(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Groups found: " + cloudGroups.size());
		saveFile(DataFile.GROUP_CLOUD, cloudGroups);
	}
	
	private static void mapObjects() throws Exception {
		Log.info(LOGGER, "Mapping objects between Data Center and Cloud...");
		mapProjects();
		mapUsers();
		mapCustomFields();
		mapRoles();
		mapStatuses();
		mapGroups();
	}
	
	private static void createDashboards(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Creating dashboards...");
		List<DataCenterPortalPage> dashboards = readValuesFromFile(DataFile.DASHBOARD_REMAPPED,
				DataCenterPortalPage.class);
		// Create dashboard mapping along the way
		Mapping migratedList = new Mapping(MappingType.DASHBOARD);
		int migratedCount = 0;
		for (DataCenterPortalPage dashboard : dashboards) {
			// Create dashboard
			CloudDashboard cd = CloudDashboard.create(dashboard);
			Log.info(LOGGER, "CloudDashboard: " + OM.writeValueAsString(cd));
			Response resp = restCall(cloudClient,
					new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/dashboard"), HttpMethod.POST, null,
					null, cd);
			if (checkStatusCode(resp, Response.Status.OK)) {
				CloudDashboard createdDashboard = resp.readEntity(CloudDashboard.class);
				// Sort portlets with position
				dashboard.getPortlets().sort(new GadgetOrderComparator(true));
				// Impossible to change layout via REST?
				for (DataCenterPortletConfiguration gadget : dashboard.getPortlets()) {
					// Add gadgets
					CloudGadget cg = CloudGadget.create(gadget);
					Log.info(LOGGER, "DC Gadget: " + OM.writeValueAsString(gadget));
					Log.info(LOGGER, "Cloud Gadget: " + OM.writeValueAsString(cg));
					Response resp1 = restCall(cloudClient,
							new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/dashboard/")
									.resolve(createdDashboard.getId() + "/").resolve("gadget"),
							HttpMethod.POST, null, null, cg);
					if (checkStatusCode(resp1, Response.Status.OK)) {
						CloudGadget createdGadget = resp1.readEntity(CloudGadget.class);
						// Gadget configuration
						CloudGadgetConfiguration cc = CloudGadgetConfiguration.create(
								gadget.getGadgetConfigurations());
						Response resp2;
						GadgetType gadgetType = GadgetType.parse(
								gadget.getDashboardCompleteKey(), 
								gadget.getGadgetXml());
						if (gadgetType != null) {
							GadgetConfigType configType = gadgetType.getConfigType();
							switch (configType) {
							case CONFIG: 
								// Add all properties under propertyKey as JSON
								resp2 = restCall(cloudClient,
										new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/dashboard/")
												.resolve(createdDashboard.getId() + "/").resolve("items/")
												.resolve(createdGadget.getId() + "/").resolve("properties/")
												.resolve(GadgetConfigType.CONFIG.getPropertyKey()),
										HttpMethod.PUT, null, null, cc);
								if (!checkStatusCode(resp2, Response.Status.OK)) {
									Log.error(LOGGER, "Failed to config for gadget [" + gadget.getGadgetXml() + 
											"] in dashboard ["
											+ dashboard.getPageName() + "]: " + resp2.readEntity(String.class));
								}
								break;
							case SEPARATE: 
								// Add property one by one
								for (Map.Entry<String, String> entry : cc.entrySet()) {
									Log.info(LOGGER, "Config: [" + entry.getKey() + "] = [" + entry.getValue() + "]");
									resp2 = restCall(cloudClient,
											new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/dashboard/")
													.resolve(createdDashboard.getId() + "/").resolve("items/")
													.resolve(createdGadget.getId() + "/").resolve("properties/")
													.resolve(entry.getKey()),
											HttpMethod.PUT, null, null, entry.getValue());
									if (!checkStatusCode(resp2, Response.Status.OK)) {
										Log.error(LOGGER, "Failed to config for gadget [" + gadget.getGadgetXml() + 
												"] in dashboard ["
												+ dashboard.getPageName() + "]: " + resp2.readEntity(String.class));
									}
								}
								break;
							}
						} else {
							Log.warn(LOGGER, "Unrecognized gadget [" + gadget.getDashboardCompleteKey() + ", " + 
									gadget.getGadgetXml() + "] in dashboard [" + 
									dashboard.getPageName() + "]");
						}
					} else {
						Log.error(LOGGER, "Failed to add gadget [" + gadget.getGadgetXml() + "] to dashboard ["
								+ dashboard.getPageName() + "]: " + resp1.readEntity(String.class));
					}
				}
				// Change owner
				// There's no REST API to change owner?!!
				Log.warn(LOGGER, "Please change owner of [" + createdDashboard.getName() + "] to ["
						+ dashboard.getUserDisplayName() + "]");
				migratedList.getMapped().put(Integer.toString(dashboard.getId()), createdDashboard.getId());
				migratedCount++;
			} else {
				String msg = resp.readEntity(String.class);
				migratedList.getFailed().put(Integer.toString(dashboard.getId()), msg);
				Log.error(LOGGER, "Failed to create dashboard [" + dashboard.getPageName() + "]: " + msg);
			}
		}
		saveFile(DataFile.DASHBOARD_MIGRATED, migratedList);
		Log.printCount(LOGGER, "Dashboards migrated: ", migratedCount, dashboards.size());
	}
	
	private static void createFilters(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Creating filters...");
		List<DataCenterFilter> filters = readValuesFromFile(DataFile.FILTER_REMAPPED, DataCenterFilter.class);
		// Create filter mapping along the way
		Mapping migratedList = new Mapping(MappingType.FILTER);
		int migratedCount = 0;
		for (DataCenterFilter filter : filters) {
			CloudFilter cf = CloudFilter.create(filter);
			Response resp = restCall(cloudClient,
					new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/filter"), 
					HttpMethod.POST, null, null,
					cf);
			if (checkStatusCode(resp, Response.Status.OK)) {
				CloudFilter newFilter = resp.readEntity(CloudFilter.class);
				// Change owner
				PermissionTarget owner = new PermissionTarget();
				owner.setAccountId(filter.getOwner().getAccountId());
				Response resp2 = restCall(cloudClient, new URI(conf.getTargetRESTBaseURL())
						.resolve("rest/api/latest/filter/").resolve(newFilter.getId() + "/").resolve("owner"),
						HttpMethod.PUT, null, null, owner);
				if (!checkStatusCode(resp2, Response.Status.NO_CONTENT)) {
					Log.error(LOGGER, "Failed to set owner for filter [" + filter.getName() + "]: "
							+ resp2.readEntity(String.class));
				}
				// TODO Change permissions	
				migratedList.getMapped().put(filter.getId(), newFilter.getId());
				migratedCount++;
			} else {
				String msg = resp.readEntity(String.class);
				migratedList.getFailed().put(filter.getId(), msg);
				Log.error(LOGGER, "Failed to create filter [" + filter.getName() + "]: " + msg);
			}
		}
		saveFile(DataFile.FILTER_MIGRATED, migratedList);
		Log.printCount(LOGGER, "Filters migrated: ", migratedCount, filters.size());
	}
	
	private static void mapFilters() throws Exception {
		Log.info(LOGGER, "Processing Filters...");
		List<DataCenterFilter> filters = readValuesFromFile(DataFile.FILTER_DATACENTER, DataCenterFilter.class);
		Mapping userMapping = readFile(DataFile.USER_MAP, Mapping.class);
		Mapping projectMapping = readFile(DataFile.PROJECT_MAP, Mapping.class);
		Mapping roleMapping = readFile(DataFile.ROLE_MAP, Mapping.class);
		Mapping groupMapping = readFile(DataFile.GROUP_MAP, Mapping.class);
		Mapping fieldMapping = readFile(DataFile.FIELD_MAP, Mapping.class);
		Map<String, Mapping> maps = new HashMap<>();
		maps.put("project", projectMapping);
		maps.put("field", fieldMapping);
		int successCount = 0;
		for (DataCenterFilter filter : filters) {
			boolean hasError = false;
			// Translate owner
			if (userMapping.getMapped().containsKey(filter.getOwner().getName())) {
				filter.getOwner().setAccountId(userMapping.getMapped().get(filter.getOwner().getName()));
			} else {
				hasError = true;
				Log.error(LOGGER, "Filter [" + filter.getName() + "] owner [" + filter.getOwner().getName()
						+ "] cannot be mapped");
			}
			// Translate permissions
			for (DataCenterPermission permission : filter.getSharePermissions()) {
				PermissionType type = PermissionType.parse(permission.getType());
				if (type == PermissionType.USER) {
					if (userMapping.getMapped().containsKey(permission.getUser().getId())) {
						permission.getUser()
								.setAccountId(userMapping.getMapped().get(permission.getUser().getName()));
					} else {
						hasError = true;
						Log.error(LOGGER, "Filter [" + filter.getName() + "] user [" + permission.getUser().getName()
								+ "] (" + permission.getUser().getDisplayName() + ") cannot be mapped");
					}
				} else if (type == PermissionType.GROUP) {
					if (groupMapping.getMapped().containsKey(permission.getGroup().getName())) {
						permission.getGroup()
								.setGroupId(groupMapping.getMapped().get(permission.getGroup().getName()));
					} else {
						hasError = true;
						Log.error(LOGGER, "Filter [" + filter.getName() + "] group [" + permission.getGroup().getName()
								+ "] cannot be mapped");
					}
				} else if (type == PermissionType.PROJECT) {
					if (projectMapping.getMapped().containsKey(permission.getProject().getId())) {
						permission.getProject()
								.setId(projectMapping.getMapped().get(permission.getProject().getId()));
					} else {
						hasError = true;
						Log.error(LOGGER, "Filter [" + filter.getName() + "] project [" + permission.getProject().getId()
								+ "] (" + permission.getProject().getName() + ") cannot be mapped");
					}
					if (permission.getRole() != null) {
						permission.setType(PermissionType.PROJECT_ROLE.toString());
						if (roleMapping.getMapped().containsKey(permission.getRole().getId())) {
							permission.getRole().setId(roleMapping.getMapped().get(permission.getRole().getId()));
						} else {
							hasError = true;
							Log.error(LOGGER, "Filter [" + filter.getName() + "] role [" + permission.getRole().getId()
									+ "] (" + permission.getRole().getName() + ") cannot be mapped");
						}
					}
				}
			}
			// Translate JQL
			String jql = filter.getJql();
			// Log.info(LOGGER, "Filter [" + filter.getName() + "] JQL: [" + jql + "]");
			JqlLexer lexer = new JqlLexer((CharStream) new ANTLRStringStream(jql));
			CommonTokenStream cts = new CommonTokenStream(lexer);
			JqlParser parser = new JqlParser(cts);
			query_return qr = parser.query();
			Clause clone = cloneClause(filter.getName(), maps, qr.clause);
			OrderBy orderClone = null;
			if (qr.order != null) {
				List<SearchSort> sortList = new ArrayList<>();
				for (SearchSort ss : qr.order.getSearchSorts()) {
					String newColumn = mapCustomFieldName(fieldMapping.getMapped(), ss.getField());
					SearchSort newSS = new SearchSort(newColumn, ss.getProperty(), ss.getSortOrder());
					sortList.add(newSS);
					// Log.warn(LOGGER, "Mapped sort column for filter [" + filter.getName() + "] column
					// [" + ss.getField() + "] => [" + newColumn + "]");
				}
				orderClone = new OrderByImpl(sortList);
			}
			// Log.info(LOGGER, "Updated JQL for filter [" + filter.getName() + "]: [" + clone +
			// ((orderClone != null)? " " + orderClone : "") + "]");
			filter.setJql(clone + ((orderClone != null) ? " " + orderClone : ""));
			if (!hasError) {
				successCount++;
			}
		}
		saveFile(DataFile.FILTER_REMAPPED, filters);
		Log.printCount(LOGGER, "Filters mapped: ", successCount, filters.size());
	}
	
	private static void mapDashboards() throws Exception {
		Log.info(LOGGER, "Processing Dashboards...");
		List<DataCenterPortalPage> dashboards = readValuesFromFile(DataFile.DASHBOARD_DATACENTER, 
				DataCenterPortalPage.class);
		Mapping projectMapping = readFile(DataFile.PROJECT_MAP, Mapping.class);
		Mapping roleMapping = readFile(DataFile.ROLE_MAP, Mapping.class);
		Mapping userMapping = readFile(DataFile.USER_MAP, Mapping.class);
		Mapping groupMapping = readFile(DataFile.GROUP_MAP, Mapping.class);
		Mapping fieldMapping = readFile(DataFile.FIELD_MAP, Mapping.class);
		Mapping filterMapping = readFile(DataFile.FILTER_MIGRATED, Mapping.class);
		// Dashboards uses user KEY instead of name.
		List<User> userDC = readValuesFromFile(DataFile.USER_DATACENTER, User.class);
		int errorCount = 0;
		for (DataCenterPortalPage dashboard : dashboards) {
			// Translate owner, if any
			if (dashboard.getUsername() != null) {
				if (userMapping.getMapped().containsKey(dashboard.getUsername())) {
					dashboard.setAccountId(userMapping.getMapped().get(dashboard.getUsername()));
				} else {
					errorCount++;
					Log.warn(LOGGER, "Unable to map owner for dashboard [" + dashboard.getPageName() + "] owner ["
							+ dashboard.getUsername() + "]");
				}
			}
			// Translate permissions
			for (DataCenterPortalPermission permission : dashboard.getPermissions()) {
				PermissionType type = PermissionType.parse(permission.getShareType());
				switch (type) {
				case USER: 
					// DC Dashboard permission stores user with key instead of name
					String userKey = permission.getParam1();
					for (User u : userDC) {
						if (u.getKey().equals(userKey)) {
							if (userMapping.getMapped().containsKey(u.getName())) {
								String accountId = userMapping.getMapped().get(u.getName());
								permission.setParam1(accountId);
							}
							break;
						}
					}
					break;
				case PROJECT:
					String projectId = permission.getParam1();
					String projectRoleId = permission.getParam2();
					if (projectMapping.getMapped().containsKey(projectId)) {
						String newId = projectMapping.getMapped().get(projectId);
						permission.setParam1(newId);
					}
					if (roleMapping.getMapped().containsKey(projectRoleId)) {
						String newId = roleMapping.getMapped().get(projectRoleId);
						permission.setParam2(newId);
					}
					break;
				default:
					break;
				}
			}
			dashboard.getPortlets().sort(new GadgetOrderComparator(true));
			int cursorY = 0;
			int cursorX = 0;
			int maxY = 0;
			int maxX = 0;
			for (DataCenterPortletConfiguration gadget : dashboard.getPortlets()) {
				// Save cursor position
				cursorY = gadget.getPositionSeq();
				cursorX = gadget.getColumnNumber();
				// If max is exceeded, calculate offset
				if (cursorY > maxY + 1) {
					gadget.setPositionSeq(maxY + 1);
				}
				if (cursorX > maxX + 1) {
					gadget.setColumnNumber(maxX + 1);
				}
				// Save max
				maxY = Math.max(maxY, cursorY);
				maxX = Math.max(maxX, cursorX);
			}
			// Fix configuration values
			for (DataCenterPortletConfiguration gadget : dashboard.getPortlets()) {
				CloudGadgetConfigurationMapper.mapConfiguration(gadget, projectMapping, roleMapping, fieldMapping,
						groupMapping, userMapping, filterMapping);
			}
			dashboard.getPortlets().sort(new GadgetOrderComparator(false));
		}
		saveFile(DataFile.DASHBOARD_REMAPPED, dashboards);
		Log.printCount(LOGGER, "Dashboards mapped: ", dashboards.size() - errorCount, dashboards.size());
		Log.info(LOGGER, "Please manually translate references");
	}
	
	private static void mapProjects() throws Exception {
		Log.info(LOGGER, "Processing Projects...");
		int mappedProjectCount = 0;
		List<Project> serverProjects = readValuesFromFile(DataFile.PROJECT_DATACENTER, Project.class);
		List<Project> cloudProjects = readValuesFromFile(DataFile.PROJECT_CLOUD, Project.class);
		Mapping projectMapping = new Mapping(MappingType.PROJECT);
		for (Project src : serverProjects) {
			List<String> targets = new ArrayList<>();
			for (Project target : cloudProjects) {
				if (target.getKey().equals(src.getKey()) && target.getProjectTypeKey().equals(src.getProjectTypeKey())
						&& target.getName().equals(src.getName())) {
					targets.add(Integer.toString(target.getId()));
				}
			}
			switch (targets.size()) {
			case 0:
				projectMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Project [" + src.getName() + "] is not mapped");
				break;
			case 1:
				projectMapping.getMapped().put(Integer.toString(src.getId()), targets.get(0));
				mappedProjectCount++;
				break;
			default:
				projectMapping.getConflict().put(Integer.toString(src.getId()), targets);
				Log.warn(LOGGER, "Project [" + src.getName() + "] is mapped to multiple Cloud projects");
				break;
			}
		}
		Log.printCount(LOGGER, "Projects mapped: ", mappedProjectCount, serverProjects.size());
		saveFile(DataFile.PROJECT_MAP, projectMapping);
	}
	
	
	private static void mapUsers() throws Exception {
		Log.info(LOGGER, "Processing Users...");
		int mappedUserCount = 0;
		List<User> serverUsers = readValuesFromFile(DataFile.USER_DATACENTER, User.class);
		List<User> cloudUsers = readValuesFromFile(DataFile.USER_CLOUD, User.class);
		Mapping userMapping = new Mapping(MappingType.USER);
		Comparator<String> nullFirstCompare = Comparator.nullsFirst(String::compareTo);
		for (User src : serverUsers) {
			List<String> targets = new ArrayList<>();
			for (User target : cloudUsers) {
				// Migrated user names got changed... compare case-insensitively, remove all
				// space, check both name and display name against Cloud display name
				// Email should be the best condition, but cannot be retrieved from REST API
				// unless approved by Atlassian
				String srcDisplayName = src.getDisplayName().toLowerCase().replaceAll("\\s", "");
				String srcName = src.getName().toLowerCase().replaceAll("\\s", "");
				String targetDisplayName = target.getDisplayName().toLowerCase().replaceAll("\\s", "");
				if (nullFirstCompare.compare(srcDisplayName, targetDisplayName) == 0
						|| nullFirstCompare.compare(srcName, targetDisplayName) == 0) {
					targets.add(target.getAccountId());
				}
			}
			switch (targets.size()) {
			case 0:
				userMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "User [" + src.getName() + "] is not mapped");
				break;
			case 1:
				userMapping.getMapped().put(src.getName(), targets.get(0));
				mappedUserCount++;
				break;
			default:
				userMapping.getConflict().put(src.getName(), targets);
				Log.warn(LOGGER, "User [" + src.getName() + "] is mapped to multiple Cloud users");
				break;
			}
		}
		Log.printCount(LOGGER, "Users mapped: ", mappedUserCount, serverUsers.size());
		saveFile(DataFile.USER_MAP, userMapping);
	}
	
	private static void mapCustomFields() throws Exception {
		int mappedFieldCount = 0;
		Log.info(LOGGER, "Processing Custom Fields...");
		List<CustomField> serverFields = readValuesFromFile(DataFile.FIELD_DATACENTER, CustomField.class);
		List<CustomField> cloudFields = readValuesFromFile(DataFile.FIELD_CLOUD, CustomField.class);
		Mapping fieldMapping = new Mapping(MappingType.CUSTOM_FIELD);
		for (CustomField src : serverFields) {
			List<String> targets = new ArrayList<>();
			List<String> migratedTargets = new ArrayList<>();
			for (CustomField target : cloudFields) {
				if (target.getSchema().compareTo(src.getSchema()) == 0) {
					if (target.getName().equals(src.getName())) {
						targets.add(target.getId());
					}
				}
				if (target.getName().equals(src.getName() + " (migrated)")) {
					migratedTargets.add(target.getId());
				}
			}
			switch (migratedTargets.size()) {
			case 1:
				fieldMapping.getMapped().put(src.getId(), migratedTargets.get(0));
				mappedFieldCount++;
				break;
			case 0:
				// Fallback to targets
				switch (targets.size()) {
				case 0:
					fieldMapping.getUnmapped().add(src);
					Log.warn(LOGGER, "Custom Field [" + src.getName() + "] is not mapped");
					break;
				case 1:
					fieldMapping.getMapped().put(src.getId(), targets.get(0));
					mappedFieldCount++;
					break;
				default:
					fieldMapping.getConflict().put(src.getId(), targets);
					Log.warn(LOGGER, "Custom Field [" + src.getName() + "] is mapped to multiple Cloud fields");
					break;
				}
				break;
			default:
				List<String> list = new ArrayList<>();
				list.addAll(migratedTargets);
				list.addAll(targets);
				fieldMapping.getConflict().put(src.getId(), list);
				Log.error(LOGGER, "Custom Field [" + src.getName() + "] is mapped to multiple Cloud fields");
				break;
			}
		}
		Log.printCount(LOGGER, "Custom Fields mapped: ", mappedFieldCount, serverFields.size());
		saveFile(DataFile.FIELD_MAP, fieldMapping);
	}
	
	private static void mapStatuses() throws Exception {
		Log.info(LOGGER, "Processing Statuses...");
		int mappedStatusCount = 0;
		List<Role> serverStatuses = readValuesFromFile(DataFile.STATUS_DATACENTER, Role.class);
		List<Role> cloudStatuses = readValuesFromFile(DataFile.STATUS_CLOUD, Role.class);
		Mapping statusMapping = new Mapping(MappingType.ROLE);
		Comparator<String> comparator = Comparator.nullsFirst(Comparator.naturalOrder());
		for (Role src : serverStatuses) {
			List<String> targets = new ArrayList<>();
			for (Role target : cloudStatuses) {
				// Don't compare description, only name
				if (comparator.compare(target.getName(), src.getName()) == 0) {
					targets.add(Integer.toString(target.getId()));
				}
			}
			switch (targets.size()) {
			case 0:
				statusMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Status [" + src.getName() + "] is not mapped");
				break;
			case 1:
				statusMapping.getMapped().put(Integer.toString(src.getId()), targets.get(0));
				mappedStatusCount++;
				break;
			default:
				statusMapping.getConflict().put(Integer.toString(src.getId()), targets);
				Log.warn(LOGGER, "Status [" + src.getName() + "] is mapped to multiple Cloud statuses");
				break;
			}
		}
		Log.printCount(LOGGER, "Statuses mapped: ", mappedStatusCount, serverStatuses.size());
		saveFile(DataFile.STATUS_MAP, statusMapping);
	}
	
	private static void mapRoles() throws Exception {
		Log.info(LOGGER, "Processing Roles...");
		int mappedRoleCount = 0;
		List<Role> serverRoles = readValuesFromFile(DataFile.ROLE_DATACENTER, Role.class);
		List<Role> cloudRoles = readValuesFromFile(DataFile.ROLE_CLOUD, Role.class);
		Mapping roleMapping = new Mapping(MappingType.ROLE);
		Comparator<String> comparator = Comparator.nullsFirst(Comparator.naturalOrder());
		for (Role src : serverRoles) {
			List<String> targets = new ArrayList<>();
			for (Role target : cloudRoles) {
				// Don't compare description, only name
				if (comparator.compare(target.getName(), src.getName()) == 0) {
					targets.add(Integer.toString(target.getId()));
				}
			}
			switch (targets.size()) {
			case 0:
				roleMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Role [" + src.getName() + "] is not mapped");
				break;
			case 1:
				roleMapping.getMapped().put(Integer.toString(src.getId()), targets.get(0));
				mappedRoleCount++;
				break;
			default:
				roleMapping.getConflict().put(Integer.toString(src.getId()), targets);
				Log.warn(LOGGER, "Role [" + src.getName() + "] is mapped to multiple Cloud roles");
				break;
			}
		}
		Log.printCount(LOGGER, "Roles mapped: ", mappedRoleCount, serverRoles.size());
		saveFile(DataFile.ROLE_MAP, roleMapping);
	}
	
	
	private static void mapGroups() throws Exception {
		Log.info(LOGGER, "Processing Groups...");
		int mappedGroupCount = 0;
		List<Group> serverGroups = readValuesFromFile(DataFile.GROUP_DATACENTER, Group.class);
		List<Group> cloudGroups = readValuesFromFile(DataFile.GROUP_CLOUD, Group.class);
		Mapping groupMapping = new Mapping(MappingType.GROUP);
		for (Group src : serverGroups) {
			List<String> targets = new ArrayList<>();
			for (Group target : cloudGroups) {
				if (target.getHtml().equals(src.getHtml()) && target.getName().equals(src.getName())) {
					targets.add(target.getGroupId());
				}
			}
			switch (targets.size()) {
			case 0:
				groupMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Group [" + src.getName() + "] is not mapped");
				break;
			case 1:
				groupMapping.getMapped().put(src.getName(), targets.get(0));
				mappedGroupCount++;
				break;
			default:
				groupMapping.getConflict().put(src.getName(), targets);
				Log.warn(LOGGER, "Group [" + src.getName() + "] is mapped to multiple Cloud groups");
				break;
			}
		}
		Log.printCount(LOGGER, "Groups mapped: ", mappedGroupCount, serverGroups.size());
		saveFile(DataFile.GROUP_MAP, groupMapping);
	}
	
	private static DataCenterFilter getFilter(Client client, String baseURL, int id) throws Exception {
		URI uri = new URI(baseURL).resolve("rest/api/latest/filter/").resolve(Integer.toString(id));
		Response resp = restCall(client, uri, HttpMethod.GET, null, null, null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			DataCenterFilter filter = resp.readEntity(DataCenterFilter.class);
			filter.setOriginalJql(filter.getJql());
			return filter;
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
	}

	private static void listFilter(Client cloudClient, Config conf)
			throws Exception {
		Log.info(LOGGER, "List filters from Cloud...");
		URI uri = new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/filter/search");
		Map<String, String> result = new HashMap<>();
		int startAt = 0;
		int maxResults = 50;
		boolean isLast = false;
		do {
			Map<String, Object> queryParameters = new HashMap<>();
			queryParameters.put("startAt", startAt);
			queryParameters.put("maxResults", maxResults);
			Response resp = restCall(cloudClient, uri, HttpMethod.GET, null, queryParameters, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				SearchResult<Filter> searchResult = resp.readEntity(new GenericType<SearchResult<Filter>>() {
				});
				isLast = searchResult.getIsLast();
				for (Filter f : searchResult.getValues()) {
					result.put(f.getId(), f.getId());
				}
				startAt += searchResult.getMaxResults();
			} else {
				throw new Exception(resp.readEntity(String.class));
			}
		} while (!isLast);
		saveFile(DataFile.FILTER_LIST, result);
		Log.info(LOGGER, "Filters found: " + result.size());
		Log.info(LOGGER, "Filter list completed");
	}

	private static void listDashboard(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "List dashboards from Cloud...");
		URI uri = new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/dashboard");
		Map<String, String> result = new HashMap<>();
		Response resp = restCall(cloudClient, uri, HttpMethod.GET, null, null, null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			DashboardSearchResult searchResult = resp.readEntity(DashboardSearchResult.class);
			for (Dashboard d : searchResult.getDashboards()) {
				result.put(d.getId(), d.getId());
			}
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
		saveFile(DataFile.DASHBOARD_LIST, result);
		Log.info(LOGGER, "Dashboards found: " + result.size());
		Log.info(LOGGER, "Dashboard list completed");
	}

	private static void deleteFilter(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Deleting migrated filters...");
		Mapping filters = readFile(DataFile.FILTER_MIGRATED, Mapping.class);
		int deletedCount = 0;
		for (Map.Entry<String, String> filter : filters.getMapped().entrySet()) {
			Response resp = restCall(cloudClient,
					new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/filter/").resolve(filter.getValue()),
					HttpMethod.DELETE, null, null, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				deletedCount++;
			} else {
				Log.error(LOGGER, "Failed to delete filter [" + filter.getKey() + "] (" + filter.getValue() + "): "
						+ resp.readEntity(String.class));
			}
		}
		Log.printCount(LOGGER, "Filters deleted: ", deletedCount, filters.getMapped().size());
		Log.info(LOGGER, "Delete filter completed");
	}

	private static void deleteDashboard(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Deleting migrated dashboards...");
		Mapping dashboards = readFile(DataFile.DASHBOARD_MIGRATED, Mapping.class);
		int deletedCount = 0;
		for (Map.Entry<String, String> dashboard : dashboards.getMapped().entrySet()) {
			Response resp = restCall(cloudClient, new URI(conf.getTargetRESTBaseURL())
					.resolve("rest/api/latest/dashboard/").resolve(dashboard.getValue()), HttpMethod.DELETE, null, null,
					null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				deletedCount++;
			} else {
				Log.error(LOGGER, "Failed to delete dashboard [" + dashboard.getKey() + "] (" + dashboard.getValue() + "): "
						+ resp.readEntity(String.class));
			}
		}
		Log.printCount(LOGGER, "Dashboards deleted: ", deletedCount, dashboards.getMapped().size());
		Log.info(LOGGER, "Delete dashboard completed");
	}

	/**
	 * A subclass to remove curly brackets added by TerminalClauseImpl.toString().
	 */
	public static class MyTerminalClause extends TerminalClauseImpl {
		private static final long serialVersionUID = 1L;
		public MyTerminalClause(String name, Operator op, Operand value) {
			super(name, op, value);
		}
		@Override
		public String toString() {
			String s = super.toString();
			// Remove curly brackets added by TerminalClauseImpl
			if (s.startsWith("{") && s.endsWith("}")) {
				return s.substring(1, s.length() - 1);
			}
			return s;
		}
	}

	private static SingleValueOperand cloneValue(SingleValueOperand src, Map<String, String> map, String filterName,
			String propertyName) {
		SingleValueOperand result = null;
		if (src != null) {
			String key = null;
			if (src.getLongValue() != null) {
				key = Long.toString(src.getLongValue());
			} else {
				key = src.getStringValue();
			}
			if (map != null && map.containsKey(key)) {
				if (src.getLongValue() != null) {
					Long newValue = Long.valueOf(map.get(Long.toString(src.getLongValue())));
					// Log.info(LOGGER, "Mapped value for filter [" + filterName + "] type [" +
					// propertyName + "] value [" + src.getLongValue() + "] => [" + newValue + "]");
					result = new SingleValueOperand(newValue);
				} else {
					String newValue = map.get(src.getStringValue());
					// Log.info(LOGGER, "Mapped value forfilter [" + filterName + "] type [" +
					// propertyName + "] value [" + src.getStringValue() + "] => [" + newValue +
					// "]");
					result = new SingleValueOperand(newValue);
				}
			} else {
				if (src.getLongValue() != null) {
					Log.warn(LOGGER, "Unable to map value for filter [" + filterName + "] type [" + propertyName
							+ "] value [" + src.getLongValue() + "]");
					result = new SingleValueOperand(src.getLongValue());
				} else {
					Log.warn(LOGGER, "Unable to map value for filter [" + filterName + "] type [" + propertyName
							+ "] value [" + src.getStringValue() + "]");
					result = new SingleValueOperand(src.getStringValue());
				}
			}
		}
		return result;
	}

	private static final Pattern CUSTOM_FIELD_CF = Pattern.compile("(cf\\[)([0-9]+)(\\])");
	private static final String CUSTOM_FIELD = "customfield_";

	private static String mapCustomFieldName(Map<String, String> map, String data) {
		// If data is customfield_#
		if (map.containsKey(data)) {
			return map.get(data);
		}
		// If data is cf[#]
		Matcher m = CUSTOM_FIELD_CF.matcher(data);
		if (m.matches()) {
			if (map.containsKey(CUSTOM_FIELD + m.group(2))) {
				String s = map.get(CUSTOM_FIELD + m.group(2));
				s = s.substring(CUSTOM_FIELD.length());
				return "cf[" + s + "]";
			}
		}
		if (data.contains(" ")) {
			return "\"" + data + "\"";
		} else {
			return data;
		}
	}

	private static Clause cloneClause(String filterName, Map<String, Mapping> maps, Clause c) {
		Clause clone = null;
		Map<String, String> propertyMap = maps.get("field").getMapped();
		List<Clause> clonedChildren = new ArrayList<>();
		if (c != null) {
			// Log.debug(LOGGER, "Clause: " + c + ", " + c.getClass());
			// Check name
			String propertyName = mapCustomFieldName(propertyMap, c.getName());
			for (Clause sc : c.getClauses()) {
				Clause clonedChild = cloneClause(filterName, maps, sc);
				clonedChildren.add(clonedChild);
			}
			if (c instanceof AndClause) {
				clone = new AndClause(clonedChildren.toArray(new Clause[0]));
			} else if (c instanceof OrClause) {
				clone = new OrClause(clonedChildren.toArray(new Clause[0]));
			} else if (c instanceof NotClause) {
				clone = new NotClause(clonedChildren.get(0));
			} else if (c instanceof TerminalClause) {
				TerminalClause tc = (TerminalClause) c;
				// if (maps.containsKey(tc.getName())) {
				Map<String, String> targetMap = null;
				if (maps.containsKey(tc.getName())) {
					targetMap = maps.get(tc.getName()).getMapped();
				}
				// Modify values
				Operand o = tc.getOperand();
				Operand clonedO = null;
				if (o instanceof SingleValueOperand) {
					SingleValueOperand svo = (SingleValueOperand) o;
					// Change value
					clonedO = cloneValue(svo, targetMap, filterName, tc.getName());
				} else if (o instanceof MultiValueOperand) {
					MultiValueOperand mvo = (MultiValueOperand) o;
					List<Operand> list = new ArrayList<>();
					for (Operand item : mvo.getValues()) {
						if (item instanceof SingleValueOperand) {
							// Change value
							SingleValueOperand svo = (SingleValueOperand) item;
							list.add(cloneValue(svo, targetMap, filterName, tc.getName()));
						} else {
							list.add(item);
						}
					}
					clonedO = new MultiValueOperand(list);
				} else if (o instanceof FunctionOperand) {
					// TODO membersOf to map group
					FunctionOperand fo = (FunctionOperand) o;
					List<String> args = new ArrayList<>();
					for (String s : fo.getArgs()) {
						args.add("\"" + s + "\"");
					}
					// Log.debug(LOGGER, "args: [" + GSON.toJson(args) + "]");
					clonedO = new FunctionOperand(fo.getName(), args);
				} else if (o instanceof EmptyOperand) {
					clonedO = o;
				} else {
					Log.warn(LOGGER, "Unrecognized Operand class for filter [" + filterName + "] class [" + o.getClass()
							+ "], reusing reference");
					clonedO = o;
				}
				// Use cloned operand
				clone = new MyTerminalClause(propertyName, tc.getOperator(), clonedO);
//				} else {
//					// Use original operand
//					clone = new MyTerminalClause(propertyName, tc.getOperator(), tc.getOperand());
//				}				
			} else if (c instanceof WasClause) {
				WasClause wc = (WasClause) c;
				clone = new WasClauseImpl(propertyName, wc.getOperator(), wc.getOperand(), wc.getPredicate());
			} else if (c instanceof ChangedClause) {
				ChangedClause cc = (ChangedClause) c;
				clone = new ChangedClauseImpl(propertyName, cc.getOperator(), cc.getPredicate());
			} else {
				Log.warn(LOGGER, "Unrecognized Clause class for filter [" + filterName + "] class [" + c.getClass()
						+ "], reusing reference");
				clone = c;
			}
		} else {
			Log.warn(LOGGER, "Clause: null");
		}
		return clone;
	}
	
	public static void main(String[] args) {
		AnsiConsole.systemInstall();
		try {
			// Parse config
			Config conf = parseConfig(args);
			if (conf == null || conf.getOperation() == null) {
				printHelp();
				return;
			}
			switch (conf.getOperation()) {
			case DUMP_DATACENTER: 
				try (ClientWrapper wrapper = new ClientWrapper(false, conf)) {
					// Get filter info from source
					dumpDC(wrapper.getClient(), conf);
				}
				break;
			case DUMP_DATACENTER_FILTER_DASHBOARD: 
				{
					SqlSessionFactory sqlSessionFactory = setupMyBatis(conf);
					try (	SqlSession session = sqlSessionFactory.openSession();
							ClientWrapper wrapper = new ClientWrapper(false, conf)) {
						// Get filter info from source
						FilterMapper filterMapper = session.getMapper(FilterMapper.class);
						dumpDCFilterDashboard(filterMapper, wrapper.getClient(), conf);
					}
				}
				break;
			case DUMP_CLOUD:
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					dumpCloud(wrapper.getClient(), conf);
				}
				break;
			case MAP_OBJECT:
				mapObjects();
				break;
			case MAP_FILTER: 
				mapFilters();
				break;
			case CREATE_FILTER: 
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					createFilters(wrapper.getClient(), conf);
				}
				break;
			case MAP_DASHBOARD:
				mapDashboards();
				break;
			case CREATE_DASHBOARD: 
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					createDashboards(wrapper.getClient(), conf);
				}
				break;
			case DELETE_FILTER:
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					deleteFilter(wrapper.getClient(), conf);
				}
				break;
			case LIST_FILTER:
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					listFilter(wrapper.getClient(), conf);
				}
				break;
			case DELETE_DASHBOARD:
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					deleteDashboard(wrapper.getClient(), conf);
				}
				break;
			case LIST_DASHBOARD:
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					listDashboard(wrapper.getClient(), conf);
				}
				break;
			}
		} catch (Exception ex) {
			LOGGER.fatal("Exception: " + ex.getMessage(), ex);
		}
		AnsiConsole.systemUninstall();
	}
}
