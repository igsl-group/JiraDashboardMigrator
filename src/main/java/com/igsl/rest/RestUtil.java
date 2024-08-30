package com.igsl.rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.igsl.Log;
import com.igsl.config.Config;

public class RestUtil<T> {

	private static final String ENCODDING = "ASCII";	
	private static final String DEFAULT_SCHEME = "https";
	private static final String DEFAULT_METHOD = HttpMethod.GET;
	private static final long DEFAULT_MAX_CALL = 100L;
	private static final long DEFAULT_PERIOD_MUILLISECOND = 1000L;
	
	private static final Logger LOGGER = LogManager.getLogger();
	private static final ObjectMapper OM = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	private static final JacksonJsonProvider JACKSON_JSON_PROVIDER = 
			new JacksonJaxbJsonProvider()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(SerializationFeature.INDENT_OUTPUT, true);

	// URL
	private String scheme = DEFAULT_SCHEME;
	private String host;
	private String path;
	private Map<String, String> pathTemplates;
	private String method = DEFAULT_METHOD;
	
	// Headers
	private MultivaluedMap<String, Object> headers;
	
	// Authentication header
	private MultivaluedMap<String, Object> authHeader;
	
	// Query
	private Map<String, Object> query;
	
	// Payload
	private Object payload;
	
	// Status
	private boolean bitwiseStatus = true;
	private List<Integer> statusList = Arrays.asList(Status.OK.getStatusCode());
	
	// Paging
	private Pagination<T> pagination;
	
	// Throttle
	private long lastCheck = System.currentTimeMillis();
	private Float allowance = null;
	private long maxCall = DEFAULT_MAX_CALL;
	private long periodMS = DEFAULT_PERIOD_MUILLISECOND;
	
	/**
	 * Create instance.
	 * @param <T> Data class, use Object if unknown.
	 */
	public static <T> RestUtil<T> getInstance(Class<T> dataClass) {
		return new RestUtil<T>(dataClass);
	}

	private RestUtil(Class<T> dataClass) {
		this.pathTemplates = new HashMap<>();
		this.headers = new MultivaluedHashMap<>();
		this.query = new HashMap<>();
		this.pagination = new SinglePage<>(dataClass);
	}

	/**
	 * Set throttling.
	 * @param maxCall Max. no. of REST API calls in a period. Default is {@value #DEFAULT_MAX_CALL}.
	 * @param periodMS Period defined as milliseconds. Default is {@value #DEFAULT_PERIOD_MUILLISECOND}.
	 * @return
	 */
	public RestUtil<T> throttle(Integer maxCall, Integer periodMS) {
		this.maxCall = maxCall;
		this.periodMS = periodMS;
		return this;
	}
	
	/**
	 * Configure using {@link Config} object.
	 * Includes:
	 * - scheme
	 * - host
	 * - authenticate 
	 * - throttle rate
	 * - throttle period
	 * @param config
	 * @throws UnsupportedEncodingException 
	 * @throws URISyntaxException 
	 */
	public RestUtil<T> config(Config config, boolean cloud) 
			throws UnsupportedEncodingException, URISyntaxException {
		if (cloud) {
			return this
					.scheme(DEFAULT_SCHEME)
					.host(config.getTargetHost())
					.authenticate(config.getTargetUser(), config.getTargetAPIToken());
		} else {
			URI uri = new URI(config.getSourceRESTBaseURL());
			return this
					.scheme(uri.getScheme())
					.host(config.getSourceHost())
					.authenticate(config.getSourceUser(), config.getSourcePassword());
		}
	}
	
	/**
	 * Set scheme. Default is {@value #DEFAULT_SCHEME}.
	 */
	public RestUtil<T> scheme(String scheme) {
		this.scheme = scheme;
		return this;
	}
	
	/**
	 * Set host. e.g. localhost:8080
	 */
	public RestUtil<T> host(String host) {
		this.host = host;
		return this;
	}
	
	/**
	 * Set path. You can include path templates in the format of {name}.
	 * e.g. /rest/api/2/dosomething/{id}
	 */
	public RestUtil<T> path(String path) {
		this.path = path;
		return this;
	}
	
	/**
	 * Set a single path template.
	 * @param variable Name of path template.
	 * @param value Value of path template.
	 */
	public RestUtil<T> pathTemplate(String variable, String value) {
		this.pathTemplates.put(variable, value);
		return this;
	}
	
	/**
	 * Set path templates. This will overwrite all existing path templates.
	 * @param urlTemplates Map<String, String> of name to value pairs.
	 */
	public RestUtil<T> pathTemplates(Map<String, String> urlTemplates) {
		this.pathTemplates = urlTemplates;
		return this;
	}
	
