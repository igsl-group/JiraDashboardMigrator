package com.igsl.model.mapping;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Custom Jackson deserializer for JiraObject
 */
public class JiraObjectDeserializer extends JsonDeserializer<JiraObject<?>> {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final ObjectMapper OM = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			// Allow comments
			.configure(Feature.ALLOW_COMMENTS, true)	
			// Allow attributes missing in POJO
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	
	@Override
    public JiraObject<?> deserialize(JsonParser jp, DeserializationContext ctxt) 
    		throws IOException, JsonProcessingException {
		TreeNode node = jp.readValueAsTree();
        TreeNode objectType = node.get("objectType");
        if (objectType != null && objectType instanceof TextNode) {
        	TextNode tn = (TextNode) objectType;
        	String type = tn.asText();
			try {
	        	@SuppressWarnings("unchecked")
				Class<JiraObject<?>> cls = (Class<JiraObject<?>>) Class.forName(type);
	        	ObjectReader reader = OM.readerFor(cls);
	        	// TODO Cannot read twice
	        	JiraObject<?> result = reader.readValue(node.toString());
	        	return result;
			} catch (ClassNotFoundException cnfex) {
				// JsonProcessingException ctor is protected, so make a subclass
				throw new JsonProcessingException("objectType value is not valid", cnfex) {};
			}
        }
		throw new JsonProcessingException("objectType is not specified") {};
    }

}