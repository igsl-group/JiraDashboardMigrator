package com.igsl;

import java.io.IOException;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.igsl.config.Config;

public class ClientWrapper implements AutoCloseable {

	private static final JacksonJsonProvider JACKSON_JSON_PROVIDER = new JacksonJsonProvider()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(SerializationFeature.INDENT_OUTPUT, true);

	private Client client;
	
	public ClientWrapper(boolean cloud, Config config) throws IOException {
		client = ClientBuilder.newClient();
		client.register(JACKSON_JSON_PROVIDER);
		String userName = null;
		String password = null;
		if (cloud) {
			userName = config.getTargetUser();
			if (userName == null || userName.isEmpty()) {
				userName = Console.readLine("Cloud administrator email: ");
				config.setTargetUser(userName);
			}
			password = config.getTargetAPIToken();
			if (password == null || password.isEmpty()) {
				password = new String(Console.readPassword("API token for " + userName + ": "));
				config.setTargetAPIToken(password);
			}
		} else {
			userName = config.getSourceUser();
			if (userName == null || userName.isEmpty()) {
				userName = Console.readLine("DataCenter administrator username: ");
				config.setSourceUser(userName);
			}
			password = config.getSourcePassword();
			if (password == null || password.isEmpty()) {
				password = new String(Console.readPassword("Password for " + userName + ": "));
				config.setSourcePassword(password);
			}
		}
		client.register(HttpAuthenticationFeature.basic(userName, password));
	}
	
	public Client getClient() {
		return client;
	}
	
	@Override
	public void close() throws Exception {
		if (client != null) {
			client.close();
		}
	}
}
