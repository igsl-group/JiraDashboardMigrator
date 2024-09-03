package com.igsl.model.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Mapping {
	private MappingType type;
	private Map<String, String> mapped = new LinkedHashMap<>();
	private Map<String, List<String>> conflict = new LinkedHashMap<>();
	private List<Object> unmapped = new ArrayList<>();
	private Map<String, String> failed = new LinkedHashMap<>();

	public Mapping() {
	}

	public Mapping(MappingType type) {
		this.type = type;
	}

	public List<Object> getUnmapped() {
		return unmapped;
	}

	public void setUnmapped(List<Object> unmapped) {
		this.unmapped = unmapped;
	}

	public Map<String, List<String>> getConflict() {
		return conflict;
	}

	public void setConflict(Map<String, List<String>> conflict) {
		this.conflict = conflict;
	}

	public Map<String, String> getMapped() {
		return mapped;
	}

	public void setMapped(Map<String, String> mapped) {
		this.mapped = mapped;
	}

	public MappingType getType() {
		return type;
	}

	public void setType(MappingType type) {
		this.type = type;
	}

	public Map<String, String> getFailed() {
		return failed;
	}

	public void setFailed(Map<String, String> failed) {
		this.failed = failed;
	}
}