package com.igsl.rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.igsl.Log;
import com.igsl.config.Config;

public class RestUtil<T> {
	
	// Generics
	private Class<T> dataClass;
	public Class<T> getDataClass() {
		return dataClass;
	}
	
	// Rate limit
	private static Object LOCK = new Object();
	private static long maxCall = 100;	// No. of calls per period
	private static long period = 1000;	// ms
	private static Date lastCheck = null;
	private static Float allowance = null;
	
	// Thread wait
	private long sleep = 1000;
	
	// Encoding
	private static final String ENCODDING = "ASCII";	
	private static final String DEFAULT_SCHEME = "https";
	private static final String DEFAULT_METHOD = HttpMethod.GET;
	
	private static final Logger LOGGER = LogManager.getLogger();

	// JSON
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
	
	// Retry
	private boolean allowRetry = false;
	private int maxRetryCount = -1;
	private boolean bitwiseRetryStatus = false;
	// Atlassian supposedly will return HTTP 429 for too many requests
	private List<Integer> retryStatusList = Arrays.asList(
			429	// Supposedly returned when rate limit exceeded
			);	
	// Retry when these exceptions occur
	private List<Class<? extends Throwable>> retryExceptionList = Arrays.asList(
				SocketException.class,
				SSLHandshakeException.class
			); 
	
	// Status
	private boolean bitwiseStatus = true;
	private List<Integer> statusList = Arrays.asList(Status.OK.getStatusCode());
	
	// Paging
	private Pagination<T> pagination;
	
	/**
	 * Create instance.
	 * @param <T> Data class, use Object if unknown.
	 */
	public static <T> RestUtil<T> getInstance(Class<T> dataClass) {
		return new RestUtil<T>(dataClass);
	}

	protected RestUtil(Class<T> dataClass) {
		this.dataClass = dataClass;
		this.pathTemplates = new HashMap<>();
		this.headers = new MultivaluedHashMap<>();
		this.query = new HashMap<>();
		this.pagination = new SinglePage<>(dataClass);
	}

	/**
	 * Configure using {@link Config} object.
	 * Includes:
	 * - scheme
	 * - host
	 * - authenticate
	 * - sleep
	 * @param config
	 * @throws UnsupportedEncodingException 
	 * @throws URISyntaxException 
	 */
	public RestUtil<T> config(Config config, boolean cloud) 
			throws UnsupportedEncodingException, URISyntaxException {
		if (cloud) {
			return this
					.sleep(config.getThreadWait())
					.scheme(config.getTargetScheme())
					.host(config.getTargetHost())
					.authenticate(config.getTargetUser(), config.getTargetAPIToken());
		} else {
			return this
					.sleep(config.getThreadWait())
					.scheme(config.getSourceScheme())
					.host(config.getSourceHost())
					.authenticate(config.getSourceUser(), config.getSourcePassword());
		}
	}
	
