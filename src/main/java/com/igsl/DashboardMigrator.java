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
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.igsl.model.CloudPermission;
import com.igsl.model.DataCenterPermission;
import com.igsl.model.DataCenterPortalPage;
import com.igsl.model.DataCenterPortalPermission;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.PermissionTarget;
import com.igsl.model.PermissionType;
import com.igsl.model.mapping.Dashboard;
import com.igsl.model.mapping.DashboardSearchResult;
import com.igsl.model.mapping.Filter;
import com.igsl.model.mapping.JiraObject;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;
import com.igsl.model.mapping.Project;
import com.igsl.model.mapping.SearchResult;
import com.igsl.model.mapping.User;
import com.igsl.model.mapping.UserPicker;
import com.igsl.mybatis.FilterMapper;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

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
	
	private static SingleValueOperand mapValue(
			SingleValueOperand src, Mapping map, 
			String filterName, String propertyName) throws Exception {
		SingleValueOperand result = null;
		if (src != null) {
			boolean isLong = false;
			String originalValue = null;
			if (src.getLongValue() != null) {
				originalValue = Long.toString(src.getLongValue());
				isLong = true;
			} else {
				originalValue = src.getStringValue();
				isLong = false;
			}
			if (map != null) {
				if (isLong) {
					if (map.getMapped().containsKey(originalValue)) {
						// Remap numerical values
						Long newValue = Long.valueOf(map.getMapped().get(originalValue));
						Log.info(LOGGER, "Mapped value for filter [" + filterName + "] type [" +
								propertyName + "] value [" + originalValue + "] => [" + newValue + "]");
						result = new SingleValueOperand(newValue);
					} else {
						if (map.getType() == MappingType.FILTER) {
							throw new FilterNotMappedException(originalValue);
						} else {
							throw new Exception("Unable to map value for filter [" + filterName + "] type [" + 
										propertyName + "] value [" + originalValue + "]");
						}
					}
				} else {
					// String values not remapped
					Log.info(LOGGER, "Value unchanged for filter [" + filterName + "] type [" +
							propertyName + "] value [" + originalValue + "]");
					result = new SingleValueOperand(originalValue);
				}
			} else {
				if (isLong) {
					Log.warn(LOGGER, "Value unchanged for filter [" + filterName + "] type [" + propertyName
							+ "] value [" + originalValue + "]");
					result = new SingleValueOperand(src.getLongValue());
				} else {
					Log.warn(LOGGER, "Value unchanged for filter [" + filterName + "] type [" + propertyName
							+ "] value [" + originalValue + "]");
					result = new SingleValueOperand(src.getStringValue());
				}
			}
		}
		return result;
	}

	private static Clause mapClause(String filterName, Map<MappingType, Mapping> maps, Clause c) throws Exception {
		Clause clone = null;
		List<Clause> clonedChildren = new ArrayList<>();
		if (c != null) {
			Log.debug(LOGGER, "Clause: [" + c + "], [" + c.getClass() + "]");
			String propertyName = c.getName();
			String newPropertyName = propertyName;
			MappingType mappingType = null;
			if (propertyName != null) {
				// Get mapping type
				mappingType = MappingType.parseFilterProperty(propertyName);
				// Remap propertyName if it is a custom field
				Mapping customFieldMapping = maps.get(MappingType.CUSTOM_FIELD);
				newPropertyName = mapCustomFieldName(customFieldMapping.getMapped(), propertyName);
				Log.info(LOGGER, "Property [" + propertyName + "] -> [" + newPropertyName + "]");
			}
			for (Clause sc : c.getClauses()) {
				// Recursively process children
				Clause clonedChild = mapClause(filterName, maps, sc);
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
				Operand originalOperand = tc.getOperand();
				Operand clonedOperand = null;
				if (mappingType != null) {
					// Modify value
					Mapping map = maps.get(mappingType);
					if (originalOperand instanceof SingleValueOperand) {
						SingleValueOperand svo = (SingleValueOperand) originalOperand;
						// Change value
						clonedOperand = mapValue(svo, map, filterName, tc.getName());
					} else if (originalOperand instanceof MultiValueOperand) {
						MultiValueOperand mvo = (MultiValueOperand) originalOperand;
						List<Operand> list = new ArrayList<>();
						for (Operand item : mvo.getValues()) {
							if (item instanceof SingleValueOperand) {
								// Change value
								SingleValueOperand svo = (SingleValueOperand) item;
								list.add(mapValue(svo, map, filterName, tc.getName()));
							} else {
								list.add(item);
							}
						}
						clonedOperand = new MultiValueOperand(list);
					} else if (originalOperand instanceof FunctionOperand) {
						// TODO Throw error for unsupported function?
						// TODO Remap arguments?
						FunctionOperand fo = (FunctionOperand) originalOperand;
						List<String> args = new ArrayList<>();
						for (String s : fo.getArgs()) {
							args.add("\"" + s + "\"");
						}
						clonedOperand = new FunctionOperand(fo.getName(), args);
					} else if (originalOperand instanceof EmptyOperand) {
						clonedOperand = originalOperand;
					} else {
						Log.warn(LOGGER, "Unrecognized Operand class for filter [" + filterName + "] class [" + originalOperand.getClass()
								+ "], reusing reference");
						clonedOperand = originalOperand;
					}
				} else {
					// No change
					clonedOperand = originalOperand;
				}
				// Create clone
				clone = new MyTerminalClause(newPropertyName, tc.getOperator(), clonedOperand);
			} else if (c instanceof WasClause) {
				WasClause wc = (WasClause) c;
				clone = new WasClauseImpl(newPropertyName, wc.getOperator(), wc.getOperand(), wc.getPredicate());
			} else if (c instanceof ChangedClause) {
				ChangedClause cc = (ChangedClause) c;
				clone = new ChangedClauseImpl(newPropertyName, cc.getOperator(), cc.getPredicate());
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
	
	@SuppressWarnings("incomplete-switch")
	private static void mapFiltersV2(Config conf) throws Exception {
		// Results of mapped and failed filters
		Mapping result = new Mapping(MappingType.FILTER);
		List<Filter> remappedList = new ArrayList<>();
		// RestUtil 
		RestUtil<Object> util = RestUtil.getInstance(Object.class).config(conf, true);
		// Load mappings from files
		Map<MappingType, Mapping> mappings = loadMappings(MappingType.FILTER, MappingType.DASHBOARD);
		// Add filter mapping, to be filled as we go
		mappings.put(MappingType.FILTER, result);
		// Get filters to map
		List<Filter> filterList = readValuesFromFile(MappingType.FILTER.getDC(), Filter.class);
		// Process in batches
		int batch = 0;
		boolean done = false;
		while (!done) {
			Log.info(LOGGER, "Processing filters batch #" + batch);
			// Put filters into current batch
			Map<String, Filter> currentBatch = new LinkedHashMap<>();	
			for (Filter filter : filterList) {
				currentBatch.put(filter.getId(), filter);
			}
			filterList.clear();
			// Process current batch, put those missing filter references back into filterList
			for (Filter filter : currentBatch.values()) {
				Log.info(LOGGER, "Processing filter " + filter.getName());
				// Verify and map owner
				String originalOwner = filter.getOwner().getKey();
				String newOwner = null;
				if (!mappings.get(MappingType.USER).getMapped().containsKey(originalOwner)) {
					String msg = "Filter [" + filter.getName() + "] " + 
							"owned by unmapped user [" + originalOwner + "]";
					Log.error(LOGGER, msg);
					result.getFailed().put(filter.getId(), msg);
					continue;
				}
				newOwner = mappings.get(MappingType.USER).getMapped().get(originalOwner);
				filter.getOwner().setAccountId(newOwner);
				// Verify and map share permissions
				List<DataCenterPermission> newPermissions = new ArrayList<>();
				for (DataCenterPermission share: filter.getSharePermissions()) {
					PermissionType permissionType = PermissionType.parse(share.getType());
					switch (permissionType) {
					case GLOBAL: // Fall-thru
					case LOGGED_IN:	// Fall-thru
						// Add permission unchanged
						newPermissions.add(share);
						break;
					case UNKNOWN:	
						// This happens when you share to a project you cannot access. 
						// This type of permissions cannot be created via REST.
						// So this is excluded.
						Log.warn(LOGGER, "Share permission to inaccessible project [" + 
								share.getProject().getId() + "] excluded");
						break;
					case GROUP:
						if (mappings.get(MappingType.GROUP).getMapped().containsKey(share.getGroup().getName())) {
							String newId = mappings.get(MappingType.GROUP).getMapped()
									.get(share.getGroup().getName());
							share.getGroup().setName(newId);
							newPermissions.add(share);
						} else {
							String msg = "Filter [" + filter.getName() + "] " + 
									"is shared to unmapped group [" + share.getGroup().getName() + "]";
							Log.error(LOGGER, msg);
							result.getFailed().put(filter.getId(), msg);
							continue;
						}
						break;
					case PROJECT:
						if (share.getRole() == null) {
							// No role, add as project
							if (mappings.get(MappingType.PROJECT).getMapped().containsKey(share.getProject().getId())) {
								String newId = mappings.get(MappingType.PROJECT).getMapped()
										.get(share.getProject().getId());
								share.getProject().setId(newId);
								newPermissions.add(share);
							} else {
								String msg = "Filter [" + filter.getName() + "] " + 
										"is shared to unmapped project [" + share.getProject().getId() + "]";
								Log.error(LOGGER, msg);
								result.getFailed().put(filter.getId(), msg);
								continue;
							}
						} else if (share.getRole() != null) {
							// Has role, add as project-role
							if (mappings.get(MappingType.PROJECT).getMapped().containsKey(share.getProject().getId())) {
								String newId = mappings.get(MappingType.PROJECT).getMapped()
										.get(share.getProject().getId());
								share.getProject().setId(newId);
							} else {
								String msg = "Filter [" + filter.getName() + "] " + 
										"is shared to unmapped project [" + share.getProject().getId() + "]";
								Log.error(LOGGER, msg);
								result.getFailed().put(filter.getId(), msg);
								continue;
							}
							if (mappings.get(MappingType.ROLE).getMapped().containsKey(share.getRole().getId())) {
								String newId = mappings.get(MappingType.ROLE).getMapped()
										.get(share.getRole().getId());
								DataCenterPermission newItem = new DataCenterPermission();
								newItem.setEdit(share.isEdit());
								newItem.setView(share.isView());
								newItem.setType(PermissionType.PROJECT_ROLE.toString());
								newItem.setProject(share.getProject());
								PermissionTarget newTarget = new PermissionTarget();
								newTarget.setId(newId);
								newItem.setRole(newTarget);
								newPermissions.add(newItem);
							} else {
								String msg = "Filter [" + filter.getName() + "] " + 
										"is shared to unmapped role [" + share.getRole().getId() + "]";
								Log.error(LOGGER, msg);
								result.getFailed().put(filter.getId(), msg);
								continue;
							}
						}
						break;
					case USER:
						if (mappings.get(MappingType.USER).getMapped().containsKey(share.getUser().getKey())) {
							String newId = mappings.get(MappingType.USER).getMapped()
									.get(share.getUser().getKey());
							share.getUser().setAccountId(newId);
							newPermissions.add(share);
						} else {
							String msg = "Filter [" + filter.getName() + "] " + 
									"is shared to unmapped user [" + share.getUser().getKey() + "]";
							Log.error(LOGGER, msg);
							result.getFailed().put(filter.getId(), msg);
							continue;
						}
						break;
					}
				}
				filter.setSharePermissions(newPermissions);
				// Parse and convert JQL
				JqlLexer lexer = new JqlLexer((CharStream) new ANTLRStringStream(filter.getJql()));
				CommonTokenStream cts = new CommonTokenStream(lexer);
				JqlParser parser = new JqlParser(cts);
				query_return qr = parser.query();
				try {
					validateClause(filter.getName(), qr.clause);
				} catch (Exception ex) {
					Log.error(LOGGER, "Failed to map filter [" + filter.getName() + "]", ex);
					result.getFailed().put(filter.getId(), ex.getMessage());
					continue;
				}
				try {
					Clause clone = mapClause(filter.getName(), mappings, qr.clause);
					// Handler order clause
					OrderBy orderClone = null;
					if (qr.order != null) {
						Mapping fieldMapping = mappings.get(MappingType.CUSTOM_FIELD);
						List<SearchSort> sortList = new ArrayList<>();
						for (SearchSort ss : qr.order.getSearchSorts()) {
							String newColumn = mapCustomFieldName(fieldMapping.getMapped(), ss.getField());
							SearchSort newSS = new SearchSort(newColumn, ss.getProperty(), ss.getSortOrder());
							sortList.add(newSS);
							Log.warn(LOGGER, "Mapped sort column for filter [" + filter.getName() + "] " + 
									"column [" + ss.getField() + "] => [" + newColumn + "]");
						}
						orderClone = new OrderByImpl(sortList);
					}
					Log.info(LOGGER, "Updated JQL for filter [" + filter.getName() + "]: " + 
							"[" + clone + ((orderClone != null)? " " + orderClone : "") + "]");
					filter.setJql(clone + ((orderClone != null) ? " " + orderClone : ""));
					remappedList.add(filter);
					// Check if filter already exist
					CloudFilter cloudFilter = CloudFilter.create(filter);
					List<Filter> foundFilterList = RestUtil.getInstance(Filter.class)
						.config(conf, true)
						.path("/rest/api/latest/filter/search")
						.method(HttpMethod.GET)
						.query("filterName", "\"" + filter.getName() + "\"")
						.pagination(new Paged<Filter>(Filter.class).maxResults(1))
						.requestAllPages();
					Response respFilter = null;
					boolean updateFilter = false;
					Log.info(LOGGER, "Filter payload: [" + OM.writeValueAsString(cloudFilter) + "]");
					if (foundFilterList.size() == 1) {
						cloudFilter.setId(foundFilterList.get(0).getId());
						// Update filter
						updateFilter = true;
						respFilter = util.path("/rest/api/latest/filter/{filterId}")
								.pathTemplate("filterId", cloudFilter.getId())
								.method(HttpMethod.PUT)
								.query("overrideSharePermissions", true)
								.payload(cloudFilter)
								.request();
					} else {
						// Create filter
						updateFilter = false;
						respFilter = util.path("/rest/api/latest/filter")
								.method(HttpMethod.POST)
								.query("overrideSharePermissions", true)
								.payload(cloudFilter)
								.request();
					}
					if (!checkStatusCode(respFilter, Response.Status.OK)) {
						String msg = respFilter.readEntity(String.class);
						result.getFailed().put(filter.getId(), msg);
						continue;
					} 
					CloudFilter newFilter = respFilter.readEntity(CloudFilter.class);
					result.getMapped().put(filter.getId(), newFilter.getId());
					Log.info(LOGGER, "Filter [" + filter.getName() + "] " + 
							(updateFilter? "updated" : "created") + ": " + newFilter.getId());
					// Change owner
					PermissionTarget owner = new PermissionTarget();
					owner.setAccountId(newOwner);
					Response respOwner = util.path("/rest/api/latest/filter/{filterId}/owner")
						.pathTemplate("filterId", newFilter.getId())
						.method(HttpMethod.PUT)
						.payload(owner)
						.request();
					if (!checkStatusCode(respOwner, Response.Status.NO_CONTENT)) {
						String msg = "Failed to set owner for filter [" + filter.getName() + "] to " + 
								"[" + filter.getOwner().getAccountId() + "]: " + 
								respOwner.readEntity(String.class);
						Log.error(LOGGER, msg);
						result.getFailed().put(filter.getId(), msg);
						continue;
					}
					Log.info(LOGGER, "Filter [" + filter.getName() + "] owner changed: " + newOwner);
				} catch (FilterNotMappedException fnmex) {
					String refId = fnmex.getReferencedFilter();
					// Detect impossible to map ones and declare them failed
					if (!currentBatch.containsKey(refId) && 
						!result.getMapped().containsKey(refId)) {
						// Filter references non-existent filter
						String msg = "Filter [" + filter.getName() + "] references non-existing filter [" + refId + "]";
						Log.error(LOGGER, msg);
						result.getFailed().put(filter.getId(), msg);
					} else if (result.getFailed().containsKey(refId)) {
						// Filter references a filter that failed
						String msg = "Filter [" + filter.getName() + "] references failed filter [" + refId + "]";
						Log.error(LOGGER, msg);
						result.getFailed().put(filter.getId(), msg);
					} else {					
						// Add filter to next batch
						Log.info(LOGGER, "Filter [" + filter.getName() + "] " + 
								"references filter [" + refId + "] which is not created in Cloud yet, " + 
								"delaying filter to next batch");
						filterList.add(filter);
					}
					// Note: Circular reference is impossible, so no need to check those
				} catch (Exception ex) {
					Log.error(LOGGER, "Failed to map filter [" + filter.getName() + "]", ex);
					result.getFailed().put(filter.getId(), ex.getMessage());
					continue;
				}
			} // For each filter in currentBatch
			if (filterList.size() == 0) {
				// No more filters
				done = true;
			}
			batch++;
		}	// While !done
		// Save result
		saveFile(MappingType.FILTER.getRemapped(), remappedList);
		saveFile(MappingType.FILTER.getMap(), result);
	}
	
	private static void validateClause(String filterName, Clause clause) throws Exception {
		if (clause instanceof TerminalClause) {
			TerminalClause tc = (TerminalClause) clause;
			Operand originalOperand = tc.getOperand();
			String propertyName = tc.getName().toLowerCase();
			if ("issuefunction".equals(propertyName)) {
				throw new Exception("Filter [" + filterName + "] Property [" + propertyName + "] " + 
						"issueFunction is no longer supported");
			}
			// TODO Check value?
			if (originalOperand instanceof SingleValueOperand) {
			} else if (originalOperand instanceof MultiValueOperand) {
				MultiValueOperand mvo = (MultiValueOperand) originalOperand;
			} else if (originalOperand instanceof FunctionOperand) {
			}
		} 
		// Check children
		for (Clause child :	clause.getClauses()) {
			validateClause(filterName, child);
		}
	}
	
	/**
	 * Dashboard and Filter from Server requires DB access to dump, so this is needed outside of mapObjectsV2().
	 */
	private static void dumpDashboard(Config conf) throws Exception {
		SqlSessionFactory factory = setupMyBatis(conf);
		try (SqlSession session = factory.openSession()) {
			// Get filter info from source
			FilterMapper filterMapper = session.getMapper(FilterMapper.class);
			Log.info(LOGGER, "Dumping filters and dashboards from Data Center...");
			Log.info(LOGGER, "Processing Filters...");
			List<Integer> filters = filterMapper.getFilters();
			List<Filter> filterList = new ArrayList<>();
			for (Integer id : filters) {
				List<Filter> filter = JiraObject.getObjects(conf, Filter.class, false, null, id);
				filterList.addAll(filter);
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
	}
	
	private static Map<MappingType, List<? extends JiraObject<?>>> dumpObjects(
			Config conf, boolean cloud) throws Exception {
		Map<MappingType, List<? extends JiraObject<?>>> map = new HashMap<>();
		for (MappingType type : MappingType.values()) {
			Class<?> dataClass = null;
			if (cloud && type.isIncludeCloud()) {
				dataClass = type.getDataClass();
			} else if (!cloud && type.isIncludeServer()) {
				dataClass = type.getDataClass();
			}
			if (dataClass != null) {
				@SuppressWarnings({ "unchecked" })
				List<? extends JiraObject<?>> list = 
					(List<? extends JiraObject<?>>) JiraObject.getObjects(conf, dataClass, cloud, map);
				map.put(type, list);
				Log.info(LOGGER, type + ": " + list.size() + " object(s) found");
				saveFile((cloud? type.getCloud() : type.getDC()), list);
			}
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	private static <U extends JiraObject<U>> void mapObjectsV2() throws Exception {
		Log.info(LOGGER, "Mapping objects between Data Center and Cloud...");
		for (MappingType type : MappingType.values()) {
			Log.info(LOGGER, "Processing mapping for " + type);
			int mappedCount = 0;
			Mapping mapping = new Mapping(type);
			if (type.isIncludeCloud() && type.isIncludeServer()) {
				List<U> serverObjects =
						(List<U>) readValuesFromFile(type.getDC(), type.getDataClass());
				List<U> cloudObjects =
						(List<U>) readValuesFromFile(type.getCloud(), type.getDataClass());
				for (U server : serverObjects) {
					List<String> targets = new ArrayList<>();
					for (U cloud : cloudObjects) {
						if (server.compareTo(cloud) == 0) {
							targets.add(cloud.getUniqueName());
						}
					}
					switch (targets.size()) {
					case 0:
						mapping.getUnmapped().add(server);
						Log.warn(LOGGER, type + " [" + server.getUniqueName() + "] is not mapped");
						break;
					case 1: 
						mapping.getMapped().put(server.getUniqueName(), targets.get(0));
						mappedCount++;
						break;
					default:
						mapping.getConflict().put(server.getUniqueName(), targets);
						Log.warn(LOGGER, 
								type + " [" + server.getUniqueName() + "] is mapped to multiple Cloud objects");
						break;
					}
				}
				Log.printCount(LOGGER, type + " mapped: ", mappedCount, serverObjects.size());
				saveFile(type.getMap(), mapping);
			}	// If type is exported for both server and cloud
		}	// For all types
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
		List<Filter> filters = readValuesFromFile(MappingType.FILTER.getRemapped(), Filter.class);
		// Create filter mapping along the way
		Mapping migratedList = new Mapping(MappingType.FILTER);
		int migratedCount = 0;
		for (Filter filter : filters) {
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
	
	private static Map<MappingType, Mapping> loadMappings(MappingType... excludes) throws Exception {
		List<MappingType> excludeList = new ArrayList<>();
		for (MappingType e : excludes) {
			excludeList.add(e);
		}
		Map<MappingType, Mapping> result = new HashMap<>();
		for (MappingType type : MappingType.values()) {
			if (!excludeList.contains(type)) {
				if (Files.exists(Paths.get(type.getMap()))) {
					Mapping m = readFile(type.getMap(), Mapping.class);
					result.put(type, m);
				} else {
					Log.warn(LOGGER, "Mapping [" + type.getMap() + "] cannot be loaded");
				}
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
	
	private static void mapUsersWithCSV(String csvFile) throws Exception {
		Log.info(LOGGER, "Mapping users with CSV: " + csvFile);
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

	/*
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
	*/

	private static final Pattern CUSTOM_FIELD_CF = Pattern.compile("^cf\\[([0-9]+)\\]$");
	private static final String CUSTOM_FIELD = "customfield_";
	private static String mapCustomFieldName(Map<String, String> map, String data) throws Exception {
		// If data is customfield_#
		if (map.containsKey(data)) {
			return map.get(data);
		}
		// If data is cf[#]
		Matcher m = CUSTOM_FIELD_CF.matcher(data);
		if (m.matches()) {
			if (map.containsKey(CUSTOM_FIELD + m.group(1))) {
				String s = map.get(CUSTOM_FIELD + m.group(1));
				s = s.substring(CUSTOM_FIELD.length());
				return "cf[" + s + "]";
			} else {
				throw new Exception("Custom field [" + data + "] cannot be mapped");
			}
		}
		if (data.contains(" ")) {
			return "\"" + data + "\"";
		} else {
			return data;
		}
	}

	/*
	private static Clause cloneClause(String filterName, Map<String, Mapping> maps, Clause c) throws Exception {
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
	*/
	
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

	private static void getCredentials(Config config, boolean cloud) throws IOException {
		String userName = null;
		String password = null;
		if (cloud) {
			userName = config.getTargetUser();
			if (userName == null || userName.isEmpty()) {
				userName = Console.readLine("Cloud administrator email: ");
				config.setTargetUser(userName);
			}
			password = config.getTargetAPIToken();
			if (password == null || password.isEmpty()) {
				password = new String(Console.readPassword("API token for " + userName + ": "));
				config.setTargetAPIToken(password);
			}
		} else {
			userName = config.getSourceUser();
			if (userName == null || userName.isEmpty()) {
				userName = Console.readLine("DataCenter administrator username: ");
				config.setSourceUser(userName);
			}
			password = config.getSourcePassword();
			if (password == null || password.isEmpty()) {
				password = new String(Console.readPassword("Password for " + userName + ": "));
				config.setSourcePassword(password);
			}
		}
	}
	
	@SuppressWarnings("incomplete-switch")
	public static void main(String[] args) {
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
				if (cli.getOptions().length < 2) {
					CLI.printHelp();
					return;
				}
				for (Option op : cli.getOptions()) {
					CLIOptions opt = CLIOptions.parse(op);
					if (opt == null) {
						continue;
					}
					switch (opt) {
					case DUMP_DC: {
						getCredentials(conf, false);
						dumpObjects(conf, false);
						dumpDashboard(conf);
						break;
					}
					case DUMP_CLOUD:
						getCredentials(conf, true);
						dumpObjects(conf, true);
						break;
					case MAP_OBJECT:
						mapObjectsV2();
						String csvFile = cli.getOptionValue(CLI.MAPOBJECT_OPTION);
						if (csvFile != null) {
							// Override user mapping with CSV file exported from Cloud User Management
							mapUsersWithCSV(csvFile);
						}
						break;
					case CREATE_FILTER: 
						mapFiltersV2(conf);
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
	}
}
