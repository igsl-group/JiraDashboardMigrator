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
import com.igsl.Log;
import com.igsl.config.Config;
import com.igsl.rest.RestUtil;

public abstract class JiraObject<T> implements Comparable<T> {

	private static final Logger LOGGER = LogManager.getLogger();
	protected static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(String::compareTo);
	
	protected static final Pattern PATTERN = Pattern.compile("(.+?)( \\(migrated( [0-9]+)?\\))?");	

	/**
	 * Returns class name of implementation.
	 * This information is used to deserialize JiraObject from file.
	 */
	public final String getObjectType() {
		return this.getClass().getCanonicalName();
	}
	
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
	protected final int compareName(String name1, String name2, boolean exactMatch) {
		if (name1 != null && name2 != null) {
			if (!exactMatch) {
				Matcher matcher1 = PATTERN.matcher(name1);
				if (matcher1.matches()) {
					name1 = matcher1.group(1);
				}
				Matcher matcher2 = PATTERN.matcher(name2);
				if (matcher2.matches()) {
					name2 = matcher2.group(1);
				}
			}
			return STRING_COMPARATOR.compare(name1, name2);
		}
		return -1;
	}
	
	/**
	 * Return display name of this object. 
	 * Usually this is .getName().
	 */
	@JsonIgnore
	public abstract String getDisplay();
	
	/**
	 * Return internal id of this object.
	 * Usually this is .getId(), but can be .getName() as well.
	 * @return
	 */
	@JsonIgnore
	public abstract String getInternalId();

	/**
	 * Return identifier to be used in JQL.
	 * Usually this is .getId(), but can be .getKey() or .getName() as well.
	 * @return
	 */
	@JsonIgnore
	public abstract String getJQLName();
	
	/**
	 * Compare objects. 
	 * STRING_COMPARATOR can be used to perform null first string comparison.
	 */
	public final int compareTo(T obj1) {
		return compareTo(obj1, true);
	}
	
	/**
	 * Compare objects. 
	 * STRING_COMPARATOR can be used to perform null first string comparison.
	 * exactMatch controls if (migrated #) is accpeted as a match.
	 * Implementations can ignore exactMatch if name must be identical.
	 */
	public abstract int compareTo(T obj1, boolean exactMatch);
	
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
