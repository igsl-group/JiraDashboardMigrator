package com.igsl;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.igsl.CLI.CLIOptions;
import com.igsl.config.Config;
import com.igsl.config.GadgetConfigType;
import com.igsl.config.GadgetType;
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
import com.igsl.model.mapping.AgileBoard;
import com.igsl.model.mapping.AgileBoardConfig;
import com.igsl.model.mapping.CustomField;
import com.igsl.model.mapping.Dashboard;
import com.igsl.model.mapping.DashboardSearchResult;
import com.igsl.model.mapping.Filter;
import com.igsl.model.mapping.Group;
import com.igsl.model.mapping.GroupPickerResult;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;
import com.igsl.model.mapping.Project;
import com.igsl.model.mapping.ProjectCategory;
import com.igsl.model.mapping.ProjectComponent;
import com.igsl.model.mapping.Role;
import com.igsl.model.mapping.SearchResult;
import com.igsl.model.mapping.Sprint;
import com.igsl.model.mapping.Status;
import com.igsl.model.mapping.User;
import com.igsl.model.mapping.UserPicker;
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

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
	
	private static final String SCRUM = "scrum";	// AgileBoard type that allows Sprint
	
	private static final String NEWLINE = "\r\n";
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
	 * @param valuesAttributeName 	Name of attribute containing array of values. Usually "values". 
	 * 								If null, the root is an array of values. lastPageAttributeName should be null.
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
			JsonNode values;
			if (valuesAttributeName != null) {
				values = root.get(valuesAttributeName);
			} else {
				values = root;
			}
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
		List<Project> result = restCallWithPaging(
				client, 
				new URI(conf.getTargetRESTBaseURL()).resolve("rest/api/latest/project/search"), 
				HttpMethod.GET, 
				null, null, null,
				Project.class);	
		return result;
	}

	private static List<ProjectComponent> getServerProjectComponents(
			Client client, String baseURL, List<Project> projects) throws Exception {
		List<ProjectComponent> result = new ArrayList<>();
		for (Project p : projects) {
			Response resp = restCall(
					client, 
					new URI(baseURL).resolve("/rest/api/latest/project/").resolve(p.getId() + "/").resolve("components"), 
					HttpMethod.GET, 
					null, null, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				List<ProjectComponent> searchResult = resp.readEntity(new GenericType<List<ProjectComponent>>(){});
				result.addAll(searchResult);
			} else {
				throw new Exception(resp.readEntity(String.class));
			}
		}
		return result;
	}

	private static List<ProjectComponent> getCloudProjectComponents(
			Client client, String baseURL, List<Project> projects) throws Exception {
		List<ProjectComponent> result = new ArrayList<>();
		for (Project p : projects) {
			List<ProjectComponent> list = restCallWithPaging(
					client, 
					new URI(baseURL).resolve("/rest/api/latest/project/").resolve(p.getId() + "/").resolve("component"), 
					HttpMethod.GET, 
					null, null, null,
					ProjectComponent.class);
			result.addAll(list);
		}
		return result;
	}

	private static List<ProjectCategory> getServerProjectCategories(Client client, String baseURL) throws Exception {
		List<ProjectCategory> result = new ArrayList<>();
		Response resp = restCall(
				client, new URI(baseURL).resolve("/rest/api/latest/projectCategory"), HttpMethod.GET, 
				null, null, null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			List<ProjectCategory> searchResult = resp.readEntity(new GenericType<List<ProjectCategory>>(){});
			result.addAll(searchResult);
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
		return result;
	}
	
	private static List<ProjectCategory> getCloudProjectCategories(Client client, String baseURL) throws Exception {
		List<ProjectCategory> result = new ArrayList<>();
		Response resp = restCall(
				client, new URI(baseURL).resolve("..").resolve("/rest/api/latest/projectCategory"), HttpMethod.GET, 
				null, null, null);
		if (checkStatusCode(resp, Response.Status.OK)) {
			List<ProjectCategory> searchResult = resp.readEntity(new GenericType<List<ProjectCategory>>() {
			});
			result.addAll(searchResult);
		} else {
			throw new Exception(resp.readEntity(String.class));
		}
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
	
	private static List<AgileBoard> getCloudAgileBoards(Client client, String baseURL) throws Exception {
		List<AgileBoard> result = restCallWithPaging(
				client, new URI(baseURL).resolve("..").resolve("rest/agile/1.0/board"), HttpMethod.GET,
				null, null, null,
				AgileBoard.class);
		// Get associated filter name
		for (AgileBoard board : result) {
			Response resp = restCall(
					client, 
					new URI(baseURL)
						.resolve("rest/agile/1.0/board/")
						.resolve(board.getId() + "/")
						.resolve("configuration"),
					HttpMethod.GET,
					null, null, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				AgileBoardConfig config = resp.readEntity(AgileBoardConfig.class);
				Response resp2 = restCall(
						client, 
						new URI(baseURL)
							.resolve("/rest/api/2/filter/")
							.resolve(config.getFilter().getId()),
						HttpMethod.GET, 
						null, null, null);
				if (checkStatusCode(resp, Response.Status.OK)) {
					Filter f = resp2.readEntity(Filter.class);
					board.setFilterName(f.getName());
				} else {
					Log.error(
							LOGGER, 
							"Unable to retrieve filter for AgileBoard " + 
							board.getName() + " (" + board.getId() + "): " + 
							resp2.readEntity(String.class));
				}
			} else {
				Log.error(
						LOGGER, 
						"Unable to retrieve filter for AgileBoard " + 
						board.getName() + " (" + board.getId() + "): " + 
						resp.readEntity(String.class));
			}
		}
		return result;
	}
	
	private static List<AgileBoard> getAgileBoards(Client client, String baseURL) throws Exception {
		List<AgileBoard> result = restCallWithPaging(
				client, new URI(baseURL).resolve("rest/agile/1.0/board"), HttpMethod.GET,
				null, null, null,
				AgileBoard.class);
		// Get associated filter name
		for (AgileBoard board : result) {
			Response resp = restCall(
					client, 
					new URI(baseURL)
						.resolve("rest/agile/1.0/board/")
						.resolve(board.getId() + "/")
						.resolve("configuration"),
					HttpMethod.GET,
					null, null, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				AgileBoardConfig config = resp.readEntity(AgileBoardConfig.class);
				Response resp2 = restCall(
						client, 
						new URI(baseURL)
							.resolve("/rest/api/latest/filter/")
							.resolve(config.getFilter().getId()),
						HttpMethod.GET, 
						null, null, null);
				if (checkStatusCode(resp, Response.Status.OK)) {
					Filter f = resp2.readEntity(Filter.class);
					board.setFilterName(f.getName());
				} else {
					Log.error(
							LOGGER, 
							"Unable to retrieve filter for AgileBoard " + 
							board.getName() + " (" + board.getId() + "): " + 
							resp2.readEntity(String.class));
				}
			} else {
				Log.error(
						LOGGER, 
						"Unable to retrieve filter for AgileBoard " + 
						board.getName() + " (" + board.getId() + "): " + 
						resp.readEntity(String.class));
			}
		}
		return result;
	}
	
	private static List<Sprint> getCloudSprints(
			Client client, String baseURL, List<AgileBoard> boards) throws Exception {
		Map<String, Sprint> result = new HashMap<>();
		for (AgileBoard board : boards) {
			if (SCRUM.equals(board.getType())) {
				List<Sprint> list =	restCallWithPaging(
					client, 
					new URI(baseURL)
						.resolve("../rest/agile/1.0/board/")
						.resolve(board.getId() + "/")
						.resolve("sprint"), 
					HttpMethod.GET,
					null, null, null,
					Sprint.class);
				for (Sprint sp : list) {
					result.put(sp.getId(), sp);
				}
			}
		}
		// Set originalBoardName and originalBoardFilterName
		for (Sprint sprint : result.values()) {
			String boardId = sprint.getOriginBoardId();
			if (boardId != null) {
				for (AgileBoard board : boards) {
					if (boardId.equals(board.getId())) {
						sprint.setOriginalBoardName(board.getName());
						sprint.setOriginalBoardFilterName(board.getFilterName());
						break;
					}
				}
			}
		}
		List<Sprint> list = new ArrayList<>();
		list.addAll(result.values());
		return list;
	}

	private static List<Sprint> getSprints(
			Client client, String baseURL, List<AgileBoard> boards) throws Exception {
		Map<String, Sprint> result = new HashMap<>();
		for (AgileBoard board : boards) {
			if (SCRUM.equals(board.getType())) {
				List<Sprint> list =	restCallWithPaging(
					client, 
					new URI(baseURL)
						.resolve("rest/agile/1.0/board/")
						.resolve(board.getId() + "/")
						.resolve("sprint"), 
					HttpMethod.GET,
					null, null, null,
					Sprint.class);
				for (Sprint sp : list) {
					result.put(sp.getId(), sp);
				}
			}
		}
		// Set originalBoardName and originalBoardFilterName
		for (Sprint sprint : result.values()) {
			String boardId = sprint.getOriginBoardId();
			if (boardId != null) {
				for (AgileBoard board : boards) {
					if (boardId.equals(board.getId())) {
						sprint.setOriginalBoardName(board.getName());
						sprint.setOriginalBoardFilterName(board.getFilterName());
						break;
					}
				}
			}
		}
		List<Sprint> list = new ArrayList<>();
		list.addAll(result.values());
		return list;
	}
	
	private static Config parseConfig(CommandLine cli) {
		Config result = null;
		String configFile = cli.getOptionValue(CLI.CONFIG_OPTION);
		ObjectReader reader = OM.readerFor(Config.class);
		try (FileReader fr = new FileReader(configFile)) {
			result = reader.readValue(fr);
		} catch (IOException ioex) {
			Log.error(LOGGER, "Unable to read configuration file [" + configFile + "]", ioex);
		}
		return result;
	}

	private static <T> T readFile(String fileName, Class<? extends T> cls) throws IOException, JsonParseException {
		ObjectReader reader = OM.readerFor(cls);
		StringBuilder sb = new StringBuilder();
		for (String line : Files.readAllLines(Paths.get(fileName), DEFAULT_CHARSET)) {
			sb.append(line).append(NEWLINE);
		}
		return reader.readValue(sb.toString());
	}

	private static <T> List<T> readValuesFromFile(String fileName, Class<? extends T> cls) 
			throws IOException, JsonParseException {
		ObjectReader reader = OM.readerFor(cls);
		StringBuilder sb = new StringBuilder();
		for (String line : Files.readAllLines(Paths.get(fileName), DEFAULT_CHARSET)) {
			sb.append(line).append(NEWLINE);
		}
		List<T> result = new ArrayList<>();
		MappingIterator<T> list = reader.readValues(sb.toString());
		while (list.hasNext()) {
			result.add(list.next());
		}
		return result;
	}

	private static void saveFile(String fileName, Object content) throws IOException {
		try (FileWriter fw = new FileWriter(fileName, DEFAULT_CHARSET)) {
			ObjectWriter writer = OM.writer(new DefaultPrettyPrinter()
						.withObjectIndenter(
								new DefaultIndenter().withLinefeed(NEWLINE)));
			fw.write(writer.writeValueAsString(content));
		}
		Log.info(LOGGER, "File " + fileName + " saved");
	}
	
	private static void dumpDashboard(FilterMapper filterMapper, Client dataCenterClient, Config conf) 
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
		saveFile(MappingType.FILTER.getDC(), filterList);
		
		Log.info(LOGGER, "Processing Dashboards...");
		List<DataCenterPortalPage> dashboards = filterMapper.getDashboards();
		Log.info(LOGGER, "Dashboards found: " + dashboards.size());
		// Sort gadget configuration
		for (DataCenterPortalPage dashboard : dashboards) {
			for (DataCenterPortletConfiguration config : dashboard.getPortlets()) {
				config.getGadgetConfigurations().sort(new GadgetConfigurationComparator());
			}
		}
		saveFile(MappingType.DASHBOARD.getDC(), dashboards);
	}

	private static void dumpDC(Client dataCenterClient, Config conf) throws Exception {
		Log.info(LOGGER, "Dumping data from Data Center...");
		
		Log.info(LOGGER, "Processing Projects...");
		List<Project> serverProjects = getServerProjects(dataCenterClient, conf);
		Log.info(LOGGER, "Projects found: " + serverProjects.size());
		saveFile(MappingType.PROJECT.getDC(), serverProjects);
		
		Log.info(LOGGER, "Processing Project Categories...");
		List<ProjectCategory> serverProjectCategories = getServerProjectCategories(
				dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Project categories found: " + serverProjectCategories.size());
		saveFile(MappingType.PROJECT_CATEGORY.getDC(), serverProjectCategories);
		
		Log.info(LOGGER, "Processing Project Components...");
		List<ProjectComponent> serverProjectComponents = getServerProjectComponents(
				dataCenterClient, conf.getSourceRESTBaseURL(), serverProjects);
		Log.info(LOGGER, "Project components found: " + serverProjectComponents.size());
		saveFile(MappingType.PROJECT_COMPONENT.getDC(), serverProjectComponents);
		
		Log.info(LOGGER, "Processing Users...");
		List<User> serverUsers = getServerUsers(dataCenterClient, conf);
		Log.info(LOGGER, "Users found: " + serverUsers.size());
		saveFile(MappingType.USER.getDC(), serverUsers);
		
		Log.info(LOGGER, "Processing Custom Fields...");
		List<CustomField> serverFields = getCustomFields(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Custom fields found: " + serverFields.size());
		saveFile(MappingType.CUSTOM_FIELD.getDC(), serverFields);
		
		Log.info(LOGGER, "Processing Roles...");
		List<Role> serverRoles = getRoles(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Roles found: " + serverRoles.size());
		saveFile(MappingType.ROLE.getDC(), serverRoles);
		
		Log.info(LOGGER, "Processing Statuses...");
		List<Status> serverStatus = getStatuses(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Statuses found: " + serverStatus.size());
		saveFile(MappingType.STATUS.getDC(), serverStatus);
		
		Log.info(LOGGER, "Processing Groups...");
		List<Group> serverGroups = getGroups(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Groups found: " + serverGroups.size());
		saveFile(MappingType.GROUP.getDC(), serverGroups);
		
		Log.info(LOGGER, "Processing Agile Boards...");
		List<AgileBoard> serverAgile = getAgileBoards(dataCenterClient, conf.getSourceRESTBaseURL());
		Log.info(LOGGER, "Agile boards found: " + serverAgile.size());
		saveFile(MappingType.AGILE_BOARD.getDC(), serverAgile);
		
		Log.info(LOGGER, "Processing Sprints...");
		List<Sprint> serverSprints = getSprints(dataCenterClient, conf.getSourceRESTBaseURL(), serverAgile);
		Log.info(LOGGER, "Sprints found: " + serverSprints.size());
		saveFile(MappingType.SPRINT.getDC(), serverSprints);
	}
	
	private static void dumpCloud(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Dumping data from Cloud...");
		
		Log.info(LOGGER, "Processing Projects...");
		List<Project> cloudProjects = getCloudProjects(cloudClient, conf);
		Log.info(LOGGER, "Projects found: " + cloudProjects.size());
		saveFile(MappingType.PROJECT.getCloud(), cloudProjects);
		
		Log.info(LOGGER, "Processing Project Categories...");
		List<ProjectCategory> cloudProjectCategories = getCloudProjectCategories(
				cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Project categories found: " + cloudProjectCategories.size());
		saveFile(MappingType.PROJECT_CATEGORY.getCloud(), cloudProjectCategories);
		
		Log.info(LOGGER, "Processing Project Components...");
		List<ProjectComponent> cloudProjectComponents = getCloudProjectComponents(
				cloudClient, conf.getTargetRESTBaseURL(), cloudProjects);
		Log.info(LOGGER, "Project components found: " + cloudProjectComponents.size());
		saveFile(MappingType.PROJECT_COMPONENT.getCloud(), cloudProjectComponents);
		
		Log.info(LOGGER, "Processing Users...");
		List<User> cloudUsers = getCloudUsers(cloudClient, conf);
		Log.info(LOGGER, "Users found: " + cloudUsers.size());
		saveFile(MappingType.USER.getCloud(), cloudUsers);
		
		Log.info(LOGGER, "Processing Custom Fields...");
		List<CustomField> cloudFields = getCustomFields(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Custom Fields found: " + cloudFields.size());
		saveFile(MappingType.CUSTOM_FIELD.getCloud(), cloudFields);
		
		Log.info(LOGGER, "Processing Roles...");
		List<Role> cloudRoles = getRoles(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Roles found: " + cloudRoles.size());
		saveFile(MappingType.ROLE.getCloud(), cloudRoles);
		
		Log.info(LOGGER, "Processing Statuses...");
		List<Status> cloudStatus = getCloudStatuses(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Statuses found: " + cloudStatus.size());
		saveFile(MappingType.STATUS.getCloud(), cloudStatus);
		
		Log.info(LOGGER, "Processing Groups...");
		List<Group> cloudGroups = getGroups(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Groups found: " + cloudGroups.size());
		saveFile(MappingType.GROUP.getCloud(), cloudGroups);
		
		Log.info(LOGGER, "Processing Agile Boards...");
		List<AgileBoard> cloudAgile = getCloudAgileBoards(cloudClient, conf.getTargetRESTBaseURL());
		Log.info(LOGGER, "Agile boards found: " + cloudAgile.size());
		saveFile(MappingType.AGILE_BOARD.getCloud(), cloudAgile);
		
		Log.info(LOGGER, "Processing Sprints...");
		List<Sprint> cloudSprints = getCloudSprints(cloudClient, conf.getTargetRESTBaseURL(), cloudAgile);
		Log.info(LOGGER, "Sprints found: " + cloudSprints.size());
		saveFile(MappingType.SPRINT.getCloud(), cloudSprints);
	}
	
	private static void mapObjects() throws Exception {
		Log.info(LOGGER, "Mapping objects between Data Center and Cloud...");
		mapProjects();
		mapProjectCategories();
		mapProjectComponents();
		mapUsers();
		mapCustomFields();
		mapRoles();
		mapStatuses();
		mapGroups();
		mapAgileBoards();
		mapSprints();
	}
	
	private static void createDashboards(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Creating dashboards...");
		List<DataCenterPortalPage> dashboards = readValuesFromFile(MappingType.DASHBOARD.getRemapped(),
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
		saveFile(MappingType.DASHBOARD.getMap(), migratedList);
		Log.printCount(LOGGER, "Dashboards migrated: ", migratedCount, dashboards.size());
	}
	
	private static void createFilters(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Creating filters...");
		List<DataCenterFilter> filters = readValuesFromFile(MappingType.FILTER.getRemapped(), 
				DataCenterFilter.class);
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
		saveFile(MappingType.FILTER.getMap(), migratedList);
		Log.printCount(LOGGER, "Filters migrated: ", migratedCount, filters.size());
	}
	
	private static Map<MappingType, Mapping> readMappings() throws Exception {
		Map<MappingType, Mapping> result = new HashMap<>();
		for (MappingType type : MappingType.values()) {
			// TODO Skip some types
			Mapping map = readFile(type.getMap(), Mapping.class);
			result.put(type, map);
		}
		return result;
	}
	
	private static void createFiltersV2() throws Exception {
		// Check each filter for:
		// - Unmapped owner
		// - Unmapped share user
		// - Unmapped share group
		// - Unmapped custom field
		// - Unmapped project
		// - etc.
		// Everything should be mapped after mapObject.
		// If not, treat as error.
		
		// Calculate filter dependency, order filters into batches
		// Filter de-serializing
		// - Keep track of filters referenced
		// - Validate values
		// Map and create each batch
		// Filter serializing
		// - Some components are serialized with curly brackets

		Log.info(LOGGER, "Processing Filters...");
		List<DataCenterFilter> filters = readValuesFromFile(MappingType.FILTER.getDC(), 
				DataCenterFilter.class);

	}
	
	private static void mapFilters() throws Exception {
		Log.info(LOGGER, "Processing Filters...");
		List<DataCenterFilter> filters = readValuesFromFile(MappingType.FILTER.getDC(), 
				DataCenterFilter.class);
		Mapping userMapping = readFile(MappingType.USER.getMap(), Mapping.class);
		Mapping projectMapping = readFile(MappingType.PROJECT.getMap(), Mapping.class);
		Mapping roleMapping = readFile(MappingType.ROLE.getMap(), Mapping.class);
		Mapping groupMapping = readFile(MappingType.GROUP.getMap(), Mapping.class);
		Mapping fieldMapping = readFile(MappingType.CUSTOM_FIELD.getMap(), Mapping.class);
		Map<String, Mapping> maps = new HashMap<>();
		maps.put("project", projectMapping);
		maps.put("field", fieldMapping);
		int successCount = 0;
		for (DataCenterFilter filter : filters) {
			boolean hasError = false;
			// Translate owner
			if (userMapping.getMapped().containsKey(filter.getOwner().getKey())) {
				filter.getOwner().setAccountId(userMapping.getMapped().get(filter.getOwner().getKey()));
			} else {
				hasError = true;
				Log.error(LOGGER, "Filter [" + filter.getName() + "] owner [" + filter.getOwner().getKey()
						+ "] cannot be mapped");
			}
			// Translate permissions
			for (DataCenterPermission permission : filter.getSharePermissions()) {
				PermissionType type = PermissionType.parse(permission.getType());
				if (type == PermissionType.USER) {
					if (userMapping.getMapped().containsKey(permission.getUser().getId())) {
						permission.getUser()
								.setAccountId(userMapping.getMapped().get(permission.getUser().getKey()));
					} else {
						hasError = true;
						Log.error(LOGGER, "Filter [" + filter.getName() + "] user [" + permission.getUser().getKey()
								+ "] (" + permission.getUser().getDisplayName() + ") cannot be mapped");
					}
				} else if (type == PermissionType.GROUP) {
					if (groupMapping.getMapped().containsKey(permission.getGroup().getName())) {
						permission.getGroup()
								.setGroupId(groupMapping.getMapped().get(permission.getGroup().getKey()));
					} else {
						hasError = true;
						Log.error(LOGGER, "Filter [" + filter.getName() + "] group [" + permission.getGroup().getKey()
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
		saveFile(MappingType.FILTER.getRemapped(), filters);
		Log.printCount(LOGGER, "Filters mapped: ", successCount, filters.size());
	}
	
	private static Map<MappingType, Mapping> loadMappings(MappingType... excludes) throws Exception {
		List<MappingType> excludeList = new ArrayList<>();
		for (MappingType e : excludes) {
			excludeList.add(e);
		}
		Map<MappingType, Mapping> result = new HashMap<>();
		for (MappingType type : MappingType.values()) {
			if (!excludeList.contains(type)) {
				Mapping m = readFile(type.getMap(), Mapping.class);
				result.put(type, m);
			}
		}
		return result;
	}
	
	private static void mapDashboards() throws Exception {
		Log.info(LOGGER, "Processing Dashboards...");
		List<DataCenterPortalPage> dashboards = readValuesFromFile(MappingType.DASHBOARD.getDC(), 
				DataCenterPortalPage.class);
		Map<MappingType, Mapping> mappings = loadMappings(MappingType.DASHBOARD);
		Mapping userMapping = mappings.get(MappingType.USER);
		Mapping projectMapping = mappings.get(MappingType.PROJECT);
		Mapping roleMapping = mappings.get(MappingType.ROLE);
		// Dashboards uses user KEY instead of name.
		List<User> userDC = readValuesFromFile(MappingType.USER.getDC(), User.class);
		int errorCount = 0;
		for (DataCenterPortalPage dashboard : dashboards) {
			// Translate owner, if any
			if (dashboard.getUsername() != null) {
				if (userMapping.getMapped().containsKey(dashboard.getUsername())) {
					dashboard.setAccountId(userMapping.getMapped().get(dashboard.getUsername()));
				} else {
					errorCount++;
					Log.warn(LOGGER, "Unable to map owner for dashboard [" + dashboard.getPageName() + "] " + 
							"owner [" + dashboard.getUsername() + "]");
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
				CloudGadgetConfigurationMapper.mapConfiguration(gadget, mappings);
			}
			dashboard.getPortlets().sort(new GadgetOrderComparator(false));
		}
		saveFile(MappingType.DASHBOARD.getRemapped(), dashboards);
		Log.printCount(LOGGER, "Dashboards mapped: ", dashboards.size() - errorCount, dashboards.size());
		Log.info(LOGGER, "Please manually translate references");
	}
	
	private static void mapProjectComponents() throws Exception {
		Log.info(LOGGER, "Processing Project Components...");
		int mappedProjectComponentCount = 0;
		List<ProjectComponent> serverProjectComponents = readValuesFromFile(
				MappingType.PROJECT_COMPONENT.getDC(), ProjectComponent.class);
		List<ProjectComponent> cloudProjectComponents = readValuesFromFile(
				MappingType.PROJECT_COMPONENT.getCloud(), ProjectComponent.class);
		Mapping projectCategoriesMapping = new Mapping(MappingType.PROJECT_CATEGORY);
		for (ProjectComponent src : serverProjectComponents) {
			List<String> targets = new ArrayList<>();
			for (ProjectComponent target : cloudProjectComponents) {
				if (target.getName().equals(src.getName()) && 
					target.getProject().equals(src.getProject())) {
					targets.add(target.getId());
				}
			}
			switch (targets.size()) {
			case 0:
				projectCategoriesMapping.getUnmapped().add(src);
				Log.warn(LOGGER, 
						"Project Component [" + src.getName() + "] " + 
						"for Project [" + src.getProject() + "] is not mapped");
				break;
			case 1:
				projectCategoriesMapping.getMapped().put(src.getId(), targets.get(0));
				mappedProjectComponentCount++;
				break;
			default:
				projectCategoriesMapping.getConflict().put(src.getId(), targets);
				Log.warn(LOGGER, 
						"Project Component [" + src.getName() + "] " + 
						"for Project [" + src.getProject() + "] is mapped to multiple Cloud projects components");
				break;
			}
		}
		Log.printCount(LOGGER, "Projects components mapped: ", 
				mappedProjectComponentCount, serverProjectComponents.size());
		saveFile(MappingType.PROJECT_COMPONENT.getMap(), projectCategoriesMapping);
	}
	
	private static void mapProjectCategories() throws Exception {
		Log.info(LOGGER, "Processing Project Categories...");
		int mappedProjectCategoryCount = 0;
		List<ProjectCategory> serverProjectCategories = readValuesFromFile(
				MappingType.PROJECT_CATEGORY.getDC(), ProjectCategory.class);
		List<ProjectCategory> cloudProjectCategories = readValuesFromFile(
				MappingType.PROJECT_CATEGORY.getCloud(), ProjectCategory.class);
		Mapping projectCategoriesMapping = new Mapping(MappingType.PROJECT_CATEGORY);
		for (ProjectCategory src : serverProjectCategories) {
			List<String> targets = new ArrayList<>();
			for (ProjectCategory target : cloudProjectCategories) {
				if (target.getName().equals(src.getName())) {
					targets.add(target.getId());
				}
			}
			switch (targets.size()) {
			case 0:
				projectCategoriesMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Project Category [" + src.getName() + "] is not mapped");
				break;
			case 1:
				projectCategoriesMapping.getMapped().put(src.getId(), targets.get(0));
				mappedProjectCategoryCount++;
				break;
			default:
				projectCategoriesMapping.getConflict().put(src.getId(), targets);
				Log.warn(LOGGER, 
						"Project Category [" + src.getName() + "] is mapped to multiple Cloud projects categories");
				break;
			}
		}
		Log.printCount(LOGGER, "Projects categories mapped: ", 
				mappedProjectCategoryCount, serverProjectCategories.size());
		saveFile(MappingType.PROJECT_CATEGORY.getMap(), projectCategoriesMapping);
	}
	
	private static void mapProjects() throws Exception {
		Log.info(LOGGER, "Processing Projects...");
		int mappedProjectCount = 0;
		List<Project> serverProjects = readValuesFromFile(MappingType.PROJECT.getDC(), Project.class);
		List<Project> cloudProjects = readValuesFromFile(MappingType.PROJECT.getCloud(), Project.class);
		Mapping projectMapping = new Mapping(MappingType.PROJECT);
		for (Project src : serverProjects) {
			List<String> targets = new ArrayList<>();
			for (Project target : cloudProjects) {
				if (target.getKey().equals(src.getKey()) && 
					target.getProjectTypeKey().equals(src.getProjectTypeKey()) && 
					target.getName().equals(src.getName())) {
					targets.add(target.getId());
				}
			}
			switch (targets.size()) {
			case 0:
				projectMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Project [" + src.getName() + "] is not mapped");
				break;
			case 1:
				projectMapping.getMapped().put(src.getId(), targets.get(0));
				mappedProjectCount++;
				break;
			default:
				projectMapping.getConflict().put(src.getId(), targets);
				Log.warn(LOGGER, "Project [" + src.getName() + "] is mapped to multiple Cloud projects");
				break;
			}
		}
		Log.printCount(LOGGER, "Projects mapped: ", mappedProjectCount, serverProjects.size());
		saveFile(MappingType.PROJECT.getMap(), projectMapping);
	}
	
	private static void mapUsersWithCSV(String csvFile) throws Exception {
		// Parse CSV file, exact columns unknown, target is "User Id" and "email" columns only.
		final String COL_EMAIL = "email";
		final String COL_ACCOUNTID = "User id";
		List<User> serverUsers = readValuesFromFile(MappingType.USER.getDC(), User.class);
		Map<String, String> csvUsers = new HashMap<>();	// Key: Email, Value: Account ID
		CSVFormat format = CSVFormat.Builder.create()
				.setHeader()
				.setSkipHeaderRecord(false)
				.build();
		try (FileReader fr = new FileReader(csvFile); 
			CSVParser parser = CSVParser.parse(fr, format)) {
			List<String> colNames = parser.getHeaderNames();
			for (String colName : colNames) {
				Log.info(LOGGER, "CSV column: " + colName);
			}
			Integer accountIdCol = null;
			Integer emailCol = null;
			for (int i = 0; i < colNames.size(); i++) {
				String colName = colNames.get(i);
				if (COL_EMAIL.equals(colName)) {
					emailCol = i;
				} else if (COL_ACCOUNTID.equals(colName)) {
					accountIdCol = i;
				}
			}
			if (emailCol == null) {
				throw new Exception("CSV provided does not contain column " + COL_EMAIL);
			}
			if (accountIdCol == null) {
				throw new Exception("CSV provided does not contain column " + COL_ACCOUNTID);
			}
			for (CSVRecord row : parser.getRecords()) {
				String accountId = row.get(accountIdCol);
				String email = row.get(emailCol);
				csvUsers.put(email, accountId);
			}
		}
		Mapping userMapping = new Mapping(MappingType.USER);
		int mappedUserCount = 0;
		for (User src : serverUsers) {
			if (csvUsers.containsKey(src.getEmailAddress())) {
				userMapping.getMapped().put(src.getKey(), csvUsers.get(src.getEmailAddress()));
				mappedUserCount++;
			} else {
				userMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "User [" + src.getName() + "] is not mapped");
			}
		}
		Log.printCount(LOGGER, "Users mapped: ", mappedUserCount, serverUsers.size());
		saveFile(MappingType.USER.getMap(), userMapping);
	}
	
	private static void mapUsers() throws Exception {
		Log.info(LOGGER, "Processing Users...");
		int mappedUserCount = 0;
		List<User> serverUsers = readValuesFromFile(MappingType.USER.getDC(), User.class);
		List<User> cloudUsers = readValuesFromFile(MappingType.USER.getCloud(), User.class);
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
				Log.warn(LOGGER, "User [" + src.getKey() + "] is not mapped");
				break;
			case 1:
				userMapping.getMapped().put(src.getKey(), targets.get(0));
				mappedUserCount++;
				break;
			default:
				userMapping.getConflict().put(src.getKey(), targets);
				Log.warn(LOGGER, "User [" + src.getKey() + "] is mapped to multiple Cloud users");
				break;
			}
		}
		Log.printCount(LOGGER, "Users mapped: ", mappedUserCount, serverUsers.size());
		saveFile(MappingType.USER.getMap(), userMapping);
	}
	
	private static void mapCustomFields() throws Exception {
		int mappedFieldCount = 0;
		Log.info(LOGGER, "Processing Custom Fields...");
		List<CustomField> serverFields = readValuesFromFile(MappingType.CUSTOM_FIELD.getDC(), CustomField.class);
		List<CustomField> cloudFields = readValuesFromFile(MappingType.CUSTOM_FIELD.getCloud(), CustomField.class);
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
		saveFile(MappingType.CUSTOM_FIELD.getMap(), fieldMapping);
	}
	
	private static void mapStatuses() throws Exception {
		Log.info(LOGGER, "Processing Statuses...");
		int mappedStatusCount = 0;
		List<Role> serverStatuses = readValuesFromFile(MappingType.STATUS.getDC(), Role.class);
		List<Role> cloudStatuses = readValuesFromFile(MappingType.STATUS.getCloud(), Role.class);
		Mapping statusMapping = new Mapping(MappingType.ROLE);
		Comparator<String> comparator = Comparator.nullsFirst(Comparator.naturalOrder());
		for (Role src : serverStatuses) {
			List<String> targets = new ArrayList<>();
			for (Role target : cloudStatuses) {
				// Don't compare description, only name
				if (comparator.compare(target.getName(), src.getName()) == 0) {
					targets.add(target.getId());
				}
			}
			switch (targets.size()) {
			case 0:
				statusMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Status [" + src.getName() + "] is not mapped");
				break;
			case 1:
				statusMapping.getMapped().put(src.getId(), targets.get(0));
				mappedStatusCount++;
				break;
			default:
				statusMapping.getConflict().put(src.getId(), targets);
				Log.warn(LOGGER, "Status [" + src.getName() + "] is mapped to multiple Cloud statuses");
				break;
			}
		}
		Log.printCount(LOGGER, "Statuses mapped: ", mappedStatusCount, serverStatuses.size());
		saveFile(MappingType.STATUS.getMap(), statusMapping);
	}
	
	private static void mapRoles() throws Exception {
		Log.info(LOGGER, "Processing Roles...");
		int mappedRoleCount = 0;
		List<Role> serverRoles = readValuesFromFile(MappingType.ROLE.getDC(), Role.class);
		List<Role> cloudRoles = readValuesFromFile(MappingType.ROLE.getCloud(), Role.class);
		Mapping roleMapping = new Mapping(MappingType.ROLE);
		Comparator<String> comparator = Comparator.nullsFirst(Comparator.naturalOrder());
		for (Role src : serverRoles) {
			List<String> targets = new ArrayList<>();
			for (Role target : cloudRoles) {
				// Don't compare description, only name
				if (comparator.compare(target.getName(), src.getName()) == 0) {
					targets.add(target.getId());
				}
			}
			switch (targets.size()) {
			case 0:
				roleMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Role [" + src.getName() + "] is not mapped");
				break;
			case 1:
				roleMapping.getMapped().put(src.getId(), targets.get(0));
				mappedRoleCount++;
				break;
			default:
				roleMapping.getConflict().put(src.getId(), targets);
				Log.warn(LOGGER, "Role [" + src.getName() + "] is mapped to multiple Cloud roles");
				break;
			}
		}
		Log.printCount(LOGGER, "Roles mapped: ", mappedRoleCount, serverRoles.size());
		saveFile(MappingType.ROLE.getMap(), roleMapping);
	}
	
	private static void mapGroups() throws Exception {
		Log.info(LOGGER, "Processing Groups...");
		int mappedGroupCount = 0;
		List<Group> serverGroups = readValuesFromFile(MappingType.GROUP.getDC(), Group.class);
		List<Group> cloudGroups = readValuesFromFile(MappingType.GROUP.getCloud(), Group.class);
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
		saveFile(MappingType.GROUP.getMap(), groupMapping);
	}
	
	private static void mapAgileBoards() throws Exception {
		Log.info(LOGGER, "Processing Agile Boards...");
		int mappedCount = 0;
		List<AgileBoard> serverAgileBoards = readValuesFromFile(MappingType.AGILE_BOARD.getDC(), AgileBoard.class);
		List<AgileBoard> cloudAgileBoards = readValuesFromFile(MappingType.AGILE_BOARD.getCloud(), AgileBoard.class);
		Mapping agileMapping = new Mapping(MappingType.AGILE_BOARD);
		for (AgileBoard src : serverAgileBoards) {
			List<String> targets = new ArrayList<>();
			for (AgileBoard target : cloudAgileBoards) {
				if (target.getName().equals(src.getName()) && 
					target.getFilterName().equals(src.getFilterName())) {
					targets.add(target.getId());
				}
			}
			switch (targets.size()) {
			case 0:
				agileMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Agile Board [" + src.getName() + "] is not mapped");
				break;
			case 1:
				agileMapping.getMapped().put(src.getId(), targets.get(0));
				mappedCount++;
				break;
			default:
				agileMapping.getConflict().put(src.getId(), targets);
				Log.warn(LOGGER, "Agile Board [" + src.getName() + "] is mapped to multiple Cloud Agile Boards");
				break;
			}
		}
		Log.printCount(LOGGER, "Agile Boards mapped: ", mappedCount, serverAgileBoards.size());
		saveFile(MappingType.AGILE_BOARD.getMap(), agileMapping);
	}
	
	private static void mapSprints() throws Exception {
		Comparator<String> nullFirstCompare = Comparator.nullsFirst(String::compareTo);
		Log.info(LOGGER, "Processing Sprints...");
		int mappedCount = 0;
		List<Sprint> serverSprints = readValuesFromFile(MappingType.SPRINT.getDC(), Sprint.class);
		List<Sprint> cloudSprints = readValuesFromFile(MappingType.SPRINT.getCloud(), Sprint.class);
		Mapping sprintMapping = new Mapping(MappingType.SPRINT);
		for (Sprint src : serverSprints) {
			List<String> targets = new ArrayList<>();
			for (Sprint target : cloudSprints) {
				if (nullFirstCompare.compare(target.getName(), src.getName()) == 0 && 
					nullFirstCompare.compare(target.getOriginalBoardName(), src.getOriginalBoardName()) == 0 && 
					nullFirstCompare.compare(
							target.getOriginalBoardFilterName(), src.getOriginalBoardFilterName()) == 0) {
					targets.add(target.getId());
				}
			}
			switch (targets.size()) {
			case 0:
				sprintMapping.getUnmapped().add(src);
				Log.warn(LOGGER, "Sprint [" + src.getName() + "] is not mapped");
				break;
			case 1:
				sprintMapping.getMapped().put(src.getId(), targets.get(0));
				mappedCount++;
				break;
			default:
				sprintMapping.getConflict().put(src.getId(), targets);
				Log.warn(LOGGER, "Sprint [" + src.getName() + "] is mapped to multiple Cloud sprints");
				break;
			}
		}
		Log.printCount(LOGGER, "Sprints mapped: ", mappedCount, serverSprints.size());
		saveFile(MappingType.SPRINT.getMap(), sprintMapping);
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
		saveFile(MappingType.FILTER.getList(), result);
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
		saveFile(MappingType.DASHBOARD.getList(), result);
		Log.info(LOGGER, "Dashboards found: " + result.size());
		Log.info(LOGGER, "Dashboard list completed");
	}

	private static void deleteFilter(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Deleting migrated filters...");
		Mapping filters = readFile(MappingType.FILTER.getMap(), Mapping.class);
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
		Mapping dashboards = readFile(MappingType.DASHBOARD.getMap(), Mapping.class);
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
	
	private static void assignProjectRoles(
			Client client, Config config, String accountId, String[] roleNames, boolean grant) 
			throws Exception {
		Pattern pattern = Pattern.compile("^.+/([0-9]+)$");
		// Get account if null
		if (accountId == null) {
			Map<String, Object> query = new HashMap<>();
			query.put("query", config.getTargetUser());
			query.put("maxResults", 1);
			Response resp = restCall(
					client, 
					new URI(config.getTargetRESTBaseURL())
						.resolve("/rest/api/3/user/picker"), 
					HttpMethod.GET, 
					null, query, null);
			UserPicker userPicker = resp.readEntity(UserPicker.class);
			if (userPicker.getTotal() == 1) {
				accountId = userPicker.getUsers().get(0).getAccountId();
			} else {
				Log.error(LOGGER, "Unable to retrieve account id for " + config.getTargetUser());
				return;
			}
		}
		// Get project list
		List<Project> projectList = getCloudProjects(client, config);
		for (Project project : projectList) {
			// For each project, get role list
			Response resp = restCall(	client, 
										new URI(config.getTargetRESTBaseURL())
											.resolve("/rest/api/3/project/")
											.resolve(project.getId() + "/")
											.resolve("role"),
										HttpMethod.GET,
										null, null, null);
			if (checkStatusCode(resp, Response.Status.OK)) {
				// Parse name and id
				Map<String, String> roleMap = new HashMap<>();
				Map<String, String> data = resp.readEntity(new GenericType<Map<String, String>>(){});
				for (Map.Entry<String, String> entry : data.entrySet()) {
					Matcher matcher = pattern.matcher(entry.getValue());
					if (matcher.matches()) {
						roleMap.put(entry.getKey(), matcher.group(1));
					} else {
						Log.error(LOGGER, 
								"Unrecognized pattern for project role in project " + 
								project.getName() + " (" + project.getKey() + "): " + 
								"[" + entry.getValue() + "]");
					}
				}
				// For each role requested
				for (String roleName : roleNames) {
					if (roleMap.containsKey(roleName)) {
						String roleId = roleMap.get(roleName);
						// Grant or revoke
						Map<String, Object> query = new HashMap<>();	
						Map<String, Object> payload = new HashMap<>();
						if (grant) {
							// Put user in post data
							List<String> userList = new ArrayList<>();
							userList.add(accountId);
							payload.put("user", userList);
						} else {
							// Put user in query
							query.put("user", accountId);
						}
						Response resp2 = restCall(
								client,
								new URI(config.getTargetRESTBaseURL())
									.resolve("/rest/api/3/project/")
									.resolve(project.getId() + "/")
									.resolve("role/")
									.resolve(roleId),
								(grant? HttpMethod.POST : HttpMethod.DELETE),
								null, 
								(grant? null : query), 
								(grant? payload : null));
						if (grant) {
							// Grant result
							switch (resp2.getStatus()) {
							case 200: 
								Log.info(LOGGER, 
										"Granted role " + roleName + " (" + roleId + ") to user " + accountId + 
										" in project " + project.getName() + " (" + project.getKey() + ")");
								break;
							case 400:
								Log.info(LOGGER, 
										"Role " + roleName + " (" + roleId + ") " + 
										"is already granted to user " + accountId + 
										" in project " + project.getName() + " (" + project.getKey() + ")");
								break;
							default:
								Log.error(LOGGER, 
										"Unable to grant role " + roleName + " (" + roleId + ") to user " + accountId + 
										" in project " + project.getName() + " (" + project.getKey() + "): " + 
										resp2.readEntity(String.class));
								break;
							}
						} else {
							// Revoke result
							switch (resp2.getStatus()) {
							case 204: 
								Log.info(LOGGER, 
										"Revoked role " + roleName + " (" + roleId + ") from user " + accountId + 
										" in project " + project.getName() + " (" + project.getKey() + ")");
								break;
							case 404:
								Log.info(LOGGER, 
										"Role " + roleName + " (" + roleId + ") " + 
										"is not granted to user " + accountId + 
										" in project " + project.getName() + " (" + project.getKey() + ")");
								break;
							default:
								Log.error(LOGGER, 
										"Unable to revoke role " + roleName + " (" + roleId + ") from user " + accountId + 
										" in project " + project.getName() + " (" + project.getKey() + "): " + 
										resp2.readEntity(String.class));
								break;
							}
						}
					} else {
						Log.error(LOGGER, 
								"Project role requested [" + roleName + "] " + 
								"is not found in project " + project.getName() + " (" + project.getKey() + ")");
					}
				}
			} else {
				Log.error(LOGGER, 
						"Unable to read project roles for project " + 
						project.getName() + " (" + project.getKey() + "): " + 
						resp.readEntity(String.class));
			}
		}
	}

	@SuppressWarnings("incomplete-switch")
	public static void main(String[] args) {
		AnsiConsole.systemInstall();
		try {
			// Parse config
			CommandLine cli = CLI.parseCommandLine(args);
			if (cli == null) {
				return;
			}
			Config conf = parseConfig(cli);
			if (conf == null) {
				return;
			}

			// TODO
			/*
			Status s = new Status();
			List<Status> sList = s.getCloudObjects(conf, Status.class, null);
			List<Status> sList2 = s.getServerObjects(conf, Status.class, null);
			
			User u = new User();
			List<User> uList = u.getCloudObjects(conf, User.class, null);
			List<User> uList2 = u.getServerObjects(conf, User.class, null);
			
			Role r = new Role();
			List<Role> rList = r.getCloudObjects(conf, Role.class, null);
			List<Role> rList2 = r.getServerObjects(conf, Role.class, null);
			
			Project p = new Project();
			List<Project> pList = p.getCloudObjects(conf, Project.class, null);
			for (Project project : pList) {
				Map<String, Object> data = new HashMap<>();
				data.put(ProjectComponent.PARAM_PROJECTID, project.getId());
				ProjectComponent pc = new ProjectComponent();
				List<ProjectComponent> pcList = pc.getCloudObjects(conf, ProjectComponent.class, data);
			}
			List<Project> pList2 = p.getServerObjects(conf, Project.class, null);
			for (Project project : pList2) {
				Map<String, Object> data = new HashMap<>();
				data.put(ProjectComponent.PARAM_PROJECTID, project.getId());
				ProjectComponent pc = new ProjectComponent();
				List<ProjectComponent> pcList = pc.getServerObjects(conf, ProjectComponent.class, data);
			}

			ProjectCategory pcat = new ProjectCategory();
			List<ProjectCategory> pcatList = pcat.getCloudObjects(conf, ProjectCategory.class, null);
			List<ProjectCategory> pcatList2 = pcat.getServerObjects(conf, ProjectCategory.class, null);

			Group g = new Group();
			List<Group> gList = g.getCloudObjects(conf, Group.class, null);
			List<Group> gList2 = g.getServerObjects(conf, Group.class, null);

			CustomField cf = new CustomField();
			List<CustomField> cfList = cf.getCloudObjects(conf, CustomField.class, null);
			List<CustomField> cfList2 = cf.getServerObjects(conf, CustomField.class, null);
			
			AgileBoard ab = new AgileBoard();
			List<AgileBoard> abList = ab.getCloudObjects(conf, AgileBoard.class, null);
			Sprint sp = new Sprint();
			for (AgileBoard a : abList) {
				if (a.canHasSprint()) {
					Map<String, Object> data = new HashMap<>();
					data.put(Sprint.PARAM_BOARDID, a.getId());
					List<Sprint> spList = sp.getCloudObjects(conf, Sprint.class, data);
					Log.info(LOGGER, "Board: " + a.getName() + " (" + a.getId() + ")");
					Log.info(LOGGER, "Sprint: " + spList.size());
					for (Sprint s : spList) {
						Log.info(LOGGER, "Sprint: " + s.getName() + " (" + s.getId() + ")");
					}
				}
			}
			List<AgileBoard> abList2 = ab.getServerObjects(conf, AgileBoard.class, null);
			for (AgileBoard a : abList2) {
				if (a.canHasSprint()) {
					Map<String, Object> data = new HashMap<>();
					data.put(Sprint.PARAM_BOARDID, a.getId());
					List<Sprint> spList = sp.getServerObjects(conf, Sprint.class, data);
					Log.info(LOGGER, "Board: " + a.getName() + " (" + a.getId() + ")");
					Log.info(LOGGER, "Sprint: " + spList.size());
					for (Sprint s : spList) {
						Log.info(LOGGER, "Sprint: " + s.getName() + " (" + s.getId() + ")");
					}
				}
			}
			*/
			
			if (cli.hasOption(CLI.GRANT_OPTION)) {
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					String[] roles = cli.getOptionValues(CLI.ROLE_OPTION);
					String accountId = cli.getOptionValue(CLI.USER_OPTION);
					assignProjectRoles(wrapper.getClient(), conf, accountId, roles, true);
				}
			} else if (cli.hasOption(CLI.REVOKE_OPTION)) {
				try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
					String[] roles = cli.getOptionValues(CLI.ROLE_OPTION);
					String accountId = cli.getOptionValue(CLI.USER_OPTION); 
					assignProjectRoles(wrapper.getClient(), conf, accountId, roles, false);
				}
			} else {
				// CLI.MAIN_OPTIONS
				for (Option op : cli.getOptions()) {
					CLIOptions opt = CLIOptions.parse(op);
					if (opt == null) {
						continue;
					}
					switch (opt) {
					case MAP_USER: 
						mapUsersWithCSV(cli.getOptionValue(CLI.MAPUSER_OPTION));
						break;
					case DUMP_DC: {
						SqlSessionFactory sqlSessionFactory = setupMyBatis(conf);
						try (	SqlSession session = sqlSessionFactory.openSession();
								ClientWrapper wrapper = new ClientWrapper(false, conf)) {
							// Get filter info from source
							dumpDC(wrapper.getClient(), conf);
							FilterMapper filterMapper = session.getMapper(FilterMapper.class);
							dumpDashboard(filterMapper, wrapper.getClient(), conf);
						}
						break;
					}
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
				}				
			}
		} catch (Exception ex) {
			LOGGER.fatal("Exception: " + ex.getMessage(), ex);
		}
		AnsiConsole.systemUninstall();
	}
}
