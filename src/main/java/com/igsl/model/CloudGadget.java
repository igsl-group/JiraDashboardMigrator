package com.igsl.model;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class CloudGadget {
	
	public CloudGadget clone() {
		CloudGadget result = new CloudGadget();
		result.setId(this.id);
		result.setColor(this.color);
		result.setIgnoreUriAndModuleKeyValidation(this.ignoreUriAndModuleKeyValidation);
		result.setTitle(this.title);
		result.setModuleKey(this.moduleKey);
		result.setUri(this.uri);
		Position position = new Position();
		position.setColumn(this.position.getColumn());
		position.setRow(this.position.getRow());
		result.setPosition(position);
		return result;
	}
	
	@JsonView(JacksonView.Simple.class)
	private String id;
	@JsonView(JacksonView.Simple.class)
	private Color color;
	@JsonView(JacksonView.Simple.class)
	private boolean ignoreUriAndModuleKeyValidation = false;
	@JsonView(JacksonView.Simple.class)
	private String title;
	@JsonView(JacksonView.Simple.class)
	private String moduleKey;
	@JsonView(JacksonView.Simple.class)
	private String uri;
	@JsonView(JacksonView.Simple.class)
	private Position position;
	@JsonView(JacksonView.Detailed.class)
	private Map<String, Object> configurations;
	
	public static CloudGadget create(DataCenterPortletConfiguration data, boolean includeConfiguration) {
		CloudGadget result = null;
		if (data != null) {
			result = new CloudGadget();
			result.color = Color.parse(data.getColor());
			result.title = null; // Data Center does not have customizable title for gadget unless the gadget
									// itself has a configuration
			result.moduleKey = data.getDashboardCompleteKey();
			if (data.getDashboardCompleteKey() == null) {
				result.uri = data.getGadgetXml();
			}
			result.position = new Position();
			result.position.row = data.getPositionSeq();
			result.position.column = data.getColumnNumber();
			
			if (includeConfiguration) {
				result.configurations = new TreeMap<>();
				for (DataCenterGadgetConfiguration conf : data.getGadgetConfigurations()) {
					result.configurations.put(conf.getUserPrefKey(), conf.getUserPrefValue());
				}
			} 
		}
		return result;
	}

	public boolean isIgnoreUriAndModuleKeyValidation() {
		return ignoreUriAndModuleKeyValidation;
	}

	public void setIgnoreUriAndModuleKeyValidation(boolean ignoreUriAndModuleKeyValidation) {
		this.ignoreUriAndModuleKeyValidation = ignoreUriAndModuleKeyValidation;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getModuleKey() {
		return moduleKey;
	}

	public void setModuleKey(String moduleKey) {
		this.moduleKey = moduleKey;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public Position getPosition() {
		return position;
	}

	public void setPosition(Position position) {
		this.position = position;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public Map<String, Object> getConfigurations() {
		return configurations;
	}

	public void setConfigurations(Map<String, Object> configurations) {
		this.configurations = configurations;
	}
}
