package org.pxbu.tools.oftest.util.httpservice;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Exposes HTTP service in the test's execution environment. The service can be
 * configured with specific response for specific requests.
 * 
 * 
 * @author <a href="mailto:praboo.p@gmail.com">Prabhu Periasamy</a>
 * @version <table border="1" cellpadding="3" cellspacing="0" width="95%">
 *          <tr bgcolor="#EEEEFF" id="TableSubHeadingColor">
 *          <td width="10%"><b>Date</b></td>
 *          <td width="10%"><b>Author</b></td>
 *          <td width="10%"><b>Version</b></td>
 *          <td width="*"><b>Change</b></td>
 *          </tr>
 *          <tr bgcolor="white" id="TableRowColor">
 *          <td>June 1, 2014</td>
 *          <td><a href="mailto:pperiasa@cisco.com">Prabhu Periasamy</a></td>
 *          <td align="right">1</td>
 *          <td>Creation</td>
 *          </tr>
 *          </table>
 */
public class HttpServiceUtil {
	
	static class AnyHostNameVerifier implements HostnameVerifier {

		@Override
		public boolean verify(String hostname, SSLSession session) {
			// Do not perform host name verification
			return true;
		}
	}
	
	
	public static void acceptHostsForSSLConnections() {
		HttpsURLConnection.setDefaultHostnameVerifier(new AnyHostNameVerifier());
	}
	

	/**
	 * Finds a free port by binding to 0 and getting the local port. 
	 * 
	 * @return
	 * @throws IOException
	 */
	public static int findFreePort() throws IOException {
		ServerSocket server = new ServerSocket(0);
		int port = server.getLocalPort();
		server.close();
		return port;
	}

	/**
	 * Returns a HTTP service that would bind on some port that is guaranteed to
	 * be unique Use HttpServer.getInetAddress to find the port it is running.
	 * 
	 * @param requestHandlers
	 *            The request handlers that service would use. see
	 *            {@link RequestHandler} for details
	 * @return HttpServer providing the service
	 * @throws IOException
	 */
	public static HttpServer getHttpService(Map<String, HttpHandler> requestHandlers) throws IOException {
		return getHttpService(requestHandlers, findFreePort());
	}
	
	/**
	 * Returns a HTTP service that would bind on the port passed
	 * 
	 * @param requestHandlers
	 *            The request handlers that service would use. see
	 *            {@link RequestHandler} for details
	 * @port The port where the service should run
	 * @return HttpServer providing the service
	 */
	public static HttpServer getHttpService(Map<String, HttpHandler> requestHandlers, int port) {
		HttpServer server = null;
		try {

			String hostAddress = Inet4Address.getLocalHost().getHostAddress();
			server = HttpServer.create(new InetSocketAddress(hostAddress, port), 0);

			for (String context : requestHandlers.keySet()) {
				String leadingSlash=(context.startsWith("/") ? "" : "/");
				server.createContext(leadingSlash + context, requestHandlers.get(context)).getFilters().add(new ParameterFilter());
			}


			server.setExecutor(Executors.newWorkStealingPool()); // creates a default executor
			server.start();
			//System.out.println("HttpServer in " + hostAddress + " is listening on port " + port);

		} catch (IOException e) {
			e.printStackTrace();
		} 
		return server;
	}

	/**
	 * Default handler that would service HttpRequest
	 */
	public static class RequestHandler implements HttpHandler {
		private String contentType;
		private String returnContent;
		private Map<String, String> headers;

		/**
		 * 
		 * @param fileOrData
		 *            - Pass file name with relative path to classes folder or
		 *            actual content
		 * @param contentType
		 *            - Content type
		 * @param headers
		 *            - The headers that need to be set
		 */
		public RequestHandler(String fileOrData, String contentType, Map<String, String> headers) {
			setContent(fileOrData);
			this.contentType = contentType;
			this.headers = headers;
		}

		/**
		 * Specify the content to be returned for the request. The content can refer to actual file or a string. If it is a file
		 * then, content of that file would be returned.
		 * 
		 * @param content
		 */
		public void setContent(String fileOrData) {
			URL file = getClass().getClassLoader().getResource(fileOrData);
			if (file != null)
				returnContent = FileUtil.loadFile(file.getFile());
			else
				returnContent = fileOrData;
		}

		/**
		 * Handler implementation
		 */
		public void handle(HttpExchange t) throws IOException {
			if (headers != null) {
				for (String key : headers.keySet())
					t.getResponseHeaders().add(key, headers.get(key));
			}

			t.getResponseHeaders().add("Content-type", contentType);
			t.sendResponseHeaders(200, returnContent.length());
			OutputStream os = t.getResponseBody();
			os.write(returnContent.getBytes());
			os.close();
		}
	}
}