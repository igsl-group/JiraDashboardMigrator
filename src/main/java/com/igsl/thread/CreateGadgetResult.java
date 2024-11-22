package com.igsl.thread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.igsl.model.CloudGadget;
import com.igsl.model.DataCenterPortletConfiguration;

public class CreateGadgetResult {
	private DataCenterPortletConfiguration originalGadget;
	private CloudGadget createdGadget;
	private String createResult;
	private Map<String, String> configurationResult = new HashMap<>();
	private List<String> mappingWarnings = new ArrayList<>();
	private List<String> mappingErrors = new ArrayList<>();
	public DataCenterPortletConfiguration getOriginalGadget() {
		return originalGadget;
	}
	public void setOriginalGadget(DataCenterPortletConfiguration originalGadget) {
		this.originalGadget = originalGadget;
	}
	public CloudGadget getCreatedGadget() {
		return createdGadget;
	}
	public void setCreatedGadget(CloudGadget createdGadget) {
		this.createdGadget = createdGadget;
	}
	public String getCreateResult() {
		return createResult;
	}
	public void setCreateResult(String createResult) {
		this.createResult = createResult;
	}
	public Map<String, String> getConfigurationResult() {
		return configurationResult;
	}
	public void setConfigurationResult(Map<String, String> configurationResult) {
		this.configurationResult = configurationResult;
	}
	public List<String> getMappingWarnings() {
		return mappingWarnings;
	}
	public void setMappingWarnings(List<String> mappingWarnings) {
		this.mappingWarnings = mappingWarnings;
	}
	public List<String> getMappingErrors() {
		return mappingErrors;
	}
	public void setMappingErrors(List<String> mappingErrors) {
		this.mappingErrors = mappingErrors;
	}
}