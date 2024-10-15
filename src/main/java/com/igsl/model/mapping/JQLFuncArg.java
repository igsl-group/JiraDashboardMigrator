package com.igsl.model.mapping;

public class JQLFuncArg {
	private boolean optional;
	private boolean varArgs;
	private MappingType mappingType;
	public JQLFuncArg(boolean optional, boolean varArgs) {
		this.mappingType = null;
		this.optional = optional;
		this.varArgs = varArgs;
	}
	public JQLFuncArg(MappingType mappingType, boolean optional, boolean varArgs) {
		this.mappingType = mappingType;
		this.optional = optional;
		this.varArgs = varArgs;
	}
	public boolean isOptional() {
		return optional;
	}
	public boolean isVarArgs() {
		return varArgs;
	}
	public MappingType getMappingType() {
		return mappingType;
	}
}
