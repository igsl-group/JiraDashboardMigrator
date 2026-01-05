package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.HttpMethod;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.igsl.DashboardMigrator;
import com.igsl.config.Config;
import com.igsl.mybatis.FilterMapper;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class CustomFieldOption extends JiraObject<CustomFieldOption> {
	private String id;
	private String value;
	private String customFieldId;
	private String customFieldName;
	private String contextId;
	private String contextName;
	
	public static CustomFieldOption create(CustomFieldOptionDTO dto) {
		if (dto != null) {
			CustomFieldOption result = new CustomFieldOption();
			result.setId(dto.getId());
			result.setValue(dto.getValue());
			result.setCustomFieldId(dto.getCustomFieldId());
			result.setCustomFieldName(dto.getCustomFieldName());
			result.setContextId(dto.getContextId());
			result.setContextName(dto.getContextName());
			return result;
		}
		return null;
	}
	
	@Override
	public String getDisplay() {
		return value;
	}
	@Override
	public String getInternalId() {
		return id;
	}
	@Override
	public String getAdditionalDetails() {
		return "Custom field: " + customFieldId;
	}
	@Override
	public String getJQLName() {
		return value;
	}
	@Override
	public boolean jqlEquals(String value) {
		return 	this.id.equalsIgnoreCase(value) || 
				this.value.equalsIgnoreCase(value);
	}
	@Override
	public int compareTo(CustomFieldOption obj1, boolean exactMatch) {
		if (obj1 != null) {
			return 	STRING_COMPARATOR.compare(getValue(), obj1.getValue()) | 
					compareName(customFieldName, obj1.getCustomFieldName(), true);	// Enforce exact match
		}
		return 1;
	}
	@Override
	public void setupRestUtil(RestUtil<CustomFieldOption> util, boolean cloud, Object... data) {
		if (cloud) {
			String customFieldId = (String) data[0];
			String contextId = (String) data[1];
			util.path("/rest/api/3/field/{fieldId}/context/{contextId}/option")
				.pathTemplate("fieldId", customFieldId)
				.pathTemplate("contextId", contextId)
				.method(HttpMethod.GET)
				.status(HttpStatus.SC_OK, HttpStatus.SC_BAD_REQUEST, HttpStatus.SC_NOT_FOUND)
				.pagination(new Paged<CustomFieldOption>(CustomFieldOption.class));
		}
	}
	
	public static class Process implements Callable<List<CustomFieldOption>> {
		private Config config;
		private String customFieldId;
		private String contextId;
		public Process(Config config, String customFieldId, String contextId) {
			this.config = config;
			this.customFieldId = customFieldId;
			this.contextId = contextId;
		}
		@Override
		public List<CustomFieldOption> call() throws Exception {
			RestUtil<CustomFieldOption> util = RestUtil.getInstance(CustomFieldOption.class)
				.config(config, true)
				.path("/rest/api/3/field/{fieldId}/context/{contextId}/option")
				.pathTemplate("fieldId", customFieldId)
				.pathTemplate("contextId", contextId)
				.method(HttpMethod.GET)
				.status(HttpStatus.SC_OK, HttpStatus.SC_BAD_REQUEST, HttpStatus.SC_NOT_FOUND)
				.pagination(new Paged<CustomFieldOption>(CustomFieldOption.class));
			return util.requestAllPages();
		}
	}
	
	@Override
	protected List<CustomFieldOption> _getObjects(
			Config config, 
			Class<CustomFieldOption> dataClass, 
			boolean cloud,
			Object... data)
			throws Exception {
		List<CustomFieldOption> result = new ArrayList<>();
		if (cloud) {
			RestUtil<CustomFieldOption> util = RestUtil.getInstance(dataClass);
			util.config(config, cloud);
			List<CustomFieldContext> contextList = DashboardMigrator.readValuesFromFile(
					MappingType.CUSTOM_FIELD_CONTEXT.getCloud(), 
					CustomFieldContext.class);
			ExecutorService service = Executors.newFixedThreadPool(config.getThreadCount());
			try {
				Map<CustomFieldContext, Future<List<CustomFieldOption>>> futureMap = new HashMap<>();
				for (CustomFieldContext context : contextList) {
					futureMap.put(
							context, 
							service.submit(new Process(config, context.getCustomFieldId(), context.getId())));
				}
				while (futureMap.size() != 0) {
					List<CustomFieldContext> toRemove = new ArrayList<>();
					for (Map.Entry<CustomFieldContext, Future<List<CustomFieldOption>>> entry : futureMap.entrySet()) {
						try {
							CustomFieldContext context = entry.getKey();
							Future<List<CustomFieldOption>> future = entry.getValue();
							List<CustomFieldOption> list = future.get(config.getThreadWait(), TimeUnit.MILLISECONDS);
							toRemove.add(context);
							for (CustomFieldOption opt : list) {
								opt.setCustomFieldId(context.getCustomFieldId());
								opt.setContextId(context.getId());
								opt.setCustomFieldName(context.getCustomFieldName());
							}
							result.addAll(list);
						} catch (TimeoutException tex) {
							// Ignore and keep waiting
						}
					}
					for (CustomFieldContext f : toRemove) {
						futureMap.remove(f);
					}
				}
			} finally {
				service.shutdownNow();
			}
//			for (CustomFieldContext context : contextList) {
//				setupRestUtil(util, cloud, context.getCustomFieldId(), context.getId());
//				List<CustomFieldOption> list = util.requestAllPages();
//				for (CustomFieldOption opt : list) {
//					opt.setCustomFieldId(context.getCustomFieldId());
//					opt.setContextId(context.getId());
//					opt.setCustomFieldName(context.getCustomFieldName());
//				}
//				result.addAll(list);
//			}
		} else {
			SqlSessionFactory factory = DashboardMigrator.setupMyBatis(config);
			try (SqlSession session = factory.openSession()) {
				FilterMapper filterMapper = session.getMapper(FilterMapper.class);
				List<CustomField> fieldList = DashboardMigrator.readValuesFromFile(
						MappingType.CUSTOM_FIELD.getDC(), CustomField.class);
				for (CustomField cf : fieldList) {
					if (cf.getSchema() != null) {
						String cfId = cf.getSchema().getCustomId();
						if (cfId != null) {
							List<CustomFieldOptionDTO> list = filterMapper.getCustomFieldOptions(cfId);
							for (CustomFieldOptionDTO dto : list) {
								result.add(CustomFieldOption.create(dto));
							}
						}
					}
				}
			}
		}
		return result;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public String getCustomFieldId() {
		return customFieldId;
	}
	public void setCustomFieldId(String customFieldId) {
		this.customFieldId = customFieldId;
	}
	public String getContextId() {
		return contextId;
	}
	public void setContextId(String contextId) {
		this.contextId = contextId;
	}
	public String getCustomFieldName() {
		return customFieldName;
	}
	public void setCustomFieldName(String customFieldName) {
		this.customFieldName = customFieldName;
	}

	public String getContextName() {
		return contextName;
	}

	public void setContextName(String contextName) {
		this.contextName = contextName;
	}
	
}
