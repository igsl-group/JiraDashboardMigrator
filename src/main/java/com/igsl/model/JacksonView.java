package com.igsl.model;

/**
 * Interface for customizing Jackson serialization
 */
public interface JacksonView {
	interface Simple {}  
    interface Detailed extends Simple {} 
}
