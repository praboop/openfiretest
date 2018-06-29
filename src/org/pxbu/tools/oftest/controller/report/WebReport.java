package org.pxbu.tools.oftest.controller.report;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FilenameUtils;
import org.pxbu.tools.oftest.util.httpservice.FileUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class WebReport {
	private final String SERVICE_URL = "/";
	private final String RESOURCE_URL = "/third-party";
	
	private final String WEB_RES_HOME = "org.pxbu.tools.oftest.controller.resources".replaceAll("\\.", "/") + "/";
	private final String mainPage = "index.html";
	private String mainPageContent;
	private Map<String, HttpHandler> urlHandlers = new HashMap<String, HttpHandler>();
	
	private static Map<String, String> KNOWN_TYPES = new HashMap<String, String>();
	static {
		KNOWN_TYPES.put("js", "application/javascript");
		KNOWN_TYPES.put("css", "text/css");
		KNOWN_TYPES.put("map", "application/json");
		KNOWN_TYPES.put("ico", "image/x-icon");
	}
	
	private HttpHandler homeHandler = new HttpHandler() {
		public void handle(HttpExchange t) throws IOException {

			try {
				String uri = t.getRequestURI().toString();
				System.out.println("HOME. Got the request for URI: " + uri);
				
				Resource resource;
				if (uri.equals("/")) {
					loadMainPage();
					resource = new Resource();
					resource.contentType = MediaType.TEXT_HTML;
					resource.content = mainPageContent;
				} else {
					resource = getResource(WEB_RES_HOME + uri);
				}
				
				sendResponse(t, resource);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
	
	private class Resource {
		String content;
		String contentType;
	}
	
	private Map<String, Resource> resourceCache = new LinkedHashMap<>();
	
	private Resource getResource(String uri) {
		Resource resource = resourceCache.get(uri);
		if (resource != null)
			return resource;
		
		URL file = getClass().getClassLoader().getResource(uri);
		String resourceContent = null;
		if (file != null)
			resourceContent = FileUtil.loadFile(file.getFile());
		
		if (resourceContent == null || resourceContent.isEmpty()) {
			System.err.println("Unable to load resource " + uri);
			System.exit(0);
		}
		resource = new Resource();
		resource.content = resourceContent;
		resource.contentType = KNOWN_TYPES.get(FilenameUtils.getExtension(uri));
		
		if (!uri.endsWith(".js") ) {
			resourceCache.put(uri, resource);
			System.out.println("Cached the resource: " + uri + ". Content Type: " + resource.contentType);
		}
		return resource;
	}
	
	private HttpHandler resourceHandler = new HttpHandler() {
		
		public void handle(HttpExchange t) throws IOException {

			try {
				String uri = t.getRequestURI().toString();
				sendResponse(t, getResource(WEB_RES_HOME + uri));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
	
	private void loadMainPage() {
		URL file = getClass().getClassLoader().getResource(WEB_RES_HOME + mainPage);
		if (file != null)
			mainPageContent = FileUtil.loadFile(file.getFile());
		
		if (mainPageContent == null || mainPageContent.isEmpty()) {
			System.err.println("Unable to load main page");
			System.exit(0);
		}
	}
	
	
	public Map<String, HttpHandler> getHandlers() {
		return urlHandlers;
	}
	
	private void sendResponse(HttpExchange t, Resource resource) throws UnsupportedEncodingException, IOException {

		t.getResponseHeaders().add("Content-Type", resource.contentType);
		byte[] bs = resource.content.getBytes("UTF-8");
		t.sendResponseHeaders(200, bs.length);
		OutputStream os = t.getResponseBody();
		os.write(bs);
		os.close();
	}


	public WebReport() {
		loadMainPage();
		
		urlHandlers.put(SERVICE_URL, homeHandler);
		
		urlHandlers.put(RESOURCE_URL, resourceHandler);
		
	}
	
}
