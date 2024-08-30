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
	
	private static void mapFilterV2(Config conf) throws Exception {
		// TODO
		// Group filters in batches
		// Map and create each batch
	}
	
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
			Log.info(LOGGER, "Mapping " + type);
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
	
	private static void mapFilters() throws Exception {
		Log.info(LOGGER, "Processing Filters...");
		List<Filter> filters = readValuesFromFile(MappingType.FILTER.getDC(), 
				Filter.class);
		Mapping userMapping = readFile(MappingType.USER.getMap(), Mapping.class);
		Mapping projectMapping = readFile(MappingType.PROJECT.getMap(), Mapping.class);
		Mapping roleMapping = readFile(MappingType.ROLE.getMap(), Mapping.class);
		Mapping groupMapping = readFile(MappingType.GROUP.getMap(), Mapping.class);
		Mapping fieldMapping = readFile(MappingType.CUSTOM_FIELD.getMap(), Mapping.class);
		Map<String, Mapping> maps = new HashMap<>();
		maps.put("project", projectMapping);
		maps.put("field", fieldMapping);
		int successCount = 0;
		for (Filter filter : filters) {
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
					case MAP_USER: 
						mapUsersWithCSV(cli.getOptionValue(CLI.MAPUSER_OPTION));
						break;
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
