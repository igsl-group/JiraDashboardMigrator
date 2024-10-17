package com.igsl.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Jackson mixin to include ignored members
 */
public class CloudDashboardMixin {
	@JsonIgnore(false)
	private List<CloudGadget> gadgets;
}
