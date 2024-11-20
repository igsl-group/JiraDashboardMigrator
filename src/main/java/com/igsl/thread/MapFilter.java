package com.igsl.thread;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.atlassian.jira.jql.parser.antlr.JqlLexer;
import com.atlassian.jira.jql.parser.antlr.JqlParser;
import com.atlassian.jira.jql.parser.antlr.JqlParser.query_return;
import com.atlassian.query.clause.AndClause;
import com.atlassian.query.clause.ChangedClause;
import com.atlassian.query.clause.Clause;
import com.atlassian.query.clause.NotClause;
import com.atlassian.query.clause.OrClause;
import com.atlassian.query.clause.TerminalClause;
import com.atlassian.query.clause.WasClause;
import com.atlassian.query.clause.WasClauseImpl;
import com.atlassian.query.operand.EmptyOperand;
import com.atlassian.query.operand.FunctionOperand;
import com.atlassian.query.operand.MultiValueOperand;
import com.atlassian.query.operand.Operand;
import com.atlassian.query.operand.SingleValueOperand;
import com.atlassian.query.order.OrderBy;
import com.atlassian.query.order.OrderByImpl;
import com.atlassian.query.order.SearchSort;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.igsl.DashboardMigrator;
import com.igsl.DashboardMigrator.MyChangedClause;
import com.igsl.DashboardMigrator.MyTerminalClause;
import com.igsl.FilterNotMappedException;
import com.igsl.Log;
import com.igsl.NotAllValuesMappedException;
import com.igsl.config.Config;
import com.igsl.model.CloudFilter;
import com.igsl.model.CloudPermission;
import com.igsl.model.DataCenterPermission;
import com.igsl.model.PermissionTarget;
import com.igsl.model.PermissionType;
import com.igsl.model.mapping.CustomField;
import com.igsl.model.mapping.Filter;
import com.igsl.model.mapping.Group;
import com.igsl.model.mapping.JQLFuncArg;
import com.igsl.model.mapping.JQLFunction;
import com.igsl.model.mapping.JiraObject;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;
import com.igsl.model.mapping.Project;
import com.igsl.model.mapping.Role;
import com.igsl.model.mapping.User;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class MapFilter implements Callable<MapFilterResult> {

	// Function arg not quoted
	// project = GSDT AND issuetype = "Order Provisioning" AND status in (Provisioning, Provisioned, "PM Assigned", Verified, "Pending (Customer)", "Pending (Internal)", "Pending (Others)", "Pending (Vendor)", Pending) AND assignee in (mreichl) AND "Project Manager" in (mreichl, membersOf("Service Delivery - Europe"), currentUser())
	
	private static final Logger LOGGER = LogManager.getLogger();
	private static final ObjectMapper OM = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			// Allow comments
			.configure(Feature.ALLOW_COMMENTS, true)	
			// Allow attributes missing in POJO
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	
	private Config config;
	private Path originalDir;
	private Path newDir;
	private Filter filter;
	private Map<MappingType, Mapping> mappings;
	private Map<MappingType, List<JiraObject<?>>> data;
	private boolean callApi;
	private boolean allValuesMapped;
	private boolean overwriteFilter;
	private String myAccountId;
	
	public MapFilter(
			Config config,
			String myAccountId,
			Path originalDir,
			Path newDir,
			Map<MappingType, Mapping> mappings,
			Map<MappingType, List<JiraObject<?>>> data,
			Filter filter,
			boolean callApi,
			boolean allValuesMapped,
			boolean overwriteFilter) {
		this.originalDir = originalDir;
		this.newDir = newDir;
		this.config = config;
		this.myAccountId = myAccountId;
		this.mappings = mappings;
		this.data = data;
		this.filter = filter;
		this.callApi = callApi;
		this.allValuesMapped = allValuesMapped;
		this.overwriteFilter = overwriteFilter;
	}
	
	private static final Pattern CUSTOM_FIELD_CF = Pattern.compile("^cf\\[([0-9]+)\\]$");
	private static final String CUSTOM_FIELD = "customfield_";
	private static CustomField mapCustomFieldName(
			Map<String, JiraObject<?>> map, 
			List<JiraObject<?>> data,
			String name) 
			throws Exception {
		String originalFieldId = null;
		// If data matches custom field display name
		List<CustomField> matchedFields = new ArrayList<>();
		for (JiraObject<?> obj : data) {
			if (obj.getDisplay().equalsIgnoreCase(name)) {
				// Translate to customfield_#
				matchedFields.add((CustomField) obj);
			}
		}
		if (matchedFields.size() == 1) {
			// Single match, return field
			originalFieldId = matchedFields.get(0).getId();
		} else if (matchedFields.size() > 1) {
			// Prioritize system field, use the first found that is not custom
			for (CustomField cf : matchedFields) {
				if (!cf.isCustom()) {
					originalFieldId = cf.getId();
				}
			}
			// No system field, just use first result
			originalFieldId = matchedFields.get(0).getId();
		}
		// If data is customfield_#
		if (map.containsKey(name)) {
			CustomField cf = (CustomField) map.get(name);
			originalFieldId = cf.getId();
		}
		// If data is cf[#]
		Matcher m = CUSTOM_FIELD_CF.matcher(name);
		if (m.matches()) {
			originalFieldId = CUSTOM_FIELD + m.group(1);
		}
		// Map field
		if (originalFieldId != null) {
			if (map.containsKey(originalFieldId)) {
				return (CustomField) map.get(originalFieldId);
			} else {
				throw new Exception("Custom field [" + name + "] cannot be mapped");
			}
 		}
		// This can be issue function name, return null
		return null;
	}
	
	private static void validateClause(String filterName, Clause clause) throws Exception {
		if (clause instanceof TerminalClause) {
			TerminalClause tc = (TerminalClause) clause;
			String propertyName = tc.getName().toLowerCase();
			if ("issuefunction".equals(propertyName)) {
				throw new Exception("Filter [" + filterName + "] Property [" + propertyName + "] " + 
						"issueFunction is no longer supported");
			}
		} 
		// Check children
		for (Clause child :	clause.getClauses()) {
			validateClause(filterName, child);
		}
	}
	
	private static final Pattern NON_ASCII_PATTERN = Pattern.compile("([^\\x00-\\x7F])");
	private static final Pattern NUMERIC_PATTERN = Pattern.compile("[0-9]+");
	/**
	 * Ensure value (not numeric) that contains space is surrounded by double quotes.
	 */
	private static String sanitizeValue(String value) {
		return sanitizeValue(value, false);
	}
	private static String sanitizeValue(String value, boolean addQuote) {
		if (value != null) {
			Matcher matcher = NUMERIC_PATTERN.matcher(value);
			if (!matcher.matches()) {
				// Escape double quotes in value
				value = value.replaceAll("\"", "\\\\\"");
				// Escape value to u####
				StringBuilder sb = new StringBuilder();
				matcher = NON_ASCII_PATTERN.matcher(value);
				while (matcher.find()) {
					String nonAscii = matcher.group(1);
					StringBuilder replacement = new StringBuilder();
					for (char c : nonAscii.toCharArray()) {
						replacement.append(String.format("\\\\u%04x", (int) c));
					}
					matcher.appendReplacement(sb, replacement.toString());
				}
				matcher.appendTail(sb);
				value = sb.toString();		
				if (addQuote) {
					// If contain space, add double quotes
					if (value.contains(" ")) {
						value = "\"" + value + "\"";
					}
				}
			} // Numeric value, keep unchanged
		}
		return value;
	}
	
	private static SingleValueOperand mapValue(
			SingleValueOperand src, 
			MappingType mappingType,
			CustomField mappedCustomField, 
			Map<MappingType, List<JiraObject<?>>> data, 
			Map<MappingType, Mapping> maps,
			String filterName, String propertyName, boolean ignoreFilter) throws Exception {
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
			if (mappingType != null) {
				Mapping map = maps.get(mappingType);
				if (map == null) {
					Log.error(LOGGER, "Mapping for " + mappingType + " not found");
				}
				if (isLong) {
					// Map value as internal ID
					if (map.getMapped().containsKey(originalValue)) {
						// Remap numerical values
						JiraObject<?> obj = map.getMapped().get(originalValue);
						Long newValue = Long.valueOf(obj.getInternalId());
						Log.info(LOGGER, "Mapped value for filter [" + filterName + "] " + 
								"type [" + propertyName + "][" + mappingType + "] " + 
								"value [" + originalValue + "] => [" + newValue + "]");
						result = new SingleValueOperand(newValue);
					} else {
						if (map.getType() == MappingType.FILTER) {
							if (ignoreFilter) {
								result = new SingleValueOperand(originalValue);
							} else {
								throw new FilterNotMappedException(originalValue);
							}
						} else {
							throw new Exception("Unable to map value for filter [" + filterName + "] " + 
									"type [" + propertyName + "][" + mappingType + "] " + 
									"value [" + originalValue + "]");
						}
					}
				} else {
					// Map value as JQL name
					String newValue = null;
					boolean validated = false;
					boolean mapped = false;
					List<?> dataList = data.get(map.getType());
					for (Object obj : dataList) {
						JiraObject<?> jo = (JiraObject<?>) obj;
						if (jo.jqlEquals(originalValue)) {
							validated = true;
							if (map.getType() != MappingType.FILTER || 
								(map.getType() == MappingType.FILTER && !ignoreFilter)) {
								if (map.getMapped().containsKey(jo.getInternalId())) {
									mapped = true;
									newValue = map.getMapped().get(jo.getInternalId()).getJQLName();
								}
							} else {
								// Consider filter value mapped
								mapped = true;
								newValue = originalValue;
							}
							break;
						}
					}
					if (!validated) {
						String msg = "Value not validated for filter [" + filterName + "] " + 
								"type [" + propertyName + "][" + mappingType + "] " + 
								"value [" + originalValue + "]";
						Log.info(LOGGER, msg);
						throw new Exception(msg);
					} else if (!mapped) {
						String msg = "Value not mapped for filter [" + filterName + "] " + 
								"type [" + propertyName + "][" + mappingType + "] " + 
								"value [" + originalValue + "]";
						Log.info(LOGGER, msg);
						if (map.getType() == MappingType.FILTER) {
							throw new FilterNotMappedException(msg);
						} else {
							throw new Exception(msg);
						}
					} else {
						// SingleValueOperand will add double quotes
						newValue = sanitizeValue(newValue);
						Log.info(LOGGER, "Value validated and mapped for filter [" + filterName + "] " + 
								"type [" + propertyName + "][" + mappingType + "] " + 
								"value [" + originalValue + "] -> [" + newValue + "]");
						if (isLong) {
							result = new SingleValueOperand(Long.parseLong(newValue));
						} else {
							result = new SingleValueOperand(newValue);
						}
					}
				}
			} else if (	mappedCustomField != null && 
						mappedCustomField.getSchema() != null && 
						mappedCustomField.getSchema().isOptionField()) {
				// Find associated CustomFieldOption
				Mapping mapping = maps.get(MappingType.CUSTOM_FIELD_OPTION);
				if (isLong) {
					// Id
					if (mapping.getMapped().containsKey(originalValue)) {
						JiraObject<?> obj = mapping.getMapped().get(originalValue);
						result = new SingleValueOperand(obj.getJQLName());
					} else {
						String msg = "Value not validated for filter [" + filterName + "] " + 
								"type [" + propertyName + "][" + mappingType + "] " + 
								"value [" + originalValue + "]";
						Log.info(LOGGER, msg);
						throw new Exception(msg);
					}
				} else {
					// Text
					Log.warn(LOGGER, "Value unchanged for filter [" + filterName + "] " + 
							"type [" + propertyName + "][" + mappingType + "] " + 
							"value [" + originalValue + "]");
					result = new SingleValueOperand(sanitizeValue(originalValue));
				}
			} else {
				if (isLong) {
					Log.warn(LOGGER, "Value unchanged for filter [" + filterName + "] " + 
							"type [" + propertyName + "][" + mappingType + "] " + 
							"value [" + originalValue + "]");
					result = new SingleValueOperand(Long.parseLong(originalValue));
				} else {
					Log.warn(LOGGER, "Value unchanged for filter [" + filterName + "] " + 
							"type [" + propertyName + "][" + mappingType + "] " + 
							"value [" + originalValue + "]");
					result = new SingleValueOperand(sanitizeValue(originalValue));
				}
			}
		}
		return result;
	}
	
	private static String mapArgument(
			String argument, MappingType mappingType, 
			Mapping map, List<JiraObject<?>> dataList) throws Exception {
		String result = argument;
		if (mappingType != null) {
			boolean validated = false;
			boolean mapped = false;
			for (Object obj : dataList) {
				JiraObject<?> o = (JiraObject<?>) obj;
				if (o.jqlEquals(argument)) {
					validated = true;
					if (map.getMapped().containsKey(o.getInternalId())) {
						mapped = true;
						result = map.getMapped().get(o.getInternalId()).getJQLName();						
					}
				}
			}
			if (!validated) {
				throw new Exception("Argument [" + argument + "] not validated as " + mappingType);
			}
			if (!mapped) {
				throw new Exception("Argument [" + argument + "] not mapped as " + mappingType);
			}
		} // Else treat as string, no change
		Log.info(LOGGER, "Argument [" + argument + "] -> [" + result + "]");
		return result;
	}
	
	private static Operand mapOperand(
			String filterName,
			String clauseName,
			Map<MappingType, List<JiraObject<?>>> data,
			Map<MappingType, Mapping> maps, 
			MappingType mappingType,
			CustomField mappedCustomField, 
			Operand operand,
			boolean ignoreFilter) throws Exception {
		Operand clone = null;
		if (operand instanceof SingleValueOperand) {
			SingleValueOperand svo = (SingleValueOperand) operand;
			// Change value
			clone = mapValue(svo, 
					mappingType, 
					mappedCustomField, 
					data, maps, 
					filterName, clauseName, ignoreFilter);
		} else if (operand instanceof MultiValueOperand) {
			MultiValueOperand mvo = (MultiValueOperand) operand;
			List<Operand> list = new ArrayList<>();
			for (Operand item : mvo.getValues()) {
				Operand itemClone = mapOperand(
						filterName, 
						clauseName, 
						data, 
						maps,
						mappingType,
						mappedCustomField, 
						item,
						ignoreFilter);
				list.add(itemClone);
			}
			clone = new MultiValueOperand(list);
		} else if (operand instanceof FunctionOperand) {
			FunctionOperand fo = (FunctionOperand) operand;
			JQLFunction func = JQLFunction.parse(fo.getName());
			if (func == null) {
				// Unrecognized function
				throw new Exception("Unrecognized JQL function [" + fo.getName() + "]");
			}
			// Check if obsolete
			if (func.isObsolete()) {
				throw new Exception("JQL function [" + fo.getName() + "] is obsolete");
			}
			// Check arguments
			List<String> args = new ArrayList<>();
			JQLFuncArg[] argDefList = func.getArguments();
			if (argDefList != null) {
				// If argument is present, check against type and remap value if needed
				for (int i = 0; i < fo.getArgs().size(); i++) {
					if (i >= argDefList.length) {
						// Too many arguments
						throw new Exception(
								"Too many arguments #" + i + " for JQL function [" + fo.getName() + "]");
					}
					JQLFuncArg argDef = argDefList[i];
					MappingType type = argDef.getMappingType();
					if (argDef.isVarArgs()) {
						// Varargs, consume the rest of the arguments
						for (int j = i; j < fo.getArgs().size(); j++) {
							String newValue = mapArgument(
									fo.getArgs().get(j), 
									type, 
									maps.get(type), 
									data.get(type));
							args.add(newValue);
						}
						break;	// for loop
					} else {
						// Singular argument
						String newValue = mapArgument(
								fo.getArgs().get(i), 
								type, 
								maps.get(type), 
								data.get(type));
						args.add(newValue);
					}
				}
			} else {
				for (String s : fo.getArgs()) {
					args.add(s);
				}
			}
			// Quote arguments if they contain space
			List<String> quotedArgs = new ArrayList<>();
			for (String s : args) {
				s = sanitizeValue(s, true);
				quotedArgs.add(s);
			}
			clone = new FunctionOperand(fo.getName(), quotedArgs);
		} else if (operand instanceof EmptyOperand) {
			clone = operand;
		} else {
			Log.warn(LOGGER, "Unrecognized Operand class for filter [" + filterName + "] class [" + operand + "], reusing reference");
			clone = operand;
		}
		return clone;
	}
	
	private static Clause mapClause(
			String filterName, 
			Map<MappingType, List<JiraObject<?>>> data,
			Map<MappingType, Mapping> maps, 
			Clause c, 
			boolean ignoreFilter) 
			throws Exception {
		Clause clone = null;
		List<Clause> clonedChildren = new ArrayList<>();
		if (c != null) {
			Log.debug(LOGGER, "Clause: [" + c + "], [" + c.getClass() + "]");
			String propertyName = c.getName();
			String newPropertyName = sanitizeValue(propertyName, true);
			MappingType mappingType = null;
			CustomField mappedCustomField = null;
			if (propertyName != null) {
				// Get mapping type
				mappingType = MappingType.parseFilterProperty(propertyName);
				if (mappingType == null) {
					// Remap propertyName if it is a custom field
					mappedCustomField = mapCustomFieldName(
							maps.get(MappingType.CUSTOM_FIELD).getMapped(), 
							data.get(MappingType.CUSTOM_FIELD),
							propertyName);
					if (mappedCustomField != null) {
						Log.info(LOGGER, "Custom field recognized: [" + propertyName + "] -> " + 
								"[" + mappedCustomField.getId() + "]");						
						newPropertyName = sanitizeValue(mappedCustomField.getJQLName(), true);
					} else {
						Log.info(LOGGER, "Custom field not recognized: [" + propertyName + "]");						
					}
				} else {
					Log.info(LOGGER, "Property [" + propertyName + "] is type " + mappingType.toString());
				}
				Log.info(LOGGER, "Property [" + propertyName + "] -> [" + newPropertyName + "]");
			}
			for (Clause sc : c.getClauses()) {
				// Recursively process children
				Clause clonedChild = mapClause(
						filterName, data, maps, sc, ignoreFilter);
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
				Operand clonedOperand = mapOperand(
						filterName, 
						tc.getName(), 
						data, 
						maps, 
						mappingType, 
						mappedCustomField, 
						tc.getOperand(), 
						ignoreFilter);
				// Create clone
				clone = new MyTerminalClause(newPropertyName, tc.getOperator(), clonedOperand);
			} else if (c instanceof WasClause) {
				WasClause wc = (WasClause) c;
				clone = new WasClauseImpl(newPropertyName, wc.getOperator(), wc.getOperand(), wc.getPredicate());
			} else if (c instanceof ChangedClause) {
				ChangedClause cc = (ChangedClause) c;
				clone = new MyChangedClause(cc.getField(), cc.getOperator(), cc.getPredicate());
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
	@Override
	public MapFilterResult call() throws Exception {
		MapFilterResult result = new MapFilterResult();
		result.setOriginal(filter);
		Log.info(LOGGER, "Processing filter " + filter.getName() + " [" + filter.getJql() + "]");
		String filterFileName = DashboardMigrator.sanitizePath(
				filter.getName() + "." + filter.getId() + ".json");
		DashboardMigrator.saveFile(
				originalDir.resolve(filterFileName).toString(), 
				filter);
		Filter outputFilter = this.filter.clone();
		// Verify and map owner
		String originalOwner = filter.getOwner().getKey();
		String newOwner = null;
		if (!mappings.get(MappingType.USER).getMapped().containsKey(originalOwner)) {
			String msg = "Filter [" + filter.getName() + "] " + 
					"owned by unmapped user [" + originalOwner + "]";
			result.setException(new Exception(msg));
			return result;
		}
		User newUser = (User) mappings.get(MappingType.USER).getMapped().get(originalOwner);
		outputFilter.getOwner().setAccountId(newUser.getAccountId());
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
			case USER_UNKNOWN:
				Log.warn(LOGGER, "Filter [" + filter.getName() + "] " + 
						"share permission to inaccessible user [" + OM.writeValueAsString(share) + "] " + 
						"is excluded");
				break;
			case PROJECT_UNKNOWN:	
				// This happens when you share to a project you cannot access. 
				// This type of permissions cannot be created via REST.
				// So this is excluded.
				Log.warn(LOGGER, "Filter [" + filter.getName() + "] " + 
						"share permission to inaccessible objects [" + OM.writeValueAsString(share) + "] " + 
						"is excluded");
				break;
			case GROUP:
				if (mappings.get(MappingType.GROUP).getMapped().containsKey(share.getGroup().getName())) {
					Group grp = (Group) mappings.get(MappingType.GROUP).getMapped()
							.get(share.getGroup().getName());
					String newId = grp.getName();
					share.getGroup().setName(newId);
					newPermissions.add(share);
				} else {
					String msg = "Filter [" + filter.getName() + "] " + 
							"is shared to unmapped group [" + share.getGroup().getName() + "] " + 
							"This share is excluded";
					Log.warn(LOGGER, msg);
				}
				break;
			case PROJECT:
				if (share.getRole() == null) {
					// No role, add as project
					if (mappings.get(MappingType.PROJECT).getMapped().containsKey(share.getProject().getId())) {
						Project p = (Project) mappings.get(MappingType.PROJECT).getMapped()
								.get(share.getProject().getId());
						String newId = p.getId();
						share.getProject().setId(newId);
						newPermissions.add(share);
					} else {
						String msg = "Filter [" + filter.getName() + "] " + 
								"is shared to unmapped project [" + share.getProject().getId() + "] " + 
								"This share is excluded";
						Log.warn(LOGGER, msg);
					}
				} else if (share.getRole() != null) {
					// Has role, add as project-role
					if (mappings.get(MappingType.PROJECT).getMapped().containsKey(share.getProject().getId())) {
						Project r = (Project) mappings.get(MappingType.PROJECT).getMapped()
								.get(share.getProject().getId());
						String newId = r.getId();
						share.getProject().setId(newId);
					} else {
						String msg = "Filter [" + filter.getName() + "] " + 
								"is shared to unmapped project [" + share.getProject().getId() + "] " + 
								"This share is excluded";
						Log.warn(LOGGER, msg);
					}
					if (mappings.get(MappingType.ROLE).getMapped().containsKey(share.getRole().getId())) {
						Role r = (Role) mappings.get(MappingType.ROLE).getMapped()
								.get(share.getRole().getId());
						String newId = r.getId();
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
								"is shared to unmapped role [" + share.getRole().getId() + "] " + 
								"This share is excluded";
						Log.warn(LOGGER, msg);
					}
				}
				break;
			case USER:
				if (mappings.get(MappingType.USER).getMapped().containsKey(share.getUser().getKey())) {
					User u = (User) mappings.get(MappingType.USER).getMapped()
							.get(share.getUser().getKey());
					String newId = u.getAccountId();
					share.getUser().setAccountId(newId);
					newPermissions.add(share);
				} else {
					String msg = "Filter [" + filter.getName() + "] " + 
							"is shared to unmapped user [" + share.getUser().getKey() + "] " + 
							"This share is excluded";
					Log.warn(LOGGER, msg);
				}
				break;
			}			
		}
		outputFilter.setSharePermissions(newPermissions);
		// Add current user to permission. This will need to be removed after all batches are done.
		DataCenterPermission myPermission = new DataCenterPermission();
		myPermission.setType("user");
		myPermission.setEdit(true);
		myPermission.setView(true);
		PermissionTarget pt = new PermissionTarget();
		pt.setAccountId(myAccountId);
		myPermission.setUser(pt);
		// Parse and convert JQL
		JqlLexer lexer = new JqlLexer((CharStream) new ANTLRStringStream(filter.getJql()));
		CommonTokenStream cts = new CommonTokenStream(lexer);
		JqlParser parser = new JqlParser(cts);
		query_return qr = parser.query();
		try {
			validateClause(filter.getName(), qr.clause);
		} catch (Exception ex) {
			result.setException(ex);
			return result;
		}
		Clause clone = null;
		try {
			clone = mapClause(
				filter.getName(), data, mappings, qr.clause, !callApi);
		} catch (NotAllValuesMappedException navmex) {
			if (allValuesMapped) {
				StringBuilder msg = new StringBuilder();
				msg	.append("Failed to map filter [")
					.append(filter.getName())
					.append("] Not all values mapped: ");
				for (String s : navmex.getUnmappedValues()) {
					msg	.append("[")
						.append(s)
						.append("] ");
				}
				result.setException(new Exception(msg.toString()));
				return result;
			} else {
				clone = navmex.getClause();
			}
		} catch (Exception ex) {
			result.setException(ex);
			return result;
		}
		// Handler order clause
		OrderBy orderClone = null;
		try {
			if (qr.order != null) {
				Log.info(LOGGER, "Orignal order by [" + qr.order.toString() + "]");
				List<SearchSort> sortList = new ArrayList<>();
				for (SearchSort ss : qr.order.getSearchSorts()) {
					Log.info(LOGGER, "Order clause " + 
							"[" + ss.toString() + "] " + 
							"Field [" + ss.getField() + "] " + 
							"Property [" + ss.getProperty() + "]");
					CustomField cf = mapCustomFieldName(
							mappings.get(MappingType.CUSTOM_FIELD).getMapped(), 
							data.get(MappingType.CUSTOM_FIELD),
							ss.getField());
					// Some order by items are indeed not custom fields, e.g. key
					// So don't count them as error, just let them proceed as is
					// If it fails on update then so be it
					String newColumn = (cf != null)? cf.getJQLName() : ss.getField();
					newColumn = sanitizeValue(newColumn, true);
					SearchSort newSS = new SearchSort(
							newColumn, ss.getProperty(), ss.getSortOrder());
					sortList.add(newSS);
					Log.info(LOGGER, "Mapped sort column for filter [" + filter.getName() + "] " + 
							"column [" + ss.getField() + "] => [" + newColumn + "]");
				}
				orderClone = new OrderByImpl(sortList);
			}
			outputFilter.setJql(clone + ((orderClone != null) ? " " + orderClone : ""));
		} catch (Exception ex) {
			result.setException(ex);
			return result;
		}
		Log.info(LOGGER, "Updated JQL for filter [" + outputFilter.getName() + "]: " + 
				"[" + filter.getJql() + "] => [" + outputFilter.getJql() + "]");
		result.setTarget(outputFilter);
		// Save remapped filter
		DashboardMigrator.saveFile(
				newDir.resolve(filterFileName).toString(), 
				outputFilter);
		// Create or overwrite filter
		if (callApi) {
			Response respFilter = null;
			RestUtil<Filter> util = RestUtil.getInstance(Filter.class)
					.config(config, true);
			// Check if exists for current user
			Filter existingFilter = filterExists(
					config, null, outputFilter.getName(), outputFilter.getOwner().getAccountId());
			if (existingFilter != null) {
				// Update
				Log.info(LOGGER, 
						"Updating filter [" + outputFilter.getName() + "] (" + outputFilter.getId() + ")");
				outputFilter.setId(existingFilter.getId());
				CloudFilter cloudFilter = CloudFilter.create(outputFilter);
				cloudFilter.getEditPermissions().add(CloudPermission.create(myPermission));
				if (overwriteFilter) {
					cloudFilter.setId(existingFilter.getId());
					Log.info(LOGGER, "Payload: " + OM.writeValueAsString(cloudFilter));
					respFilter = util
							.path("/rest/api/latest/filter/{filterId}")
							.pathTemplate("filterId", cloudFilter.getId())
							.method(HttpMethod.PUT)
							.query("overrideSharePermissions", true)
							.payload(cloudFilter)
							.status()
							.request();
					if ((respFilter.getStatus() & Status.OK.getStatusCode()) != Status.OK.getStatusCode()) {
						String msg = respFilter.readEntity(String.class);
						result.setException(new Exception(msg));
						return result;
					}
					Log.info(LOGGER, 
							"Updated filter [" + outputFilter.getName() + "] (" + outputFilter.getId() + ")");
				} else {
					// Overwrite disabled, add to mapping but count as error
					mappings.get(MappingType.FILTER).getMapped().put(filter.getId(), existingFilter);
					String msg = "Filter [" + filter.getName() + "] (" + filter.getId() + ") " + 
							"already exists, will not overwrite";
					result.setException(new Exception(msg));
					return result;
				}
			} else {
				Log.info(LOGGER, 
						"Creating filter [" + outputFilter.getName() + "] (" + outputFilter.getId() + ")");
				// Create
				CloudFilter cloudFilter = CloudFilter.create(outputFilter);
				cloudFilter.getEditPermissions().add(CloudPermission.create(myPermission));
				Log.info(LOGGER, "Payload: " + OM.writeValueAsString(cloudFilter));
				respFilter = util
						.path("/rest/api/latest/filter")
						.method(HttpMethod.POST)
						.query("overrideSharePermissions", true)
						.payload(cloudFilter)
						.status()
						.request();
				if ((respFilter.getStatus() & Status.OK.getStatusCode()) != Status.OK.getStatusCode()) {
					String msg = "Failed to create filter [" + filter.getName() + "] " + 
								"[" +  respFilter.readEntity(String.class) + "]";
					result.setException(new Exception(msg));
					return result;
				}
				Filter createdFilter = respFilter.readEntity(Filter.class);
				Log.info(LOGGER, "Filter created [" + filter.getName() + "] (" + createdFilter.getId() + ")");
				// Update id
				outputFilter.setId(createdFilter.getId());
				// Change owner
				Map<String, Object> payload = new HashMap<>();
				payload.put("accountId", outputFilter.getOwner().getAccountId());
				respFilter = util
						.path("/rest/api/latest/filter/{filterId}/owner")
						.pathTemplate("filterId", outputFilter.getId())
						.method(HttpMethod.PUT)
						.payload(payload)
						.status()
						.request();
				if ((respFilter.getStatus() & Status.OK.getStatusCode()) != Status.OK.getStatusCode()) {
					String msg = respFilter.readEntity(String.class);
					result.setException(new Exception(msg));
					return result;
				}
				Log.info(LOGGER, 
						"Changed filter [" + outputFilter.getName() + "] (" + outputFilter.getId() + ") " + 
						"owner to [" + outputFilter.getOwner().getAccountId() + "]");
			}
		} // else API disabled, count as success
		// Add to mapping
		mappings.get(MappingType.FILTER).getMapped().put(filter.getId(), outputFilter);
		return result;
	}
	
	/**
	 * Find a filter based on provided criteria.
	 * Return found filter. Returns null if not found.
	 */
	private static Filter filterExists(Config conf, String id, String name, String ownerAccountId) 
			throws Exception {
		Log.info(LOGGER, 
				"filterExists: " +
				"id: [" + id + "] " + 
				"name: [" + name + "] " +
				"owner: [" + ownerAccountId + "]");
		RestUtil<Filter> util = RestUtil.getInstance(Filter.class).config(conf, true);
		util.path("/rest/api/latest/filter/search")
			.method(HttpMethod.GET)
			.pagination(new Paged<Filter>(Filter.class).maxResults(1))
			.query("expand", "owner")
			.query("overrideSharePermissions", true);
		if (id != null) {
			util.query("id", id);
		}
		if (name != null) {
			util.query("filterName", "\"" + name + "\"");
		}
		if (ownerAccountId != null) {
			util.query("accountId", ownerAccountId);
		}
		List<Filter> list = util.requestAllPages();
		Log.info(LOGGER, 
				"filterExists: " +
				"id: [" + id + "] " + 
				"name: [" + name + "] " +
				"owner: [" + ownerAccountId + "] Count = " + list.size());
		if (list.size() == 1) {
			Log.info(LOGGER, 
					"filterExists: " +
					"id: [" + id + "] " + 
					"name: [" + name + "] " +
					"owner: [" + ownerAccountId + "] = true");
			return list.get(0);
		}
		Log.info(LOGGER, 
				"filterExists: " +
				"id: [" + id + "] " + 
				"name: [" + name + "] " +
				"owner: [" + ownerAccountId + "] = false");
		return null;
	}
	
}
