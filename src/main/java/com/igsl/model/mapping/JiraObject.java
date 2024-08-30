package com.igsl.model.mapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.igsl.config.Config;
import com.igsl.rest.RestUtil;

public abstract class JiraObject<T> implements Comparable<T> {

	private static final Logger LOGGER = LogManager.getLogger();
	protected static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(String::compareTo);
	
	/**
	 * Get unique name for the implementation. 
	 * Default is to call .getId(), if not present, .getName().
	 * Override if it isn't either of those.
	 * @return String
	 */
	@JsonIgnore
	public String getUniqueName() throws Exception {
		String result = null;
		Method m = null;
		String methodName = null;
		Method[] list = this.getClass().getDeclaredMethods();
		try {
			m = this.getClass().getDeclaredMethod("getId");
			methodName = "getId()";
		} catch (Exception ex) {
			// Ignore
		}
		if (m == null) {
			try {
				m = this.getClass().getDeclaredMethod("getName");
				methodName = "getName()";
			} catch (Exception ex) {
				// Ignore
			}
		}
		if (m == null) {
			throw new NoSuchMethodException(
					"Class " + this.getClass().getCanonicalName() + 
					" does not contain .getId() or .getName(), it must override .getUniqueName()");
		}
		try {
			result = String.valueOf(m.invoke(this));
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new Exception("Unable to invoke " + this.getClass().getCanonicalName() + "." + methodName, e);
		}
		return result;
	}
	
	/**
	 * Compare objects. 
	 * STRING_COMPARATOR can be used to perform null first string comparison.
	 */
	public abstract int compareTo(T obj1);
	
	/**
	 * Setup RestUtil for API calls. 
	 * Scheme and host will be already set.
	 * Set path, pathTemplate, method, pagination.
	 * @param util RestUtil instance
	 * @param cloud Cloud if true, server if false
	 * @param Additional data. Override _getObjects() to pass these parameters.
	 */
	public abstract void setupRestUtil(RestUtil<T> util, boolean cloud, Object... data);

	/**
	 * Get objects from Server/Cloud. 
	 * Override if needed. Copy function body and modify.
	 * Use additional members to pass data to setupRestUtil().
	 * 
	 * @param config Config instance
	 * @param dataClass Class of JiraObject subclass
	 * @param cloud Cloud if true, server if false
	 * @param map Map of objects exported so far
	 * @return List of data object
	 * @throws Exception
	 */
	protected List<T> _getObjects(
			Config config, 
			Class<T> dataClass, 
			boolean cloud,
			Map<MappingType, List<? extends JiraObject<?>>> map, 
			Object... data)
			throws Exception {
		RestUtil<T> util = RestUtil.getInstance(dataClass);
		util.config(config, cloud);
		setupRestUtil(util, cloud, data);
		return util.requestAllPages();
	}
	
	/** 
	 * Get objects from server.
	 * @param config Config instance
	 * @param cloud Cloud if true, server if false
	 * @param dataClass Class of JiraObject implementation
	 * @param map Map of objects retrieved so far. Some objects depend on other objects
	 * @param data Implementation-specific data.
	 */
	@SuppressWarnings("unchecked")
	public static final <U> List<U> getObjects(
			Config config, 
			Class<U> dataClass, 
			boolean cloud, 
			Map<MappingType, List<? extends JiraObject<?>>> map, 
			Object... data) 
			throws Exception {
		// Get a dummy instance of POJO class to invoke its .export() method
		U cls = dataClass.getConstructor().newInstance();
		Method method = dataClass.getSuperclass().getDeclaredMethod(
				"_getObjects", Config.class, Class.class, boolean.class, Map.class, Object[].class);
		return (List<U>) method.invoke(cls, config, dataClass, cloud, map, data);
	}
}
