package com.igsl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
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
import com.atlassian.query.clause.ChangedClause;
import com.atlassian.query.clause.ChangedClauseImpl;
import com.atlassian.query.clause.Clause;
import com.atlassian.query.clause.TerminalClause;
import com.atlassian.query.clause.TerminalClauseImpl;
import com.atlassian.query.history.HistoryPredicate;
import com.atlassian.query.operand.FunctionOperand;
import com.atlassian.query.operand.MultiValueOperand;
import com.atlassian.query.operand.Operand;
import com.atlassian.query.operand.SingleValueOperand;
import com.atlassian.query.operator.Operator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.igsl.CLI.CLIOptions;
import com.igsl.config.Config;
import com.igsl.model.CloudDashboard;
import com.igsl.model.CloudPermission;
import com.igsl.model.DataCenterPermission;
import com.igsl.model.DataCenterPortalPage;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.mapping.CustomField;
import com.igsl.model.mapping.Dashboard;
import com.igsl.model.mapping.Filter;
import com.igsl.model.mapping.JiraObject;
import com.igsl.model.mapping.JiraObjectDeserializer;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;
import com.igsl.model.mapping.Project;
import com.igsl.model.mapping.User;
import com.igsl.mybatis.FilterMapper;
import com.igsl.rest.ClientPool;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;
import com.igsl.rest.SinglePage;
import com.igsl.thread.CreateDashboard;
import com.igsl.thread.CreateDashboardResult;
import com.igsl.thread.CreateGadgetResult;
import com.igsl.thread.EditFilterPermission;
import com.igsl.thread.MapFilter;
import com.igsl.thread.MapFilterResult;

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
	
	private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private static final String NEWLINE = "\r\n";
	private static final Logger LOGGER = LogManager.getLogger(DashboardMigrator.class);
	
	private static final ObjectMapper OM = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			// Allow comments
			.configure(Feature.ALLOW_COMMENTS, true)	
			// Allow attributes missing in POJO
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) 
			// Add custom deserializer for JiraObject
			.registerModule(new SimpleModule()
					.addDeserializer(JiraObject.class, new JiraObjectDeserializer()));
	
	public static SqlSessionFactory setupMyBatis(Config conf) throws Exception {
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
	
	private static boolean checkStatusCode(Response resp, Response.Status check) {
		if ((resp.getStatus() & check.getStatusCode()) == check.getStatusCode()) {
			return true;
		}
		return false;
	}

	private static List<Project> getCloudProjects(Client client, Config conf) throws Exception {
		List<Project> result = RestUtil.getInstance(Project.class)
				.config(conf, true)
				.path("/rest/api/latest/project/search")
				.method(HttpMethod.GET)
				.pagination(new Paged<Project>(Project.class))
				.requestAllPages();
		return result;
	}

	private static Config parseConfig(CommandLine cli) {
		Config result = null;
		String configFile = cli.getOptionValue(CLI.CONFIG_OPTION);
		ObjectReader reader = OM.readerFor(Config.class);
		Path p = Paths.get(configFile);
		Log.info(LOGGER, "Reading configuration file: [" + p.toAbsolutePath() + "]");
		try (FileReader fr = new FileReader(p.toFile())) {
			result = reader.readValue(fr);
			Log.info(LOGGER, "Config: [" + OM.writeValueAsString(result) + "]");
		} catch (IOException ioex) {
			Log.error(LOGGER, "Unable to read configuration file [" + configFile + "]", ioex);
		}
		return result;
	}

	public static <T> T readFile(String fileName, Class<? extends T> cls) throws IOException, JsonParseException {
		ObjectReader reader = OM.readerFor(cls);
		StringBuilder sb = new StringBuilder();
		Log.info(LOGGER, "Loading file: [" + fileName + "] as [" + cls.getCanonicalName() + "]");
		for (String line : Files.readAllLines(Paths.get(fileName), DEFAULT_CHARSET)) {
			sb.append(line).append(NEWLINE);
		}
		return reader.readValue(sb.toString());
	}

	public static <T> List<T> readValuesFromFile(String fileName, Class<? extends T> cls) 
			throws IOException, JsonParseException {
		List<T> result = new ArrayList<>();
		try {
			ObjectReader reader = OM.readerFor(cls);
			StringBuilder sb = new StringBuilder();
			for (String line : Files.readAllLines(Paths.get(fileName), DEFAULT_CHARSET)) {
				sb.append(line).append(NEWLINE);
			}
			MappingIterator<T> list = reader.readValues(sb.toString());
			while (list.hasNext()) {
				result.add(list.next());
			}
		} catch (Exception ex) {
			throw new IOException("Failed to read JSON from file: [" + fileName + "]", ex);
		}
		return result;
	}

	public static void saveFile(String fileName, Object content) throws IOException {
		saveFile(fileName, content, null);
	}
	public static void saveFile(String fileName, Object content, Class<?> jacksonView) 
			throws IOException {
		try (FileWriter fw = new FileWriter(fileName, DEFAULT_CHARSET)) {
			ObjectWriter writer = null;
			if (jacksonView != null) {
				writer = OM
					.writerWithView(jacksonView)
					.with(new DefaultPrettyPrinter()
							.withObjectIndenter(new DefaultIndenter()
									.withLinefeed(NEWLINE)));
			} else {
				writer = OM.writer(new DefaultPrettyPrinter()
									.withObjectIndenter(new DefaultIndenter()
										.withLinefeed(NEWLINE)));
			}
			String s = writer.writeValueAsString(content);
			fw.write(s);
		}
		Log.info(LOGGER, "File " + fileName + " saved");
	}

	public static String sanitizePath(String s) {
		if (s != null) {
			return s.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
		}
		return s;
	}
	
	private static Map<Filter, String> setFilterEditPermission(Config conf, boolean add, String accountId) 
			throws Exception {
		Map<Filter, String> result = new HashMap<>();
		List<Filter> filterList = JiraObject.getObjects(conf, Filter.class, true);
		// Sort filters into batches to avoid name clashes
		Map<Integer, Map<String, Filter>> batches = new HashMap<>();
		int batchNo = 0;
		while (filterList.size() != 0) {
			batches.put(batchNo, new HashMap<>());
			List<Filter> toRemove = new ArrayList<>();
			for (Filter filter : filterList) {
				if (!batches.get(batchNo).containsKey(filter.getName())) {
					batches.get(batchNo).put(filter.getName(), filter);
					toRemove.add(filter);
				}
			}
			filterList.removeAll(toRemove);
			batchNo++;
		}
		// Process each batch
		for (Map.Entry<Integer, Map<String, Filter>> entry : batches.entrySet()) {
			ExecutorService executorService = Executors.newFixedThreadPool(conf.getThreadCount());
			Map<Filter, Future<String>> batchResult = new HashMap<>();
			for (Filter filter : entry.getValue().values()) {
				Log.info(LOGGER, 
						"Processing batch #" + entry.getKey() + " " + 
						"filter [" + filter.getName() + "] (" + filter.getId() + ")");
				batchResult.put(
						filter, 
						executorService.submit(new EditFilterPermission(conf, add, accountId, filter)));
			}
			// Get results of this batch
			while (batchResult.size() != 0) {
				List<Filter> toRemove = new ArrayList<>();
				for (Map.Entry<Filter, Future<String>> resultEntry : batchResult.entrySet()) {
					try {
						String s = resultEntry.getValue().get(conf.getThreadWait(), TimeUnit.MILLISECONDS);
						result.put(resultEntry.getKey(), s);
						toRemove.add(resultEntry.getKey());
					} catch (TimeoutException tex) {
						// Ignore and keep waiting
					} catch (Exception ex) {
						// Failed to get result from thread
						result.put(resultEntry.getKey(), ex.getMessage());
						toRemove.add(resultEntry.getKey());
					}
				}
				for (Filter f : toRemove) {
					batchResult.remove(f);
				}
			}
			executorService.shutdown();
			while (!executorService.isTerminated()) {
				try {
					executorService.awaitTermination(conf.getThreadWait(), TimeUnit.MILLISECONDS);
				} catch (InterruptedException iex) {
					Log.error(LOGGER, "AwaitTermination interrupted", iex);
				}
			}
		}
		return result;
	}
	
	private static List<FilterValue> getFilterReferences(Clause c) {
		List<FilterValue> result = new ArrayList<>();
		if (c != null) {
			if (c instanceof TerminalClause) {
				TerminalClause tc = (TerminalClause) c;
				String propertyName = c.getName();
				MappingType type = MappingType.parseFilterProperty(propertyName);
				if (MappingType.FILTER.equals(type)) {
					Operand originalOperand = tc.getOperand();
					if (originalOperand instanceof SingleValueOperand) {
						SingleValueOperand svo = (SingleValueOperand) originalOperand;
						result.add(new FilterValue(svo));
					} else if (originalOperand instanceof MultiValueOperand) {
						MultiValueOperand mvo = (MultiValueOperand) originalOperand;
						for (Operand item : mvo.getValues()) {
							if (item instanceof SingleValueOperand) {
								SingleValueOperand svo = (SingleValueOperand) item;
								result.add(new FilterValue(svo));
							}
						}
					} else if (originalOperand instanceof FunctionOperand) {
						FunctionOperand fo = (FunctionOperand) originalOperand;
						for (String s : fo.getArgs()) {
							result.add(new FilterValue(s));
						}
					} // Operand check
				} // If proeprty is filter
			} // Is TerminalClause
			// Process sub-clauses recursively
			if (c.getClauses() != null) {
				for (Clause subclause : c.getClauses()) {
					result.addAll(getFilterReferences(subclause));
				}
			}
		} // Is not null
		return result;
	}
	
	private static String[] analyzeMessage(Exception ex) {
		return analyzeMessage((ex != null)? ex.getMessage() : "");
	}
	private static String[] analyzeMessage(String msg) {
		String[] result = new String[] {"", ""};
		if (msg != null) {
			if (msg.contains("Value not mapped") || 
				msg.contains("Not all values in MultiValueOperand mapped") || 
				msg.contains("Unable to map value for filter")) {
				result[0] = "N";
				result[1] = "Referenced object(s) not in Cloud";
			} else if (	msg.contains("Filter") && 
						msg.contains("is not found")) {
				result[0] = "N";
				result[1] = "Referenced filter(s) not in Cloud";
			} else if (msg.contains("Value not validated")) {
				result[0] = "N";
				result[1] = "Referenced object(s) not in Server";
			} else if (msg.contains("issueFunction is no longer supported")) {
				result[0] = "N";
				result[1] = "Issue function not supported in Cloud";
			} else if (msg.contains("Unrecognized JQL function")) {
				result[0] = "N";
				result[1] = "Plugin-provided issue function not supported in Cloud";
			} else if (msg.contains("owned by unmapped user")) {
				result[0] = "N";
				result[1] = "Filter owned by user not in Cloud";
			} else if (msg.contains("cannot be shared with the public anymore")) {
				result[0] = "N";
				result[1] = "Filter shared to public is no longer supported in Cloud";
			} else if (msg.contains("FilterNotMappedException")) {
				result[0] = "N";
				result[1] = "Referenced filter(s) not in Cloud";
			} else if (	msg.contains("Custom field [") &&
						msg.contains("] cannot be mapped")) {
				result[0] = "N";
				result[1] = "Referenced custom field(s) not in Cloud";
			} else if (msg.contains("cannot be shared with the public anymore")) {
				result[0] = "N";
				result[1] = "Filter/dashboard cannot be shared with public";
			} else if (msg.matches(".+Field '.+' does not exist or you do not have permission to view it.+")) {
				result[0] = "N";
				result[1] = "Custom field is not in Cloud";
			} else if (msg.matches(".+Argument \\[.+\\] not mapped as .+")) {
				result[0] = "N";
				result[1] = "Referenced object(s) not in Cloud";
			} else if (	msg.contains("Issue does not exist or you do not have permission to see it") || 
						msg.contains("No issues have a parent epic with key or name") || 
						msg.matches(".+Issue '.+' could not be found in function.+")) {
				result[0] = "N";
				result[1] = "Referenced issue(s) not in Cloud";
			} else if (msg.contains("Not able to sort using field")) {
				result[0] = "N";
				result[1] = "Field does not exist or does have sort template in Cloud";
			} else if (msg.matches(".+Field '.+' does not support sorting.+")) {
				result[0] = "N";
				result[1] = "Field does not support sorting in Cloud";
			} else if (msg.contains("The operator '=' is not supported by the")) {
				result[0] = "N";
				result[1] = "In Jira Cloud, text fields can only use ~ operator";
			} else if (msg.matches(".+The option '.+' for field '.+' does not exist.+") || 
						msg.matches(".+The value '.+' does not exist for the field.+")) {
				result[0] = "N";
				result[1] = "Option value or custom field not in Cloud";
			} else if (msg.matches(".+Sprint with name '.+' does not exist.+")) {
				result[0] = "N";
				result[1] = "Some Sprints are not migrated by JCMA";
			} else if (	msg.contains("Argument") && 
						msg.contains("not mapped")) {
				result[0] = "N";
				result[1] = "Argument references object not in Cloud";
			}
		}
		return result;
	}
	
	private static void mapFiltersV4(
			Config conf, boolean callApi, boolean overwriteFilter, boolean allValuesMapped) 
			throws Exception {
		// Directories for filter output
		Date now = new Date();
		try (	FileWriter fw = CSV.getCSVFileWriter(MappingType.FILTER.getCSV(now)); 
				CSVPrinter csvPrinter = new CSVPrinter(
						fw, 
						CSV.getCSVWriteFormat(
								Arrays.asList(
										"Owner",
										"Filter Name", 
										"Server Id", 
										"Server JQL",
										"Cloud Id", 
										"Cloud JQL",
										"Action", 
										"Result", 
										"Error",
										"Bug",
										"Notes")
						)
				)) {
			String dirName = new SimpleDateFormat("yyyyMMdd-HHmmss").format(now);
			Path originalDir = Files.createDirectory(Paths.get(dirName + "-OriginalFilter"));
			Path newDir = Files.createDirectory(Paths.get(dirName + "-NewFilter"));
			// Get my account id
			String myAccountId = "";
			// Add self to edit permission for all filters
			if (callApi) {
				Log.info(LOGGER, "Getting account id");
				myAccountId = getUserAccountId(conf, conf.getTargetUser());
				Log.info(LOGGER, "Adding filter edit permission");
				Map<Filter, String> addPermissionResult = setFilterEditPermission(conf, true, myAccountId);
				for (Map.Entry<Filter, String> entry : addPermissionResult.entrySet()) {
					Log.info(LOGGER, 
							"Filter [" + entry.getKey().getName() + "] (" + entry.getKey().getId() + ") " + 
							"Value = [" + entry.getValue() + "]");
					String[] analyzeResult = analyzeMessage(entry.getValue());
					String bug = analyzeResult[0];
					String notes = analyzeResult[1];
					csvPrinter.printRecord(
							entry.getKey().getOwner().getAccountId(),
							entry.getKey().getName(),
							null, null, 
							entry.getKey().getId(), entry.getKey().getJql(), 
							"Add Edit Permission",
							((entry.getValue() != null && entry.getValue().isEmpty())? "Success" : "Fail"),
							entry.getValue(),
							bug, 
							notes);
				}
			}
			Log.info(LOGGER, "Reading DC filters from file");
			// Parse all filters from JSON, separate them into multiple batches based on filter reference
			List<Filter> filterList = readValuesFromFile(MappingType.FILTER.getDC(), Filter.class);
			Map<Filter, List<FilterValue>> filterDependencies = new HashMap<>();
			for (Filter filter : filterList) {
				// Parse and convert JQL
				JqlLexer lexer = new JqlLexer((CharStream) new ANTLRStringStream(filter.getJql()));
				CommonTokenStream cts = new CommonTokenStream(lexer);
				JqlParser parser = new JqlParser(cts);
				query_return qr = parser.query();
				// Find references to filter, name or id
				List<FilterValue> filterReferences = getFilterReferences(qr.clause);
				filterDependencies.put(filter, filterReferences);
			}
			Log.info(LOGGER, "Sorting filters into batches");
			// Sort filters into batches based on filter dependency
			int batchNo = 0;
			Map<Integer, Map<String, Filter>> filterBatches = new TreeMap<>();
			while (!filterDependencies.isEmpty()) {
				filterBatches.put(batchNo, new HashMap<>());
				List<Filter> filterToRemove = new ArrayList<>();
				for (Map.Entry<Filter, List<FilterValue>> entry : filterDependencies.entrySet()) {
					// Look in previous batches for referenced filters, remove them.
					List<FilterValue> refToRemove = new ArrayList<>();
					for (FilterValue ref : entry.getValue()) {
						for (int i = batchNo -1; i >= 0; i--) {
							boolean found = false;
							for (Filter f : filterBatches.get(i).values()) {
								if (ref.equals(f)) {
									found = true;
									refToRemove.add(ref);
									break;
								}
							}
							if (found) {
								break;
							}
						}
					}
					entry.getValue().removeAll(refToRemove);
					// Check for name clash in current batch
					boolean nameClashed = false;
					for (String filterName : filterBatches.get(batchNo).keySet()) {
						if (filterName.equalsIgnoreCase(entry.getKey().getName())) {
							nameClashed = true;
							break;
						}
					}
					if (nameClashed) {
						// Leave this filter for next batch
						continue;
					}
					// If no more reference, add to current batch
					if (entry.getValue().size() == 0) {
						Map<String, Filter> list = filterBatches.get(batchNo);
						list.put(entry.getKey().getName(), entry.getKey());
						filterToRemove.add(entry.getKey());
						filterBatches.put(batchNo, list);
						Log.info(LOGGER, 
								"Batch #" + batchNo + " " + 
								entry.getKey().getName() + " (" + entry.getKey().getId() + ") = " + 
								"[" + entry.getKey().getJql() + "]");
					} 
				}
				// If current batch is empty, that means the remaining filters references invalid filters
				if (filterBatches.get(batchNo).isEmpty()) {
					for (Filter f : filterDependencies.keySet()) {
						String msg = 	
								"Batch #" + batchNo + " " + 
								"Filter [" + f.getName() + "] (" + f.getId() + ") " + 
								"references invalid filters = [" + f.getJql() + "]";
						Log.error(LOGGER, msg);
						csvPrinter.printRecord(
								f.getOwner().getKey(),
								f.getName(),
								f.getId(), f.getJql(), 
								null, null, 
								"Map Filter",
								"Fail",
								msg,
								"N", 
								"References invalid filter(s)");
					}
					// Stop processing
					break;
				}
				// Remove resolved filters
				for (Filter f : filterToRemove) {
					filterDependencies.remove(f);
				}
				// Next batch
				batchNo++;
			}
			Log.info(LOGGER, "Loading object mappings");
			// Load object mappings
			Map<MappingType, Mapping> mappings = loadAllMappings(MappingType.FILTER, MappingType.DASHBOARD);
			// Load object data
			Map<MappingType, List<JiraObject<?>>> data = new HashMap<>();
			for (MappingType type : MappingType.values()) {
				if (type.isIncludeServer()) {
					@SuppressWarnings("unchecked")
					List<JiraObject<?>> list = (List<JiraObject<?>>) 
							readValuesFromFile(type.getDC(), type.getDataClass());
					data.put(type, list);
				}
			}
			// Modify MappingType namesInJQL
			// Get User and Group custom fields, add their names/ids
			final Map<String, MappingType> TARGET_TYPES = new HashMap<>();
			TARGET_TYPES.put(
					"com.atlassian.jira.plugin.system.customfieldtypes:userpicker", MappingType.USER);
			TARGET_TYPES.put(
					"com.atlassian.jira.plugin.system.customfieldtypes:multiuserpicker", MappingType.USER);
			TARGET_TYPES.put(
					"com.atlassian.jira.plugin.system.customfieldtypes:grouppicker", MappingType.GROUP);
			TARGET_TYPES.put(
					"com.atlassian.jira.plugin.system.customfieldtypes:multigrouppicker", MappingType.GROUP);
			for (Object obj : data.get(MappingType.CUSTOM_FIELD)) {
				CustomField cf = (CustomField) obj;
				if (cf.getSchema() != null) {
					String type = cf.getSchema().getCustom();
					if (TARGET_TYPES.containsKey(type)) {
						MappingType mt = TARGET_TYPES.get(type);
						// Custom field name
						mt.getNamesInJQL().add(cf.getName());
						// cf[xxx]
						mt.getNamesInJQL().add("cf[" + cf.getId() + "]");
					}
				}
			}
			Log.info(LOGGER, "Mapping filters");
			// Results of mapped and failed filters
			Mapping result = new Mapping(MappingType.FILTER);
			// Add filter mapping, to be filled as we go
			mappings.put(MappingType.FILTER, result);
			// Launch threads on each batch to map and update JQL
			for (Map.Entry<Integer, Map<String, Filter>> batch : filterBatches.entrySet()) {
				Log.info(LOGGER, "Remapping filters in batch #" + batch.getKey());
				ExecutorService executorService = Executors.newFixedThreadPool(conf.getThreadCount());
				Map<Filter, Future<MapFilterResult>> batchResults = new HashMap<>();
				for (Filter filter : batch.getValue().values()) {
					MapFilter mapFilter = new MapFilter(
							conf, myAccountId, 
							originalDir, newDir, 
							mappings, data, 
							filter, 
							callApi, allValuesMapped, overwriteFilter);
					batchResults.put(filter, executorService.submit(mapFilter));
				}
				// Get result from future
				while (batchResults.size() != 0) {
					List<Filter> toRemove = new ArrayList<>();
					for (Map.Entry<Filter, Future<MapFilterResult>> entry : batchResults.entrySet()) {
						try {
							Future<MapFilterResult> future = entry.getValue();
							MapFilterResult r = future.get(conf.getThreadWait(), TimeUnit.MILLISECONDS);
							if (r != null) {
								toRemove.add(entry.getKey());
								String[] analyzeResult = analyzeMessage(r.getException());
								String bug = analyzeResult[0];
								String notes = analyzeResult[1];
								if (r.getNewOwner() != null) {
									csvPrinter.printRecord(
											r.getOriginal().getOwner().getKey(),
											r.getOriginal().getName(),
											r.getOriginal().getId(), r.getOriginal().getJql(), 
											(r.getTarget() != null? r.getTarget().getId() : ""),
											(r.getTarget() != null? r.getTarget().getJql() : ""),
											"Owner changed",
											"Warning",
											"Owner changed from " + 
												"[" + r.getOriginal().getOwner().getKey() + "] to " + 
												"[" + r.getNewOwner() + "]",
											"", 
											""
											);
								}
								for (DataCenterPermission perm : r.getRemovedSharePermissions()) {
									csvPrinter.printRecord(
											r.getOriginal().getOwner().getKey(),
											r.getOriginal().getName(),
											r.getOriginal().getId(), r.getOriginal().getJql(), 
											(r.getTarget() != null? r.getTarget().getId() : ""),
											(r.getTarget() != null? r.getTarget().getJql() : ""),
											"Share Permission omitted",
											"Warning",
											perm,
											"", 
											""
											);
								}
								for (DataCenterPermission perm : r.getRemovedEditPermissions()) {
									csvPrinter.printRecord(
											r.getOriginal().getOwner().getKey(),
											r.getOriginal().getName(),
											r.getOriginal().getId(), r.getOriginal().getJql(), 
											(r.getTarget() != null? r.getTarget().getId() : ""),
											(r.getTarget() != null? r.getTarget().getJql() : ""),
											"Edit Permission omitted",
											"Warning",
											perm,
											"", 
											""
											);
								}
								csvPrinter.printRecord(
										r.getOriginal().getOwner().getKey(),
										r.getOriginal().getName(),
										r.getOriginal().getId(), r.getOriginal().getJql(), 
										(r.getTarget() != null? r.getTarget().getId() : ""),
										(r.getTarget() != null? r.getTarget().getJql() : ""),
										"Map filter",
										(r.getException() == null? "Success" : "Fail"),
										(r.getException() == null? "" : r.getException().getMessage()),
										bug, 
										notes
										);
								Log.info(LOGGER, 
										"Mapped filter [" + r.getOriginal().getName() + "] " + 
										"(" + r.getOriginal().getId() + ") " + 
										"[" + r.getOriginal().getJql() + "] => " + 
										"[" + (r.getTarget() != null? r.getTarget().getId() : "") + "]",
										"[" + (r.getTarget() != null? r.getTarget().getJql() : "") + "]");
							}
						} catch (TimeoutException tex) {
							// Ignore
						} catch (Exception ex) {
							String[] analyzeResult = analyzeMessage(ex);
							String bug = analyzeResult[0];
							String notes = analyzeResult[1];
							toRemove.add(entry.getKey());
							csvPrinter.printRecord(
									entry.getKey().getOwner().getKey(),
									entry.getKey().getName(),
									entry.getKey().getId(), entry.getKey().getJql(), 
									"", "",
									"Map filter",
									"Fail",
									ex.getMessage(),
									bug,
									notes
									);
							Log.error(LOGGER, 
									"Filter mapping thread execution failed for " + 
									"Filter [" + entry.getKey().getName() + "] (" + entry.getKey().getId() + ")", 
									ex);
						}
					}
					for (Filter f : toRemove) {
						batchResults.remove(f);
					}
				}
				// Wait for shutdown
				executorService.shutdown();
				while (!executorService.isTerminated()) {
					try {
						executorService.awaitTermination(conf.getThreadWait(), TimeUnit.MILLISECONDS);
					} catch (InterruptedException iex) {
						Log.error(LOGGER, "Map filter wait interrupted", iex);
					}
				}
			}
			// Remove self from edit permission for all filters
			if (callApi) {
				Log.info(LOGGER, "Removing filter edit permission");
				Map<Filter, String> removePermissionResult = setFilterEditPermission(conf, false, myAccountId);
				for (Map.Entry<Filter, String> entry : removePermissionResult.entrySet()) {
					Log.info(LOGGER, 
							"Filter [" + entry.getKey().getName() + "] (" + entry.getKey().getId() + ") " + 
							"Value = [" + entry.getValue() + "]");
					String[] analyzeResult = analyzeMessage(entry.getValue());
					String bug = analyzeResult[0];
					String notes = analyzeResult[1];
					csvPrinter.printRecord(
							entry.getKey().getOwner().getAccountId(),
							entry.getKey().getName(),
							null, null, 
							entry.getKey().getId(), entry.getKey().getJql(), 
							"Remove Edit Permission",
							((entry.getValue() != null && entry.getValue().isEmpty())? "Success" : "Fail"),
							entry.getValue(),
							bug, 
							notes);
				}
			}
		}
	}
	
	/**
	 * Dashboard and Filter from Server requires DB access to dump, so this is needed outside of mapObjectsV2().
	 */
	private static void dumpFilterAndDashboard(Config conf) throws Exception {
		SqlSessionFactory factory = setupMyBatis(conf);
		try (SqlSession session = factory.openSession()) {
			// Get filter info from source
			FilterMapper filterMapper = session.getMapper(FilterMapper.class);
			Log.info(LOGGER, "Dumping filters and dashboards from Data Center...");
			Log.info(LOGGER, "Processing Filters...");
			List<Integer> filters = filterMapper.getFilters();
			List<Filter> filterList = new ArrayList<>();
			for (Integer id : filters) {
				List<Filter> filter = JiraObject.getObjects(conf, Filter.class, false, id);
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
	
	private static void dumpObjects(
			Config conf, boolean cloud, List<MappingType> requestedTypes) throws Exception {
		for (MappingType type : MappingType.values()) {
			if (requestedTypes.contains(type)) {
				Class<?> dataClass = null;
				// Always exclude dashboard and filters
				// For cloud they are not exported
				// For server they require database
				if (type == MappingType.DASHBOARD || 
					(!cloud && type == MappingType.FILTER)) {
					continue;
				}
				if (cloud && type.isIncludeCloud()) {
					dataClass = type.getDataClass();
				} else if (!cloud && type.isIncludeServer()) {
					dataClass = type.getDataClass();
				}
				if (dataClass != null) {
					Log.info(LOGGER, "Processing " + type.getName() + "...");
					@SuppressWarnings({ "unchecked" })
					List<? extends JiraObject<?>> list = 
						(List<? extends JiraObject<?>>) JiraObject.getObjects(conf, dataClass, cloud);
					Log.info(LOGGER, type + ": " + list.size() + " object(s) found");
					saveFile((cloud? type.getCloud() : type.getDC()), list);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <U extends JiraObject<U>> void mapObjectsV2(boolean exactMatch, List<MappingType> types) 
			throws Exception {
		Log.info(LOGGER, "Mapping objects between Data Center and Cloud...");
		for (MappingType type : types) {
			Log.info(LOGGER, "Processing mapping for " + type);
			int mappedCount = 0;
			Mapping mapping = new Mapping(type);
			if (type.isIncludeCloud() && type.isIncludeServer()) {
				List<U> serverObjects =
						(List<U>) readValuesFromFile(type.getDC(), type.getDataClass());
				List<U> cloudObjects =
						(List<U>) readValuesFromFile(type.getCloud(), type.getDataClass());
				for (U server : serverObjects) {
					List<U> targets = new ArrayList<>();
					for (U cloud : cloudObjects) {
						if (server.compareTo(cloud, exactMatch) == 0) {
							targets.add(cloud);
						}
					}
					switch (targets.size()) {
					case 0:
						mapping.getUnmapped().add(server);
						Log.warn(LOGGER, type + " [" + server.getInternalId() + "] is not mapped");
						break;
					case 1: 
						mapping.getMapped().put(server.getInternalId(), targets.get(0));
						mappedCount++;
						break;
					case 2:
						// Problem: target stores uniqueName only
						// If only 2 matches and only one has (migrated), map to it
						int migratedCount = 0;
						U mappedObject = null;
						for (U obj : targets) {
							if (obj.isMigrated()) {
								migratedCount++;
								mappedObject = obj;
								break;
							}
						}
						if (migratedCount == 1 && mappedObject != null) {
							mapping.getMapped().put(server.getInternalId(), mappedObject);
							mappedCount++;
							break;
						}
						// Fall-through to default
					default:
						List<JiraObject<?>> conflicts = new ArrayList<>();
						for (U item : targets) {
							conflicts.add(item);
						}
						mapping.getConflict().put(server.getInternalId(), conflicts);
						Log.warn(LOGGER, 
								type + " [" + server.getInternalId() + "] is mapped to multiple Cloud objects");
						break;
					}
				}
				Log.printCount(LOGGER, type + " mapped: ", mappedCount, serverObjects.size());
				saveFile(type.getMap(), mapping);
			}	// If type is exported for both server and cloud
		}	// For all types
	}
	
	private static String[] analyzeDashboardResult(String msg) {
		String bug = "";
		String notes = "";
		if (msg != null) {
			if (msg.contains("rest/gadgets/1.0/g/com.thed.zephyr.je:zephyr-je-gadget-cycle-execution-status/gadgets/cycle-execution-status.xml")) {
				bug = "N";
				notes = "Zephyr Squad gadget Test Execution Progress not supported in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/com.arsenalesystems.dataplane:")) {
				bug = "N";
				notes = "AppFire gadget Dataplane Reports is not installed in Cloud";
			} else if (msg.contains("owned by user [") && msg.contains("] not found in Cloud")) {
				bug = "N";
				notes = "Owner is not a user in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/com.atlassian.jira.gadgets:text-gadget/gadgets/text-gadget.xml")) {
				bug = "N";
				notes = "Text gadget is not supported in Cloud";
			} else if (msg.contains("Dashboards and filters in this Jira instance cannot be shared with the public anymore")) {
				bug = "N";
				notes = "Dashboard cannot be shared to public";
			} else if (msg.contains("rest/gadgets/1.0/g/com.akelesconsulting.jira.plugins.GaugeGadget:")) {
				bug = "N";
				notes = "Gauge Gadget is not installed in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/com.idalko.pivotgadget:")) {
				bug = "N";
				notes = "Idalko Pivot Gadget is not installed in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/com.atlassian.jirafisheyeplugin:crucible-charting-gadget/gadgets/crucible-charting-gadget.xml")) {
				bug = "N";
				notes = "Crucible Chart is not supported in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/performance-objectives-for-jira:")) {
				bug = "N";
				notes = "Performance Objectives is not installed in Cloud";
			} else if (msg.contains("com.jiraworkcalendar.ujg:ujg-item")) {
				bug = "N";
				notes = "Jira Calendar Plugin is not supported in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/com.coresoftlabs.sla_powerbox.sla_powerbox:")) {
				bug = "N";
				notes = "SLA Powerbox has no gadgets in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/com.codedoers.jira.smart-ql:")) {
				bug = "N";
				notes = "SmartQL is not supported in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/com.pyxis.greenhopper.jira:greenhopper-gadget-version-report/gadgets/greenhopper-version-report.xml")) {
				bug = "N";
				notes = "Version Report gadget is not supported in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/com.pyxis.greenhopper.jira:greenhopper-gadget-rapid-view/gadgets/greenhopper-rapid-view.xml")) {
				bug = "N";
				notes = "Agile Wallboard gadget is not supported in Cloud";
			} else if (msg.contains("rest/gadgets/1.0/g/burn-up-chart:estimate-react-gadget/estgadget/estimate-gadget.xml")) {
				bug = "N";
				notes = "Advanced Burndown Chart Dashboard Gadget for Jira is not installed in Cloud";
			} else if (msg.contains("has no mapping")) {
				bug = "N";
				notes = "The gadget has no configuration to map";
			} else if (msg.contains("is not configured")) {
				bug = "N";
				notes = "The gadget is not recognized";
			}
		}
		return new String[] { bug, notes };
	}
	
	private static void createDashboardsV2(Config config) throws Exception {
		Log.info(LOGGER, "Creating dashboards...");
		String myAccountId = getUserAccountId(config, config.getTargetUser());
		List<DataCenterPortalPage> dashboards = readValuesFromFile(MappingType.DASHBOARD.getDC(),
				DataCenterPortalPage.class);
		Log.info(LOGGER, "Loading object mappings");
		// Split dashboards in batches according to name, to avoid name clash
		Map<Integer, Map<String, DataCenterPortalPage>> batches = new HashMap<>();
		int batchNo = 0;
		while (dashboards.size() != 0) {
			batches.put(batchNo, new HashMap<>());
			List<DataCenterPortalPage> toRemove = new ArrayList<>();
			for (DataCenterPortalPage dashboard : dashboards) {
				if (!batches.get(batchNo).containsKey(dashboard.getPageName())) {
					batches.get(batchNo).put(dashboard.getPageName(), dashboard);
					toRemove.add(dashboard);
				} // Wait for next batch
			}
			// Next batch
			dashboards.removeAll(toRemove);
			batchNo++;
		}
		// Load object mappings
		Map<MappingType, Mapping> mappings = loadAllMappings(MappingType.DASHBOARD);
		// Load object data
		Map<MappingType, List<JiraObject<?>>> data = new HashMap<>();
		for (MappingType type : MappingType.values()) {
			if (type.isIncludeServer()) {
				@SuppressWarnings("unchecked")
				List<JiraObject<?>> list = (List<JiraObject<?>>) 
						readValuesFromFile(type.getDC(), type.getDataClass());
				data.put(type, list);
			}
		}
		// Start threads
		Date now = new Date();
		String dirName = new SimpleDateFormat("yyyyMMdd-HHmmss").format(now);
		Path originalDir = Files.createDirectory(Paths.get(dirName + "-OriginalDashboard"));
		Path newDir = Files.createDirectory(Paths.get(dirName + "-NewDashboard"));
		CSVFormat csvFormat = CSV.getCSVWriteFormat(Arrays.asList(
				"Owner", 
				"Server ID", "Server Name", 
				"Cloud ID", "Cloud Name", 
				"Action",
				"Gadget",
				"Cloud Gadget ID",
				"Configuration",
				"Result",
				"Message",
				"Bug",
				"Notes"
				));
		try (	FileWriter fw = new FileWriter(MappingType.DASHBOARD.getCSV(now)); 
				CSVPrinter csv = new CSVPrinter(fw, csvFormat)) {
			for (Map<String, DataCenterPortalPage> currentBatch : batches.values()) {
				// For each batch			
				ExecutorService service = Executors.newFixedThreadPool(config.getThreadCount());
				Map<DataCenterPortalPage, Future<CreateDashboardResult>> futureMap = new HashMap<>();
				for (DataCenterPortalPage dashboard : currentBatch.values()) {
					futureMap.put(dashboard, service.submit(
							new CreateDashboard(
									config, myAccountId, 
									mappings, data, 
									dashboard, originalDir, newDir)));
				}
				// Wait for results
				while (futureMap.size() != 0) {
					Log.info(LOGGER, "Waiting for threads to end, remaining count: " + futureMap.size());
					List<DataCenterPortalPage> toRemove = new ArrayList<>();
					for (Map.Entry<DataCenterPortalPage, Future<CreateDashboardResult>> entry : 
						futureMap.entrySet()) {
						CreateDashboardResult result = null;
						Future<CreateDashboardResult> future = entry.getValue();
						try {
							result = future.get(config.getThreadWait(), TimeUnit.MILLISECONDS);
							toRemove.add(entry.getKey());
						} catch (TimeoutException tex) {
							// Ignore and wait
							Log.info(LOGGER, "Thread for [" + entry.getKey().getPageName()  + "] still running");
						} catch (Exception ex) {
							Log.error(LOGGER, "Thread execution for [" + entry.getKey().getPageName() + "] failed", ex);
							result = new CreateDashboardResult();
							result.setOriginalDashboard(entry.getKey());
							result.setCreateDashboardResult(
									"Thread execution for [" + entry.getKey().getPageName()  + "] " + 
									"failed: " + ex.getMessage());
							toRemove.add(entry.getKey());
						}
						if (result != null) {
							Log.info(LOGGER, "Processing output for [" + entry.getKey().getPageName() + "]");
							if (result.getNewOwner() != null) {
								csv.printRecord(
										result.getOriginalDashboard().getUsername(),
										result.getOriginalDashboard().getId(),
										result.getOriginalDashboard().getPageName(),
										(result.getCreatedDashboard() != null? result.getCreatedDashboard().getId(): ""),
										(result.getCreatedDashboard() != null? result.getCreatedDashboard().getName(): ""),
										"Owner Change",
										"N/A",
										"N/A",
										"N/A",
										"Warning",
										"Owner changed from " +
												"[" + result.getOriginalDashboard().getUsername() + "]" + 
												" to " + 
												"[" + result.getNewOwner() + "]",
										"N",
										""
										);
							}
							for (CloudPermission permission : result.getSharePermissionOmitted()) {
								csv.printRecord(
										result.getOriginalDashboard().getUsername(),
										result.getOriginalDashboard().getId(),
										result.getOriginalDashboard().getPageName(),
										(result.getCreatedDashboard() != null? result.getCreatedDashboard().getId(): ""),
										(result.getCreatedDashboard() != null? result.getCreatedDashboard().getName(): ""),
										"Omitted Share Permission",
										"N/A",
										"N/A",
										"N/A",
										"Warning",
										permission.toString(),
										"N",
										""
										);
							}
							for (CloudPermission permission : result.getEditPermissionOmitted()) {
								csv.printRecord(
										result.getOriginalDashboard().getUsername(),
										result.getOriginalDashboard().getId(),
										result.getOriginalDashboard().getPageName(),
										(result.getCreatedDashboard() != null? result.getCreatedDashboard().getId(): ""),
										(result.getCreatedDashboard() != null? result.getCreatedDashboard().getName(): ""),
										"Omitted Edit Permission",
										"N/A",
										"N/A",
										"N/A",
										"Warning",
										permission.toString(),
										"N",
										""
										);
							}
							if (result.getCreateDashboardResult() != null) {
								String[] analyze = analyzeDashboardResult(result.getCreateDashboardResult());
								String bug = analyze[0];
								String notes = analyze[1];
								csv.printRecord(
										result.getOriginalDashboard().getUsername(),
										result.getOriginalDashboard().getId(),
										result.getOriginalDashboard().getPageName(),
										(result.getCreatedDashboard() != null? result.getCreatedDashboard().getId(): ""),
										(result.getCreatedDashboard() != null? result.getCreatedDashboard().getName(): ""),
										"Create dashboard",
										"N/A",
										"N/A",
										"N/A",
										(CreateDashboardResult.SUCCESS.equals(
												result.getCreateDashboardResult())? "Success" : "Fail"),
										result.getCreateDashboardResult(),
										bug,
										notes
										);
							}
							for (Map.Entry<String, CreateGadgetResult> gadgetEntry : 
								result.getCreateGadgetResults().entrySet()) {
								String gadgetIdentifier = gadgetEntry.getKey();
								String gadgetId = "";
								if (gadgetEntry.getValue().getCreatedGadget() != null) {
									gadgetId = gadgetEntry.getValue().getCreatedGadget().getId();
								}
								CreateGadgetResult r = gadgetEntry.getValue();
								String[] analyze = analyzeDashboardResult(r.getCreateResult());
								String bug = analyze[0];
								String notes = analyze[1];
								csv.printRecord(
										result.getOriginalDashboard().getUsername(),
										result.getOriginalDashboard().getId(),
										result.getOriginalDashboard().getPageName(),
										(result.getCreatedDashboard() != null? 
												result.getCreatedDashboard().getId(): ""),
										(result.getCreatedDashboard() != null? 
												result.getCreatedDashboard().getName(): ""),
										"Create gadget",
										gadgetIdentifier,
										gadgetId,
										"N/A",
										(CreateDashboardResult.SUCCESS.equals(
												r.getCreateResult())? "Success" : "Fail"),
										r.getCreateResult(),
										bug,
										notes
										);
								for (String warning : gadgetEntry.getValue().getMappingWarnings()) {
									String[] analyze1 = analyzeDashboardResult(warning);
									String bug1 = analyze1[0];
									String notes1 = analyze1[1];
									csv.printRecord(
											result.getOriginalDashboard().getUsername(),
											result.getOriginalDashboard().getId(),
											result.getOriginalDashboard().getPageName(),
											(result.getCreatedDashboard() != null? 
													result.getCreatedDashboard().getId(): ""),
											(result.getCreatedDashboard() != null? 
													result.getCreatedDashboard().getName(): ""),
											"Map gadget configuration",
											gadgetIdentifier,
											gadgetId,
											"N/A",
											"Warning",
											warning,
											bug1,
											notes1
											);
								}
								for (String error : gadgetEntry.getValue().getMappingErrors()) {
									String[] analyze1 = analyzeDashboardResult(error);
									String bug1 = analyze1[0];
									String notes1 = analyze1[1];
									csv.printRecord(
											result.getOriginalDashboard().getUsername(),
											result.getOriginalDashboard().getId(),
											result.getOriginalDashboard().getPageName(),
											(result.getCreatedDashboard() != null? 
													result.getCreatedDashboard().getId(): ""),
											(result.getCreatedDashboard() != null? 
													result.getCreatedDashboard().getName(): ""),
											"Map gadget configuration",
											gadgetIdentifier,
											gadgetId,
											"N/A",
											"Fail",
											error,
											bug1,
											notes1
											);
								}
								for (Map.Entry<String, String> configEntry : 
									r.getConfigurationResult().entrySet()) {
									String[] analyze1 = analyzeDashboardResult(configEntry.getValue());
									String bug1 = analyze1[0];
									String notes1 = analyze1[1];
									csv.printRecord(
											result.getOriginalDashboard().getUsername(),
											result.getOriginalDashboard().getId(),
											result.getOriginalDashboard().getPageName(),
											(result.getCreatedDashboard() != null? 
													result.getCreatedDashboard().getId(): ""),
											(result.getCreatedDashboard() != null? 
													result.getCreatedDashboard().getName(): ""),
											"Configure gadget",
											gadgetIdentifier,
											gadgetId,
											configEntry.getKey(),
											(CreateDashboardResult.SUCCESS.equals(
													configEntry.getValue())? "Success" : "Fail"),
											configEntry.getValue(),
											bug1,
											notes1
											);
								}
							}
							if (result.getChangeOwnerResult() != null) {
								// Dashboard change owner
								String[] analyze = analyzeDashboardResult(result.getChangeOwnerResult());
								String bug = analyze[0];
								String notes = analyze[1];
								csv.printRecord(
										result.getOriginalDashboard().getUsername(),
										result.getOriginalDashboard().getId(),
										result.getOriginalDashboard().getPageName(),
										(result.getCreatedDashboard() != null? 
												result.getCreatedDashboard().getId(): ""),
										(result.getCreatedDashboard() != null? 
												result.getCreatedDashboard().getName(): ""),
										"Change dashboard owner",
										"N/A",
										"N/A",
										"N/A",
										(CreateDashboardResult.SUCCESS.equals(
												result.getChangeOwnerResult())? "Success" : "Fail"),
										result.getChangeOwnerResult(),
										bug, 
										notes
										);
							}
							Log.info(LOGGER, "Processed output for [" + entry.getKey().getPageName() + "]");
						}
					}
					for (DataCenterPortalPage item : toRemove) {
						Log.info(LOGGER, "Thread for [" + item.getPageName() + "] ended");
						futureMap.remove(item);
					}
				}
				// Shutdown
				Log.info(LOGGER, "Shutting down execution service");
				service.shutdownNow();
			}	// For each batch
		}	// CSV try
	}
	
	public static Mapping loadMapping(MappingType include) throws Exception {
		Mapping result = null;
		if (Files.exists(Paths.get(include.getMap()))) {
			result = readFile(include.getMap(), Mapping.class);
		} else {
			Log.warn(LOGGER, "Mapping [" + include.getMap() + "] cannot be loaded");
		}
		return result;
	}
	
	public static Map<MappingType, Mapping> loadAllMappings(MappingType... excludes) throws Exception {
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
				String accountId = csvUsers.get(src.getEmailAddress());
				User u = new User();
				u.setAccountId(accountId);
				userMapping.getMapped().put(src.getKey(), u);
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
		List<Filter> result = RestUtil.getInstance(Filter.class)
				.config(conf, true)
				.path("/rest/api/latest/filter/search")
				.method(HttpMethod.GET)
				.pagination(new Paged<Filter>(Filter.class))
				.requestAllPages();
		saveFile(MappingType.FILTER.getList(), result);
		Log.info(LOGGER, "Filters found: " + result.size());
		Log.info(LOGGER, "Filter list completed");
	}

	private static void listDashboard(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "List dashboards from Cloud...");
		List<Dashboard> result = RestUtil.getInstance(Dashboard.class)
				.config(conf, true)
				.path("/rest/api/latest/dashboard")
				.method(HttpMethod.GET)
				.pagination(new Paged<Dashboard>(Dashboard.class).valuesProperty("dashboards"))
				.requestAllPages();
		saveFile(MappingType.DASHBOARD.getList(), result);
		Log.info(LOGGER, "Dashboards found: " + result.size());
		Log.info(LOGGER, "Dashboard list completed");
	}

	private static void deleteFilter(Client cloudClient, Config conf) throws Exception {
		Log.info(LOGGER, "Deleting migrated filters...");
		Mapping filters = readFile(MappingType.FILTER.getMap(), Mapping.class);
		int deletedCount = 0;
		RestUtil<Object> util = RestUtil.getInstance(Object.class).config(conf, true);
		for (Map.Entry<String, JiraObject<?>> filter : filters.getMapped().entrySet()) {
			Filter f = (Filter) filter.getValue();
			Response resp = util.path("/rest/api/latest/filter/{filterId}")
					.pathTemplate("filterId", f.getId())
					.method(HttpMethod.DELETE)
					.status()
					.request();
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
		RestUtil<Object> util = RestUtil.getInstance(Object.class).config(conf, true);
		for (Map.Entry<String, JiraObject<?>> dashboard : dashboards.getMapped().entrySet()) {
			Dashboard db = (Dashboard) dashboard.getValue();
			Response resp = util.path("/rest/api/latest/dashboard/{boardId}")
					.pathTemplate("boardId", db.getId())
					.method(HttpMethod.DELETE)
					.status()
					.request();
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
	
	/**
	 * A subclass to remove curly brackets added by ChangedClauseImpl.toString().
	 */
	public static class MyChangedClause extends ChangedClauseImpl {
		private static final long serialVersionUID = 1L;
		public MyChangedClause(ChangedClause clause) {
			super(clause);
		}
		public MyChangedClause(String field, Operator operator, HistoryPredicate historyPredicate) {
			super(field, operator, historyPredicate);
		}
		@Override
		public String toString() {
			String s = super.toString();
			// Remove curly brackets added by ChangedClauseImpl
			if (s.startsWith("{") && s.endsWith("}")) {
				return s.substring(1, s.length() - 1);
			}
			return s;
		}
	}

	private static String getUserAccountId(Config config, String userEmail) throws Exception {
		RestUtil<User> userUtil = RestUtil.getInstance(User.class).config(config, true);
		List<User> userList = userUtil.path("/rest/api/latest/user/picker")
				.method(HttpMethod.GET)
				.query("query", config.getTargetUser())
				.pagination(new Paged<User>(User.class).maxResults(1).valuesProperty("users"))
				.requestAllPages();
		if (userList.size() == 1) {
			return userList.get(0).getAccountId();
		}
		return null;
	}
	
	private static void assignProjectRoles(
			Client client, Config config, String accountId, String[] roleNames, boolean grant) 
			throws Exception {
		RestUtil<Object> util = RestUtil.getInstance(Object.class).config(config, true);
		Pattern pattern = Pattern.compile("^.+/([0-9]+)$");
		// Get account if null
		if (accountId == null) {
			accountId = getUserAccountId(config, config.getTargetUser());
		}
		// Get project list
		List<Project> projectList = getCloudProjects(client, config);
		for (Project project : projectList) {
			// For each project, get role list
			Response resp = util
					.path("/rest/api/latest/project/{projectId}/role")
					.pathTemplate("projectId", project.getId())
					.method(HttpMethod.GET)
					.pagination(new SinglePage<Object>(Object.class))
					.status()
					.request();
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
							// Put user in post data for grant
							List<String> userList = new ArrayList<>();
							userList.add(accountId);
							payload.put("user", userList);
						} else {
							// Put user in query for revoke
							query.put("user", accountId);
						}
						Response resp2 = util.path("/rest/api/latest/project/{projectId}/role/{roleId}")
								.pathTemplate("projectId", project.getId())
								.pathTemplate("roleId", roleId)
								.method((grant? HttpMethod.POST : HttpMethod.DELETE))
								.query((grant? null : query))
								.payload((grant? payload : null))
								.status()
								.request();
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
	
	private static List<MappingType> parseMappingTypes(boolean cloud, String... objectTypes) {
		List<MappingType> result = new ArrayList<>();
		if (objectTypes != null) {
			Set<MappingType> requestedTypes = new HashSet<>();
			for (String s : objectTypes) {
				MappingType type = MappingType.parse(s);
				if (type != null && 
					(	(cloud && type.isIncludeCloud()) || 
						(!cloud && type.isIncludeServer())
					)) {
					requestedTypes.add(type);
					if (type.getDependencies() != null) {
						for (MappingType depend : type.getDependencies()) {
							requestedTypes.add(depend);
							Log.info(LOGGER, 
									"Object type [" + depend + "] added " + 
									"as it is required by [" + type + "]");
						}
					}
				} else {
					Log.error(LOGGER, "Invalid object type [" + s + "] ignored");
				}
			}
			// Sort
			for (MappingType type : MappingType.values()) {
				if (requestedTypes.contains(type)) {
					result.add(type);
				}
			}
		} else {
			// Add all
			for (MappingType type : MappingType.values()) {
				if ((cloud && type.isIncludeCloud()) || (!cloud && type.isIncludeServer())) {
					result.add(type);
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		for (MappingType type : result) {
			sb.append(",").append(type);
		}
		if (sb.length() != 0) {
			sb.delete(0, 1);
		}
		Log.info(LOGGER, "Object types to export: " + sb.toString());
		return result;
	}
	
	private static void resetFilter(Config conf, String listFile) throws Exception {
		RestUtil<Filter> util = RestUtil.getInstance(Filter.class).config(conf, true);
		String myAccountId = getUserAccountId(conf, conf.getTargetUser());
		final Pattern NAME_PATTERN = Pattern.compile("(.+?)-" + myAccountId);
		List<Filter> filterList = readValuesFromFile(listFile, Filter.class);
		for (Filter filter : filterList) {
			Response resp;
			Map<String, Object> payload = new HashMap<>();
			// Find filter id by name
			List<Filter> filtersFound = util.path("/rest/api/latest/filter/search")
				.query("overrideSharePermissions", true)
				.query("filterName", "\"" + filter.getName() + "\"")
				.method(HttpMethod.GET)
				.pagination(new Paged<Filter>(Filter.class).maxResults(1))
				.requestAllPages();
			if (filtersFound.size() != 0) {
				Filter found = filtersFound.get(0);
				Log.info(LOGGER, "Filter [" + filter.getName() + "] found: " + found.getId());
				filter.setId(found.getId());
			} else {
				Log.error(LOGGER, "Filter [" + filter.getName() + "] cannot be found");
				continue;
			}
			// Change owner to self
			payload.clear();
			payload.put("accountId", myAccountId);
			resp = util
					.path("/rest/api/latest/filter/{filterId}/owner")
					.pathTemplate("filterId", filter.getId())
					.method(HttpMethod.PUT)
					.payload(payload)
					.status()
					.request();
			if (checkStatusCode(resp, Status.NO_CONTENT)) {
				Log.info(LOGGER, "Changed filter (" + filter.getId() + ") " + 
						"owner to [" + myAccountId + "] ");
			} else if (checkStatusCode(resp, Status.BAD_REQUEST)) {
				Log.info(LOGGER, "Filter (" + filter.getId() + ") " + 
						"already owned by [" + myAccountId + "] ");
			} else {
				Log.error(LOGGER, "Unable to change filter (" + filter.getId() + ") " + 
						"owner to [" + myAccountId + "]: (" + resp.getStatus() + ") " + 
						resp.readEntity(String.class));
				continue;
			}
			// Rename
			payload.clear();
			Matcher matcher = NAME_PATTERN.matcher(filter.getName());
			if (matcher.matches()) {
				payload.put("name", matcher.group(1));
				resp = util
						.path("/rest/api/latest/filter/{filterId}")
						.pathTemplate("filterId", filter.getId())
						.method(HttpMethod.PUT)
						.payload(payload)
						.status()
						.request();
				if (!checkStatusCode(resp, Status.OK)) {
					Log.error(LOGGER, "Unable to rename filter (" + filter.getId() + ") " + 
							"to [" + matcher.group(1) + "]: (" + resp.getStatus() + ") " + 
							resp.readEntity(String.class));
					continue;
				} else {
					Log.info(LOGGER, "Renamed filter (" + filter.getId() + ") " + 
							"to [" + matcher.group(1) + "]");
				}
			}
			// Change owner to originalOwner
			payload.clear();
			payload.put("accountId", filter.getOriginalOwner().getAccountId());
			resp = util
					.path("/rest/api/latest/filter/{filterId}/owner")
					.pathTemplate("filterId", filter.getId())
					.method(HttpMethod.PUT)
					.payload(payload)
					.status()
					.request();
			if (!checkStatusCode(resp, Status.OK)) {
				Log.error(LOGGER, "Unable to change filter (" + filter.getId() + ") " + 
						"owner to [" + filter.getOriginalOwner().getAccountId() + "]: (" + resp.getStatus() + ") " + 
						resp.readEntity(String.class));
				continue;
			} else {
				Log.info(LOGGER, "Changed filter (" + filter.getId() + ") " + 
						"owner to [" + filter.getOriginalOwner().getAccountId() + "]");
			}
		}
	}
	
	private static void modifyFilterPermission(Config conf, boolean add) throws Exception {
		String myAccountId = "";
		// Add self to edit permission for all filters
		Log.info(LOGGER, "Getting account id");
		myAccountId = getUserAccountId(conf, conf.getTargetUser());
		String action = "Add";
		if (!add) {
			action = "Remove";
		}
		Log.info(LOGGER, action + " filter edit permission");
		Map<Filter, String> modifyPermissionResult = setFilterEditPermission(conf, add, myAccountId);
		for (Map.Entry<Filter, String> entry : modifyPermissionResult.entrySet()) {
			Log.info(LOGGER, 
					"Filter [" + entry.getKey().getName() + "] (" + entry.getKey().getId() + ") " + 
					"Action = " + action + 
					"Result = " + 
					((entry.getValue() != null && entry.getValue().isEmpty())? "Success" : "Fail"));
		}
	}
	
	private static void deleteMyDashboard(Config conf) throws Exception {
		String accountId = getUserAccountId(conf, conf.getTargetUser());
		RestUtil<CloudDashboard> util = RestUtil
				.getInstance(CloudDashboard.class)
				.config(conf, true);
		Log.info(LOGGER, "Getting dashbords owned by current user [" + accountId + "]...");
		List<CloudDashboard> list = util.path("/rest/api/latest/dashboard/search")
								.method(HttpMethod.GET)
								.query("accountId", accountId)
								.pagination(new Paged<CloudDashboard>(CloudDashboard.class).maxResults(100))
								.requestAllPages();
		Log.info(LOGGER, "Dashboard count: " + list.size());
		int deleteCount = 0;
		for (CloudDashboard board : list) {
			Response resp = util.path("/rest/api/latest/dashboard/{id}")
								.pathTemplate("id", board.getId())
								.method(HttpMethod.DELETE)
								.status()
								.request();
			if (checkStatusCode(resp, Status.OK)) {
				deleteCount++;
				Log.info(LOGGER, "Dashboard [" + board.getName() + "] (" + board.getId() + ") deleted");
			} else {
				Log.info(LOGGER, "Failed to delete dashboard [" + board.getName() + "] (" + board.getId() + "): " + 
						resp.readEntity(String.class));
			}
		}
		Log.info(LOGGER, "Delete count: " + deleteCount + "/" + list.size());
	}
	
	private static void deleteMyFilters(Config conf) throws Exception {
		String accountId = getUserAccountId(conf, conf.getTargetUser());
		RestUtil<Filter> util = RestUtil
				.getInstance(Filter.class)
				.config(conf, true);
		Log.info(LOGGER, "Getting filters owned by current user [" + accountId + "]...");
		List<Filter> list = util.path("/rest/api/latest/filter/search")
								.method(HttpMethod.GET)
								.query("accountId", accountId)
								.pagination(new Paged<Filter>(Filter.class).maxResults(100))
								.requestAllPages();
		Log.info(LOGGER, "Filter count: " + list.size());
		int deleteCount = 0;
		for (Filter filter : list) {
			Response resp = util.path("/rest/api/latest/filter/{id}")
								.pathTemplate("id", filter.getId())
								.method(HttpMethod.DELETE)
								.status()
								.request();
			if (checkStatusCode(resp, Status.OK)) {
				deleteCount++;
				Log.info(LOGGER, "Filter [" + filter.getName() + "] (" + filter.getId() + ") deleted");
			} else {
				Log.info(LOGGER, "Failed to delete filter [" + filter.getName() + "] (" + filter.getId() + "): " + 
						resp.readEntity(String.class));
			}
		}
		Log.info(LOGGER, "Delete count: " + deleteCount + "/" + list.size());
	}
	
	@SuppressWarnings("unchecked")
	private static void dumpObjectMapping(Config conf) {
		CSVFormat fmt = CSV.getCSVWriteFormat(Arrays.asList(
				"Match Type",
				"Server ID", "Server Name", "Server Details", 
				"Cloud ID", "Cloud Name", "Cloud Details"
				));
		Date now = new Date();
		for (MappingType type : MappingType.values()) {
			// Exclude filter and dashboard, those two do not output .Map anymore
			if (type == MappingType.FILTER || 
				type == MappingType.DASHBOARD) {
				continue;
			}
			String cloudFile = type.getCloud();
			String dcFile = type.getDC();
			String mappingFile = type.getMap();
			if (!Files.exists(Paths.get(cloudFile))) {
				Log.warn(LOGGER, type.toString() + " Cloud file missing");
				continue;
			}
			if (!Files.exists(Paths.get(dcFile))) {
				Log.warn(LOGGER, type.toString() + " Server file missing");
				continue;
			}
			if (!Files.exists(Paths.get(mappingFile))) {
				Log.warn(LOGGER, type.toString() + " Mapping file missing");
				continue;
			}
			// Read data center objects
			Map<String, JiraObject<?>> cloudList = new HashMap<>();
			Map<String, JiraObject<?>> dcList = new HashMap<>();
			Mapping mapping = null;
			try {
				List<JiraObject<?>> list = (List<JiraObject<?>>) 
						readValuesFromFile(cloudFile, type.getDataClass());
				for (JiraObject<?> obj : list) {
					cloudList.put(obj.getInternalId(), obj);
				}
			} catch (Exception ex) {
				Log.error(LOGGER, "Error reading " + type + " Cloud file", ex);
				continue;
			}
			try {
				List<JiraObject<?>> list = (List<JiraObject<?>>)
						readValuesFromFile(dcFile, type.getDataClass());
				for (JiraObject<?> obj : list) {
					dcList.put(obj.getInternalId(), obj);
				}
			} catch (Exception ex) {
				Log.error(LOGGER, "Error reading " + type + " Server file", ex);
				continue;
			}
			try {
				mapping = loadMapping(type);
			} catch (Exception ex) {
				Log.error(LOGGER, "Error reading " + type + " Mapping file", ex);
				continue;
			}
			try (	FileWriter fw = CSV.getCSVFileWriter(type.getMapping());
					CSVPrinter csv = new CSVPrinter(fw, fmt)) {
				// Output mapped items
				for (Map.Entry<String, JiraObject<?>> entry : mapping.getMapped().entrySet()) {
					String serverId = entry.getKey();
					JiraObject<?> cloudObj = entry.getValue();
					JiraObject<?> dcObj = dcList.get(serverId);
					if (dcObj == null) {
						Log.error(LOGGER, "Server object " + serverId + " not found");
					} else {
						dcList.remove(dcObj.getInternalId());
						cloudList.remove(cloudObj.getInternalId());
						csv.printRecord(
								"Matched",
								dcObj.getInternalId(), 
								dcObj.getDisplay(),
								dcObj.getAdditionalDetails(),
								cloudObj.getInternalId(),
								cloudObj.getDisplay(),
								cloudObj.getAdditionalDetails()
								);
					}
				}
				// Output conflicted items
				for (Map.Entry<String, List<JiraObject<?>>> entry : mapping.getConflict().entrySet()) {
					String serverId = entry.getKey();
					JiraObject<?> dcObj = dcList.get(serverId);
					if (dcObj == null) {
						Log.error(LOGGER, "Server object " + serverId + " not found");
					} else {
						dcList.remove(dcObj.getInternalId());
						for (JiraObject<?> cloudObj : entry.getValue()) {
							cloudList.remove(cloudObj.getInternalId());
							csv.printRecord(
									"Conflict",
									dcObj.getInternalId(), 
									dcObj.getDisplay(),
									dcObj.getAdditionalDetails(),
									cloudObj.getInternalId(),
									cloudObj.getDisplay(),
									cloudObj.getAdditionalDetails()
									);
						}
					}
				}
				// Output remaining objects in Server/Cloud
				for (JiraObject<?> obj : dcList.values()) {
					csv.printRecord(
							"Unmapped",
							obj.getInternalId(), 
							obj.getDisplay(),
							obj.getAdditionalDetails(),
							"", "", ""
							);
				}
				for (JiraObject<?> obj : cloudList.values()) {
					csv.printRecord(
							"Unmapped",
							"", "", "",
							obj.getInternalId(), 
							obj.getDisplay(),
							obj.getAdditionalDetails()
							);
				}
				Log.info(LOGGER, type + " mapping saved to " + type.getMapping());
			} catch (Exception ex) {
				Log.error(LOGGER, "Error processing " + type, ex);
			}
		}	// For each MappingType
	}
	
	@SuppressWarnings("incomplete-switch")
	public static void main(String[] args) {
		Instant startTime = Instant.now();
		Log.info(LOGGER, "Start time: " + SDF.format(Date.from(startTime)));
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
			// Initialize throttle
			RestUtil.throttle(conf.getLimit(), conf.getPeriod());
			// Initialize client pool
			ClientPool.setMaxPoolSize(conf.getConnectionPoolSize(), conf.getConnectTimeout(), conf.getReadTimeout());
			// Process options
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
					System.out.println("Config.json syntax: ");
					System.out.println(OM.writeValueAsString(Config.getExample()));
					return;
				}
				for (Option op : cli.getOptions()) {
					CLIOptions opt = CLIOptions.parse(op);
					if (opt == null) {
						continue;
					}
					switch (opt) {
					case DUMP_OBJECT_MAPPING: {
						dumpObjectMapping(conf);
						break;
					}
					case RESET_FILTER: {
						String listFile = cli.getOptionValue(CLI.RESETFILTER_OPTION);
						resetFilter(conf, listFile);
						break;
					}
					case DUMP_DC: {
						getCredentials(conf, false);
						List<MappingType> types = parseMappingTypes(false, cli.getOptionValues(CLI.DUMPDC_OPTION));
						dumpObjects(conf, false, types);
						if (types.contains(MappingType.FILTER) && types.contains(MappingType.DASHBOARD)) {
							dumpFilterAndDashboard(conf);
						}
						break;
					}
					case DUMP_CLOUD:
						getCredentials(conf, true);
						dumpObjects(conf, true, parseMappingTypes(true, cli.getOptionValues(CLI.DUMPCLOUD_OPTION)));
						break;
					case MAP_OBJECT:
						List<MappingType> types = parseMappingTypes(false, cli.getOptionValues(CLI.OBJECTTYPE_OPTION));
						boolean exactMatch = cli.hasOption(CLI.EXACTMATCH_OPTION);
						mapObjectsV2(exactMatch, types);
						if (types.contains(MappingType.USER)) {
							String csvFile = cli.getOptionValue(CLI.USERCSV_OPTION);
							if (csvFile != null) {
								// Override user mapping with CSV file exported from Cloud User Management
								mapUsersWithCSV(csvFile);
							}
						}
						break;
					case DELETE_MY_FILTER:
						deleteMyFilters(conf);
						break;
					case DELETE_MY_DASHBOARD:
						deleteMyDashboard(conf);
						break;
					case CREATE_FILTER: 
						boolean allValuesMapped = cli.hasOption(CLI.ALLVALUESMAPPED_OPTION);
						boolean overwriteFilter = cli.hasOption(CLI.OVERWRITEFILTER_OPTION);
						String callApiString = cli.getOptionValue(CLI.CREATEFILTER_OPTION);
						boolean callApi = false;
						if (callApiString != null) {
							callApi = Boolean.parseBoolean(callApiString);
						}
						mapFiltersV4(conf, callApi, overwriteFilter, allValuesMapped);
						break;
					case ADD_FILTER_PERMISSION: 
						modifyFilterPermission(conf, true);
						break;
					case REMOVE_FILTER_PERMISSION:
						modifyFilterPermission(conf, false);
						break;
					case CREATE_DASHBOARD: 
						try (ClientWrapper wrapper = new ClientWrapper(true, conf)) {
							createDashboardsV2(conf);
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
		} finally {
			// Close ClientPool
			ClientPool.close();
		}
		Instant endTime = Instant.now();
		Log.info(LOGGER, "End time: " + SDF.format(Date.from(endTime)));
		Duration elapsed = Duration.between(
				LocalTime.from(startTime.atZone(ZoneId.systemDefault())), 
				LocalTime.from(endTime.atZone(ZoneId.systemDefault())));
		Log.info(LOGGER, "Elapsed: " + 
				elapsed.toDaysPart() + " day(s) " + 
				elapsed.toHoursPart() + " hour(s) " + 
				elapsed.toMinutesPart() + " minute(s) " + 
				elapsed.toSecondsPart() + " second(s) " + 
				elapsed.toMillisPart() + " millisecond(s) ");
	}
}
