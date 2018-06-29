package org.pxbu.tools.oftest.controller.api;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class OpenfireConfig {
	
	static private final String CONFIG_URL = "/ofconfig";
	static final char PATH_SEP = File.separatorChar;
	static final String CONFIG_DIR = System.getProperty("user.home") + PATH_SEP + ".ofconfig" + PATH_SEP;	
	static final String CONFIG_FILE = "load_config.json";
	
	static {
		new File(CONFIG_DIR).mkdir();
	}
	
	private HttpHandler configHandler = new HttpHandler() {
		

		
		
		private String loadConfig() throws Exception {
			String returnMsg = "Unable to load config \n";
			try {
				File[] files = new File(CONFIG_DIR).listFiles();
				if (files.length == 0) {
					 return createEmptyJson();
				}
				
				returnMsg = FileUtils.readFileToString(new File(CONFIG_DIR + CONFIG_FILE));
			} catch (Exception e) {
				e.printStackTrace();
				returnMsg += e.getMessage();
				throw new Exception(returnMsg, e);
			}
			return returnMsg;
		}
		
		private String saveConfig(JSONObject config) throws Exception {
			String returnMsg="Unable to save file. Config is " + config.toString();
			try {
				FileUtils.writeStringToFile(new File(CONFIG_DIR + CONFIG_FILE), config.toString());
				returnMsg = "Saved to file " + CONFIG_FILE;
			} catch (Exception e) {
				e.printStackTrace();
				returnMsg += e.getMessage();
				throw new Exception(returnMsg, e);
			}
			return returnMsg;
		}
		
		private String toJson(String xmppHost, String xmppDomain, String publishPassword, String presencePassword, String listenerType, String publishRate, String totalConsumers) throws Exception {
			JSONObject returnObject = new JSONObject();
			returnObject.put("xmppHost", xmppHost);
			returnObject.put("xmppDomain", xmppDomain);
			returnObject.put("publishUser", "xmpprootowner");
			returnObject.put("publishPassword", publishPassword);
			returnObject.put("presenceUser", "presencelistener");
			returnObject.put("presencePassword", presencePassword);
			returnObject.put("listenerType", listenerType);
			returnObject.put("publishRate", publishRate);
			returnObject.put("totalConsumers", totalConsumers);
			
			return returnObject.toString();
		}
		
		private String createEmptyJson() throws Exception {
			return toJson("", "", "", "", "", "" , "");
		}

		public void handle(HttpExchange t) throws IOException {

			try {
				Map params = (Map) t.getAttribute("parameters");
				String method = t.getRequestMethod();
				String returnContent = "Unhandled request method: " + method;
				if (method.equals("PUT")) {
					StringWriter writer = new StringWriter();
					IOUtils.copy(t.getRequestBody(), writer);
					
					returnContent = saveConfig(new JSONObject(writer.toString()));
					t.getResponseHeaders().add("Content-Type", MediaType.TEXT_PLAIN);
				} 
				else if (method.equals("GET")) {
					t.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON);
					returnContent = loadConfig();
				}
				System.out.println("RETURNING: " + returnContent);
				t.sendResponseHeaders(200, returnContent.length());
				OutputStream os = t.getResponseBody();
				os.write(returnContent.getBytes());
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
				t.sendResponseHeaders(500, e.getMessage().length());
				OutputStream os = t.getResponseBody();
				os.write(e.getMessage().getBytes());
				os.close();
			}
		}
	};
	
	public HttpHandler getConfigHandler() {

		return configHandler;
	}

	public String getConfigUrl() {

		return CONFIG_URL;
	}
	
}
