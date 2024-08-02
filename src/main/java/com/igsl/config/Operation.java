package com.igsl.config;

public enum Operation {
	DUMP_DATACENTER("dumpDC"),
	DUMP_DATACENTER_FILTER_DASHBOARD("dumpDCFilterDashboard"),
	DUMP_CLOUD("dumpCloud"),
	MAP_OBJECT("mapObject"),
	
	MAP_FILTER("mapFilter"),
	CREATE_FILTER("createFilter"), 
	DELETE_FILTER("deleteFilter"),
	LIST_FILTER("listFilter"), 
	
	MAP_DASHBOARD("mapDashboard"),
	CREATE_DASHBOARD("createDashboard"),
	DELETE_DASHBOARD("deleteDashboard"), 
	LIST_DASHBOARD("listDashboard");
	
	private String value;

	private Operation(String s) {
		this.value = s;
	}

	public static Operation parse(String s) {
		for (Operation o : Operation.values()) {
			if (o.value.equals(s)) {
				return o;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return value;
	}
}
