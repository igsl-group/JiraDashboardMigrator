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

import org.apache.commons.httpclient.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.igsl.DashboardMigrator;
import com.igsl.Log;
import com.igsl.config.Config;
import com.igsl.rest.Paged;
import com.igsl.rest.RestUtil;

public class CustomFieldContext extends JiraObject<CustomFieldContext> {
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	private String customFieldId;
	private String customFieldName;
	private String id;
	private String name;
	
	@Override
	public String getDisplay() {
		return name;
	}
	@Override
	public String getInternalId() {
		return id;
	}
	@Override
	public String getJQLName() {
		return name;
	}
	@Override
	public int compareTo(CustomFieldContext obj1, boolean exactMatch) {
		if (obj1 != null) {
			return	STRING_COMPARATOR.compare(getName(), obj1.getName()) | 
					compareName(customFieldName, obj1.getCustomFieldName(), exactMatch);
		}
		return 1;
	}
	@Override
	public void setupRestUtil(RestUtil<CustomFieldContext> util, boolean cloud, Object... data) {
		if (cloud) {
			String customFieldId = (String) data[0];
			util.path("/rest/api/3/field/{fieldId}/context")
				.pathTemplate("fieldId", customFieldId)
				.method(HttpMethod.GET)
				.status(HttpStatus.SC_OK, HttpStatus.SC_NOT_FOUND)
				.pagination(new Paged<CustomFieldContext>(CustomFieldContext.class));
		}
	}
	
	private static class Process implements Callable<List<CustomFieldContext>> {
		private String fieldId;
		private Config config;
		public Process(Config config, String fieldId) {
			this.config = config;
			this.fieldId = fieldId;
		}
		@Override
		public List<CustomFieldContext> call() throws Exception {
			RestUtil<CustomFieldContext> util = RestUtil.getInstance(CustomFieldContext.class)
					.config(config, true);
			util.path("/rest/api/3/field/{fieldId}/context")
				.pathTemplate("fieldId", fieldId)
				.method(HttpMethod.GET)
				.status(HttpStatus.SC_OK, HttpStatus.SC_NOT_FOUND)
				.pagination(new Paged<CustomFieldContext>(CustomFieldContext.class));
			return util.requestAllPages();
		}
	}
	
	@Override
	protected List<CustomFieldContext> _getObjects(
			Config config, 
			Class<CustomFieldContext> dataClass, 
			boolean cloud,
			Object... data)
			throws Exception {
		if (cloud) {
			List<CustomFieldContext> result = new ArrayList<>();
//			RestUtil<CustomFieldContext> util = RestUtil.getInstance(dataClass);
//			util.config(config, cloud);
			List<CustomField> fieldList = DashboardMigrator.readValuesFromFile(
					(cloud? MappingType.CUSTOM_FIELD.getCloud() : MappingType.CUSTOM_FIELD.getDC()), 
					CustomField.class);
			ExecutorService service = Executors.newFixedThreadPool(config.getThreadCount());
			try {
				Map<CustomField, Future<List<CustomFieldContext>>> futureMap = new HashMap<>();
				for (CustomField field : fieldList) {
					futureMap.put(field, service.submit(new Process(config, field.getId())));
				}
				while (futureMap.size() != 0) {
					List<CustomField> toRemove = new ArrayList<>();
					for (Map.Entry<CustomField, Future<List<CustomFieldContext>>> entry : futureMap.entrySet()) {
						try {
							CustomField field = entry.getKey();
							Future<List<CustomFieldContext>> future = entry.getValue();
							List<CustomFieldContext> list = future.get(config.getThreadWait(), TimeUnit.MILLISECONDS);
							toRemove.add(field);
							for (CustomFieldContext ctx : list) {
								ctx.setCustomFieldId(field.getId());
								ctx.setCustomFieldName(field.getName());
							}
							result.addAll(list);
						} catch (TimeoutException tex) {
							// Ignore and keep waiting
						}
					}
					for (CustomField f : toRemove) {
						futureMap.remove(f);
					}
				}
			} finally {
				service.shutdownNow();
			}
//			for (CustomField field : fieldList) {
//				setupRestUtil(util, cloud, field.getId());
//				List<CustomFieldContext> list = util.requestAllPages();
//				for (CustomFieldContext ctx : list) {
//					ctx.setCustomFieldId(field.getId());
//					ctx.setCustomFieldName(field.getName());
//				}
//				result.addAll(list);
//			}
			return result;
		} 
		throw new Exception("CustomFieldContext cannot be retrieved via REST API for DC");
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getCustomFieldId() {
		return customFieldId;
	}
	public void setCustomFieldId(String customFieldId) {
		this.customFieldId = customFieldId;
	}
	public String getCustomFieldName() {
		return customFieldName;
	}
	public void setCustomFieldName(String customFieldName) {
		this.customFieldName = customFieldName;
	}
	
}
