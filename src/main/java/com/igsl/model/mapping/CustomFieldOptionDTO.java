package com.igsl.model.mapping;

/**
 * A clone of CustomFieldOption, because MyBatis cannot instantiate JiraObject.
 */
public class CustomFieldOptionDTO  {
	private String id;
	private String value;
	private String customFieldId;
	private String customFieldName;
	private String contextId;
	private String contextName;
	
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