	/**
	 * Set method. Default is {@value #DEFAULT_METHOD}.
	 */
	public RestUtil<T> method(String method) {
		this.method = method;
		return this;
	}
	
	/**
	 * Set a single HTTP header.
	 * @param name Header name.
	 * @param value Header value.
	 */
	public RestUtil<T> header(String name, Object value) {
		List<Object> values = this.headers.get(name);
		if (values == null) {
			values = new ArrayList<>();
		}
		values.add(values);
		this.headers.put(name, values);
		return this;
	}
	
	/**
	 * Set HTTP headers. This will overwrite all existing headers.
	 * @param headers MultivaluedMap<String, Object> of header name to value pairs.
	 */
	public RestUtil<T> headers(MultivaluedMap<String, Object> headers) {
		if (headers == null) {
			headers = new MultivaluedHashMap<>();
		}
		this.headers = headers;
		return this;
	}
	
	/**
	 * Set basic authentication. For Cloud, provide email as user and API token as password.
	 * @param user User name.
	 * @param password Password.
	 * @throws UnsupportedEncodingException
	 */
	public RestUtil<T> authenticate(String user, String password) throws UnsupportedEncodingException {
		this.authHeader = new MultivaluedHashMap<>();
		String headerValue = "Basic " + 
				Base64.getEncoder().encodeToString((user + ":" + password).getBytes(ENCODDING));
		List<Object> values = new ArrayList<>();
		values.add(headerValue);
		authHeader.put("Authorization", values);
		return this;
	}
	
	/**
	 * Set a single query parameter.
	 * @param name Parameter name.
	 * @param value Parameter value.
	 */
	public RestUtil<T> query(String name, Object value) {
		this.query.put(name, value);
		return this;
	}
	
	/**
	 * Set query parameters. This will overwrite existing query parameters.
	 * @param query Map<String, Object> of parameter name to value pairs.
	 */
	public RestUtil<T> query(Map<String, Object> query) {
		if (query == null) {
			query = new HashMap<>();
		}
		this.query = query;
		return this;
	}
	
	/**
	 * Set payload for the request. 
	 * @param payload Object.
	 */
	public RestUtil<T> payload(Object payload) {
		this.payload = payload;
		return this;
	}
	
	/**
	 * Set valid status codes. These codes with be checked with bitwise AND.
	 * Default is [HttpStatus.SC_OK]
	 * @param statuses Valid status codes.
	 */
	public RestUtil<T> status(int... statuses) {
		this.bitwiseStatus = true;
		this.statusList = new ArrayList<>();
		for (int status : statuses) {
			this.statusList.add(status);
		}
		return this;
	}
	
	/**
	 * Set valid status codes.
	 * Default is true, [HttpStatus.SC_OK]
	 * @param bitwiseAnd If false, status code must match exactly. Otherwise checked with bitwise AND.
	 * @param statuses Valid status codes.
	 */
	public RestUtil<T> status(boolean bitwiseAnd, int... statuses) {
		this.bitwiseStatus = bitwiseAnd;
		this.statusList = new ArrayList<>();
		for (int status : statuses) {
			this.statusList.add(status);
		}
		return this;
	}

	/**
	 * Set pagination method. Default is {@link SinglePage}.
	 * @param pagination Pagination subclass.
	 * @return
	 */
	public RestUtil<T> pagination(Pagination<T> pagination) {
		this.pagination = pagination;
		return this;
	}
	
