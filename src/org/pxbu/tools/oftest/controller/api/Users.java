package org.pxbu.tools.oftest.controller.api;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class Users {
	private static Logger logger = Logger.getLogger(Users.class);
	private final String URL = "/api/users";
	
	private Map<String, HttpHandler> urlHandlers = new HashMap<String, HttpHandler>();
	
	private JSONArray users = new JSONArray();
	
	public Map<String, HttpHandler> getHandlers() {
		return urlHandlers;
	}
	
	
	private HttpHandler userListHandler = new HttpHandler() {
		public void handle(HttpExchange t) throws IOException {
	
			try {
				Map params = (Map) t.getAttribute("parameters");
				String method = t.getRequestMethod();
				String returnContent = "Unhandled request method: " + method;
				if (method.equals("PUT")) {
					StringWriter writer = new StringWriter();
					IOUtils.copy(t.getRequestBody(), writer);
					String theString = writer.toString();
					System.out.println(Thread.currentThread().getName() + ": Got the users: " + theString);
					users = new JSONArray(theString);
					returnContent = "Ok. Got the " + users.length() + " user names";
					stat.setUsers(users);
				} else if (method.equals("GET")) {
					if (users.length() == 0)
						logger.warn("Users have not been registered by means of starting Subscriber");
					
					returnContent = users.toString();
				}
				System.out.println("RETURNING: " + returnContent);
				t.sendResponseHeaders(200, returnContent.length());
				OutputStream os = t.getResponseBody();
				os.write(returnContent.getBytes());
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
	private Statistics stat;
	
	public Users(Statistics stat) {
		this.stat = stat;
		urlHandlers.put(URL, userListHandler);
	}
}
