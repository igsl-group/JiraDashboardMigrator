package com.igsl.rest;

import java.util.concurrent.LinkedBlockingQueue;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import com.igsl.Log;

/**
 * A resource pool for javax.ws.rs.Client
 * @author IGS
 *
 */
public class ClientPool  {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int DEFAULT_MAX_SIZE = 10;
	private static LinkedBlockingQueue<Client> pool = new LinkedBlockingQueue<>(DEFAULT_MAX_SIZE);
	
	public static void setMaxPoolSize(int maxSize, int connectTimeout, int readTimeout) {
		ClientConfig configuration = new ClientConfig()
				.property(ClientProperties.CONNECT_TIMEOUT, connectTimeout)
				.property(ClientProperties.READ_TIMEOUT, readTimeout); 
		if (pool.isEmpty()) {
			pool = new LinkedBlockingQueue<Client>(maxSize);
			for (int i = 0; i < maxSize; i++) {
				Client c = ClientBuilder.newClient(configuration);
				pool.add(c);
			}
			Log.info(LOGGER, "ClientPool initialized, size: " + pool.size());
		} else {
			throw new IllegalStateException("Pool already in use, cannot change max pool size");
		}
	}
	
	public static void close() {
		Client c = null;
		do {
			c = pool.poll();
			if (c != null) {
				c.close();
			}
		} while (c != null);
	}
	
	public static Client get() {
		Client c = null;
		try {
			c = pool.take();
			//Log.info(LOGGER, "ClientPool.get() = " + c + ", size: " + pool.size());
		} catch (InterruptedException iex) {
			Log.error(LOGGER, "ClientPool take interrupted", iex);
		}
		return c;
	}
	
	public static void release(Client c) {
		try {
			pool.put(c);
			//Log.info(LOGGER, "ClientPool.release(), size: " + pool.size());
		} catch (InterruptedException iex) {
			Log.error(LOGGER, "ClientPool put interrupted", iex);
		}
	}
}