	/**
	 * Invoke REST API without pagination, validates status code and return the response. 
	 * @return Response.
	 * @throws URISyntaxException 
	 * @throws IllegalStateException If status code does not match.
	 */
	public Response request() 
			throws URISyntaxException, IllegalStateException, JsonProcessingException, JsonMappingException {
		Client client = ClientBuilder.newClient();
		client.register(JACKSON_JSON_PROVIDER);
		String finalPath = this.path;
		for (Map.Entry<String, String> entry : pathTemplates.entrySet()) {
			finalPath = finalPath.replaceAll(Pattern.quote("{" + entry.getKey() + "}"), entry.getValue());
		}
		URI uri = new URI(scheme + "://" + host).resolve(finalPath);
		WebTarget target = client.target(uri);
		Log.debug(LOGGER, "uri: " + uri.toASCIIString() + " " + method);
		if (query != null) {
			for (Map.Entry<String, Object> item : query.entrySet()) {
				Log.debug(LOGGER, "query: " + item.getKey() + " = " + item.getValue());
				target = target.queryParam(item.getKey(), item.getValue());
			}
		}
		Builder builder = target.request();
		MultivaluedMap<String, Object> finalHeaders = new MultivaluedHashMap<>();
		if (headers != null) {
			finalHeaders.putAll(headers);
		}
		if (authHeader != null) {
			finalHeaders.putAll(authHeader);
		}
		builder = builder.headers(finalHeaders);
		for (Map.Entry<String, List<Object>> header : finalHeaders.entrySet()) {
			for (Object o : header.getValue()) {
				Log.debug(LOGGER, "header: " + header.getKey() + " = " + o);
			}
		}
		// Check throttle
		if (this.allowance == null) {
			this.allowance = (float) this.maxCall;
		}
		long now = System.currentTimeMillis();
		long timePassed = now - this.lastCheck;
		Log.debug(LOGGER, "Throttle: Time passed: " + timePassed);
		this.lastCheck = now;
		this.allowance += timePassed * (this.maxCall / this.periodMS);
		Log.debug(LOGGER, "Throttle: Allowance: " + allowance);
		Log.debug(LOGGER, "Throttle: RATE: " + this.maxCall);
		if (this.allowance > this.maxCall) {
			Log.debug(LOGGER, "Throttle: Allowance > RATE, throttle");
			this.allowance = (float) this.maxCall;
		}
		if (this.allowance < 1) {
			Log.debug(LOGGER, "Throttle: Allowance < 1, sleeping");
			try {
				Thread.sleep(this.periodMS);
			} catch (InterruptedException e) {
				Log.warn(LOGGER, "Sleep interrupted", e);
			}
		}
		Log.debug(LOGGER, "Throttle: Executing, allowance - 1");
		this.allowance -= 1;
		// Invoke
		Response response = null;
		switch (this.method) {
		case HttpMethod.DELETE:
			response = builder.delete();
			break;
		case HttpMethod.GET:
			response = builder.get();
			break;
		case HttpMethod.HEAD:
			response = builder.head();
			break;
		case HttpMethod.OPTIONS:
			response = builder.options();
			break;
		case HttpMethod.POST:
			if (payload != null) {
				if (String.class.isAssignableFrom(payload.getClass())) {
					response = builder.post(Entity.entity(payload, MediaType.TEXT_PLAIN));
				} else {
					response = builder.post(Entity.entity(payload, MediaType.APPLICATION_JSON));
				}
			} 
			break;
		case HttpMethod.PUT:
			if (payload != null) {
				if (String.class.isAssignableFrom(payload.getClass())) {
					response = builder.post(Entity.entity(payload, MediaType.TEXT_PLAIN));
				} else {
					response = builder.put(Entity.entity(payload, MediaType.APPLICATION_JSON));
				}
			}
			break;
		default:
			throw new IllegalArgumentException("Invalid method \"" + method + "\"");
		}
		// Check status if statusList is provided
		if (statusList != null && statusList.size() != 0) {
			boolean statusValid = false;
			int respStatus = response.getStatus();
			if (bitwiseStatus) {
				for (int status : statusList) {
					if ((respStatus & status) == status) {
						statusValid = true;
						break;
					}
				}
			} else {
				for (int status : statusList) {
					if (respStatus == status) {
						statusValid = true;
						break;
					}
				}
			}
			if (!statusValid) {
				throw new IllegalStateException(response.readEntity(String.class));
			}
		} 
		return response;
	}
	
	/**
	 * Invoke REST API with pagination, validate status, and retrieve the next page of results.
	 * @return List<T>
	 * @throws URISyntaxException 
	 * @throws IllegalStateException 
 	 * @throws JsonProcessingException
	 * @throws JsonMappingException
	 */
	public List<T> requestNextPage() 
			throws JsonMappingException, JsonProcessingException, IllegalStateException, URISyntaxException, 
			IOException {
		if (this.pagination == null) {
			throw new IllegalStateException("Pagination is not configured. Call .pagination() first.");
		}
		this.pagination.setup(this);
		Response response = request();
		this.pagination.setResponse(response, OM);
		return this.pagination.getObjects();
	}
	
	/**
	 * Clear internal state for paging.
	 */
	public void resetPage() {
		if (this.pagination == null) {
			throw new IllegalStateException("Pagination is not configured. Call .pagination() first.");
		}
		this.pagination.reset();
	}
	
	public List<T> requestAllPages() 
			throws JsonMappingException, JsonProcessingException, IllegalStateException, URISyntaxException,
			IOException {
		if (this.pagination == null) {
			throw new IllegalStateException("Pagination is not configured. Call .pagination() first.");
		}
		this.pagination.reset();
		List<T> result = new ArrayList<>();
		while (true) {
			this.pagination.setup(this);
			Response response = request();
			this.pagination.setResponse(response, OM);
			result.addAll(this.pagination.getObjects());
			if (!this.pagination.hasMore()) {
				break;
			}
		}
		return result;
	}
}
