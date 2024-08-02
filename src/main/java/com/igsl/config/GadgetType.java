package com.igsl.config;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.igsl.Log;

public class GadgetType {
	
	private static final Comparator<String> stringComparator = Comparator.nullsFirst(Comparator.naturalOrder());
	
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String DIR_NAME = "GadgetType";
	private static List<GadgetType> LIST;
	
	private String description;
	private String moduleKey;
	private StringCompareType moduleKeyCompare;
	private String uri;
	private StringCompareType uriCompare;
	private String newModuleKey;
	private String newUri;
	
	private GadgetConfigType configType;
	private List<GadgetConfigMapping> config = new ArrayList<>();

	private static final ObjectMapper OM = new ObjectMapper()
			.configure(Feature.ALLOW_COMMENTS, true)
			.enable(SerializationFeature.INDENT_OUTPUT);
	
	static {
		LIST = new ArrayList<>();
		try {
			ClassLoader cl = GadgetType.class.getClassLoader();
			ObjectReader reader = OM.readerFor(GadgetType.class);
			URL url = cl.getResource(DIR_NAME);
			File file = new File(url.toURI());
			Files.list(file.toPath()).filter(p -> p.toString().toLowerCase().endsWith(".json")).forEach(
					new Consumer<Path>() {
						@Override
						public void accept(Path t) {
							try (	FileReader fr = new FileReader(t.toFile())) {
								MappingIterator<GadgetType> list = reader.readValues(fr);
								while (list.hasNext()) {
									LIST.add(list.next());
								}
							} catch (IOException ioex) {
								Log.error(LOGGER, "Error loading Gadget mapping configuration", ioex);
							}
						}
					}
				);
		} catch (Exception e) {
			Log.error(LOGGER, "Error loading Gadget mapping configuration", e);
		}
	}

	public static GadgetType parse(String moduleKey, String uri) {
		for (GadgetType type : LIST) {
			if (stringComparator.compare(moduleKey, type.moduleKey) == 0 && 
				stringComparator.compare(uri, type.uri) == 0) {
				return type;
			}
		}
		return null;
	}

	// Get GadgetConfigMapping based on configuration key
	public GadgetConfigMapping getConfig(String key) {
		for (GadgetConfigMapping conf : this.getConfig()) {
			if (conf.getAttributeNamePattern() != null) {
				Matcher m = conf.getAttributeNamePattern().matcher(key);
				if (m.matches()) {
					return conf;
				}
			}
		}
		return null;
	}

	// Generated
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getModuleKey() {
		return moduleKey;
	}
	public void setModuleKey(String moduleKey) {
		this.moduleKey = moduleKey;
	}
	public StringCompareType getModuleKeyCompare() {
		return moduleKeyCompare;
	}
	public void setModuleKeyCompare(StringCompareType moduleKeyCompare) {
		this.moduleKeyCompare = moduleKeyCompare;
	}
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public StringCompareType getUriCompare() {
		return uriCompare;
	}
	public void setUriCompare(StringCompareType uriCompare) {
		this.uriCompare = uriCompare;
	}
	public String getNewModuleKey() {
		return newModuleKey;
	}
	public void setNewModuleKey(String newModuleKey) {
		this.newModuleKey = newModuleKey;
	}
	public String getNewUri() {
		return newUri;
	}
	public void setNewUri(String newUri) {
		this.newUri = newUri;
	}
	public GadgetConfigType getConfigType() {
		return configType;
	}
	public void setConfigType(GadgetConfigType configType) {
		this.configType = configType;
	}
	public List<GadgetConfigMapping> getConfig() {
		return config;
	}
	public void setConfig(List<GadgetConfigMapping> config) {
		this.config = config;
	}
}
