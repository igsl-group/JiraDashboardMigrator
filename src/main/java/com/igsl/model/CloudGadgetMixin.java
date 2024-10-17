package com.igsl.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Jackson mixin to include ignored members
 */
public class CloudGadgetMixin {
	@JsonIgnore(false)
	private Map<String, Object> configurations;
}
