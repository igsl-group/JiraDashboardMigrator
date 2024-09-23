package com.igsl.model.mapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.igsl.config.Config;
import com.igsl.rest.RestUtil;

public abstract class JiraObject<T> implements Comparable<T> {

	private static final Logger LOGGER = LogManager.getLogger();
	protected static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(String::compareTo);
	
	protected static final Pattern PATTERN = Pattern.compile("(.+?)( \\(migrated( [0-9]+)?\\))?");	

	/**
	 * Checks if name matches (migrated #)
	 */
	public final boolean isMigrated() {
		try {
			Matcher matcher = PATTERN.matcher(getDisplay());
			if (matcher.matches()) {
				return (null != matcher.group(2));
			}
		} catch (Exception ex) {
			// Ignore
		}
		return false;
	}
	
	// Compare names with option to allow (migrated #)
	protected final int compareName(String name1, String name2) {
		if (name1 != null && name2 != null) {
			Matcher matcher1 = PATTERN.matcher(name1);
			if (matcher1.matches()) {
				name1 = matcher1.group(1);
			}
			Matcher matcher2 = PATTERN.matcher(name2);
			if (matcher2.matches()) {
				name2 = matcher2.group(1);
			}
			return STRING_COMPARATOR.compare(name1, name2);
		}
		return -1;
	}
	
	/**
	 * Get display name for the implementation.
	 * Default is to call .getName() or .getDisplayName() or getUniqueName().
	 * @return
	 * @throws Exception
	 */
	@JsonIgnore
	public String getDisplay() throws Exception {
		String result = null;
		Method m = null;
		String methodName = null;
		try {
			m = this.getClass().getDeclaredMethod("getName");
			methodName = "getName()";
		} catch (Exception ex) {
			// Ignore
		}
		if (m == null) {
			try {
				m = this.getClass().getDeclaredMethod("getDisplayName");
				methodName = "getDisplayName()";
			} catch (Exception ex) {
				// Ignore
			}
		}
		if (m == null) {
			return getUniqueName();
		}
		try {
			result = String.valueOf(m.invoke(this));
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new Exception("Unable to invoke " + this.getClass().getCanonicalName() + 
					"." + methodName, e);
		}
		return result;
	}
	
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
			throw new Exception("Unable to invoke " + this.getClass().getCanonicalName() + 
					"." + methodName, e);
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
			Object... data) 
			throws Exception {
		// Get a dummy instance of POJO class to invoke its .export() method
		U cls = dataClass.getConstructor().newInstance();
		Method method = dataClass.getSuperclass().getDeclaredMethod(
				"_getObjects", Config.class, Class.class, boolean.class, Object[].class);
		return (List<U>) method.invoke(cls, config, dataClass, cloud, data);
	}
}