	/** 
	 * Set sleep 
	 */
	public RestUtil<T> sleep(long sleep) {
		this.sleep = sleep;
		return this;
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
	 * This also clears out query() cache.
	 * e.g. /rest/api/2/dosomething/{id}
	 */
	public RestUtil<T> path(String path) {
		this.path = path;
		return this.query();
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
	 * Clear query parameters.
	 * Calling .path() also clears query parameters.
	 * @return
	 */
	public RestUtil<T> query() {
		this.query.clear();
		return this;
	}
	
	/**
	 * Add a single query parameter.
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
	 * Control the max. rate for REST API calls. 
	 * @param maxCall Max. no. of calls per period.
	 * @param period Milliseconds in one period.
	 */
	public static void throttle(long maxCall, long period) {
		RestUtil.maxCall = maxCall;
		RestUtil.period = period;
	}
	
	/**
	 * Enable or disable retry and set max retry count.
	 * @param allowRetry Boolean.
	 * @param maxRetryCount -1 to have no limit.
	 */
	public RestUtil<T> retry(boolean allowRetry, int maxRetryCount) {
		this.allowRetry = allowRetry;
		this.maxRetryCount = maxRetryCount;
		return this;
	}
	
	/**
	 * Configure list of exceptions that can be retried.
	 * Default is SocketException.class and SSLHandshakeException.class, 
	 * which happens when network connection is overloaded.
	 * @param exceptions
	 */
	@SuppressWarnings("unchecked")
	public RestUtil<T> retryException(Class<? extends Exception>... exceptions) {
		this.retryExceptionList = new ArrayList<>();
		if (exceptions != null) {
			for (Class<? extends Exception> cls : exceptions) {
				this.retryExceptionList.add(cls);
			}
		}
		return this;
	}
	
	/**
	 * Set retry status to be bitwise or not.
	 * @param bitwiseRetryStatus
	 */
	public RestUtil<T> bitwiseRetryStatus(boolean bitwiseRetryStatus) {
		this.bitwiseRetryStatus = bitwiseRetryStatus;
		return this;
	}
	
	/**
	 * Configures status code that can be retried. 
	 * Default is 429 (Atlassian's too many calls response).
	 * @param statuses
	 */
	public RestUtil<T> retryStatus(int... statuses) {
		this.retryStatusList = new ArrayList<>();
		if (statuses != null) {
			for (int status : statuses) {
				this.retryStatusList.add(status);
			}
		}
		return this;
	}
	
	/**
	 * Configure status check to be bitwise or not.
	 * Default true.
	 * @param bitwiseStatus
	 */
	public RestUtil<T> bitwiseStatus(boolean bitwiseStatus) {
		this.bitwiseStatus = bitwiseStatus;
		return this;
	}
	
	/**
	 * Set valid status codes.
	 * Default is true, [Status.OK]
	 * Pass null as statuses to bypass status code check. Call .request() and check the response code.
	 * @param bitwiseAnd If false, status code must match exactly. Otherwise checked with bitwise AND.
	 * @param statuses Valid status codes.
	 */
	public RestUtil<T> status(int... statuses) {
		this.statusList = new ArrayList<>();
		if (statuses != null) {
			for (int status : statuses) {
				this.statusList.add(status);
			}
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
	
	private void rateCheck() {
		boolean requestGranted = false;
		while (!requestGranted) {
			synchronized(LOCK) {
				Date now = new Date();
				if (lastCheck == null) {
					lastCheck = now;
				}
				float timePassed = now.getTime() - lastCheck.getTime();
				lastCheck = now;
				if (allowance == null) {
					allowance = (float) maxCall;
				} else {
					allowance += timePassed / period * maxCall;
				}
				if (allowance >= maxCall) {
					allowance = (float) maxCall;
				}
				if (allowance >= 1) {
					requestGranted = true;
					allowance -= 1;
				}
				//Log.info(LOGGER, "allowance: " + allowance);
			}
			if (!requestGranted) {
				//Log.info(LOGGER, "Waiting for rate limit");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Log.error(LOGGER, "Rate sleep interrupted", e);
				}
			}
		}
	}
	
	/**
	 * Invoke REST API without pagination, validates status code and return the response. 
	 * @return Response.
	 * @throws IllegalStateException If status code does not match.
	 */
	public Response request() throws Exception {
		Response response = null;
		Client client = null;
		int retryCount = 0;
		boolean doRetry = false;
		do {
			doRetry = false;
			try {
				// Check rate of API calls
				//Log.info(LOGGER, "Rate check");
				rateCheck();
				// Get client from pool
				//Log.info(LOGGER, "ClientPool check");
				while (client == null) {
					client = ClientPool.get();
					if (client == null) {
						//Log.info(LOGGER, "Waiting for client pool");
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							Log.error(LOGGER, "Client pool sleep interrupted", e);
						}
					} else {
						//Log.info(LOGGER, "Client received");
					}
				}
				client.register(JACKSON_JSON_PROVIDER);
				String finalPath = this.path;
				for (Map.Entry<String, String> entry : pathTemplates.entrySet()) {
					finalPath = finalPath.replaceAll(
							Pattern.quote("{" + entry.getKey() + "}"), 
							entry.getValue());
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
				// Invoke
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
				int respStatus = response.getStatus();
				// Check if status means retry
				if (retryStatusList != null && retryStatusList.size() != 0) {
					for (int status : retryStatusList) {
						if ((bitwiseRetryStatus && status == respStatus) || 
							(!bitwiseRetryStatus && (status & respStatus) == status)) {
							if (maxRetryCount == -1 || 
								retryCount <= maxRetryCount) {
								doRetry = true;
								Log.warn(LOGGER, "Retrying due to status: " + respStatus);
								break;
							}
						} 
					}
				}
				// If not retry, check result
				if (!doRetry) {
					// Check status if statusList is provided
					if (statusList != null && statusList.size() != 0) {
						boolean statusValid = false;
						for (int status : statusList) {
							if (bitwiseStatus) {
								if ((respStatus & status) == status) {
									statusValid = true;
									break;
								}
							} else {
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
				}
			} catch (Exception ex) {
				Throwable t = ex;
				if (ex instanceof ProcessingException || 
					ex instanceof ResponseProcessingException) {
					if (ex.getCause() != null) {
						t = ex.getCause();
					}
				}
				Log.error(LOGGER, "Exception class: " + t.getClass(), t);
				if (retryExceptionList != null) {
					for (Class<? extends Throwable> cls : retryExceptionList) {
						if (cls.isAssignableFrom(t.getClass())) {
							if (maxRetryCount == -1 || 
								retryCount <= maxRetryCount) {
								doRetry = true;
								Log.info(LOGGER, "Retrying due to exception: " + t.getClass());
								break;
							} else {
								throw ex;
							}
						}
					}
					if (!doRetry) {
						// Exception is not in retryable list
						throw ex;
					}
				} else {
					// No retrying, throw exception
					throw ex;
				}
			} finally {
				ClientPool.release(client);
				client = null;
				//Log.info(LOGGER, "Client returned to pool");
			}
			if (doRetry) {
				retryCount++;
				// Put a slight delay before retrying
				try {
					Log.info(LOGGER, "Waiting before retry");
					Thread.sleep(1000);
				} catch (InterruptedException iex) {
					Log.error(LOGGER, "Retry sleep interrutped", iex);
				}
				// TODO Get delay from headers
				// Retry-After and X-RateLimit-Reset
			}
		} while (	allowRetry && 
					(maxRetryCount == -1 || retryCount <= maxRetryCount) && 
					doRetry); 
		return response;
	}
	
	/**
	 * Invoke REST API with pagination, validate status, and retrieve the next page of results.
	 * @return List<T>
	 */
	public List<T> requestNextPage() throws Exception, 
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
	
	public List<T> requestAllPages() throws Exception {
		if (this.pagination == null) {
			throw new IllegalStateException("Pagination is not configured. Call .pagination() first.");
		}
		this.pagination.reset();
		List<T> result = new ArrayList<>();
		while (true) {
			this.pagination.setup(this);
			Response response = request();
			this.pagination.setResponse(response, OM);
			List<T> list = this.pagination.getObjects();
			if (list != null) {
				result.addAll(list);
			}
			if (!this.pagination.hasMore()) {
				break;
			}
		}
		return result;
	}
}
