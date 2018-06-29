package org.pxbu.tools.rtr.http;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;


public class Client {
	Logger logger = Logger.getLogger(Client.class);
	
	private static PoolingHttpClientConnectionManager cm;
	
	static {
		SSLContext sslcontext;
		SSLConnectionSocketFactory factory = null;
		try {
			sslcontext = SSLContexts.custom().loadTrustMaterial(new TrustSelfSignedStrategy()).build();
	        factory = new SSLConnectionSocketFactory(sslcontext, new NoopHostnameVerifier());
		} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(0);
		}

        
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create().register("https", factory).build();
		
		cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		// Increase max total connection to 200
		cm.setMaxTotal(200);
		// Increase default max connection per route to 20
		cm.setDefaultMaxPerRoute(200);
		
		/*
		// Increase max connections to 50
		HttpHost localhost = new HttpHost("finesse25.autobot.cvp", 8445);
		cm.setMaxPerRoute(new HttpRoute(localhost), 50);
		*/
		

	}
	
	private CloseableHttpClient httpClient;
	private HttpClientContext context;
	private String username;
	private String urlBase;
	private String password;
	
	
	public void close() {
		try {
			httpClient.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public Client(String urlBase, String username, String password)  throws Exception {

		this.urlBase = urlBase;
		
		this.username = username;
		this.password = password;
		
		this.context = HttpClientContext.create();
		CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY, 
		    new UsernamePasswordCredentials(username, password));
		
		this.httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultCredentialsProvider(credentialsProvider)
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();
	}
	
	public String getUserName() {
		return this.username;
	}
	
	public String getPassword() {
		return this.password;
	}

	// HTTP GET request
	public String sendGet(String url) throws IOException {
			HttpGet httpget = new HttpGet(urlBase + url);

			logger.debug("Executing GET request for " + username + " : " + httpget.getRequestLine());
            
            CloseableHttpResponse response = httpClient.execute(httpget, context);
            try {
            	logger.debug(response.getStatusLine());

                // Get hold of the response entity
                HttpEntity entity = response.getEntity();
                
                // If the response does not enclose an entity, there is no need
                // to bother about connection release
                if (entity != null) {
                	String strResponse = EntityUtils.toString(entity);
                	EntityUtils.consume(entity);
                	return strResponse;
                }
            } finally {
                response.close();
            }
            return "";
	}
	
	//HTTP POST request
	public String sendPOST(String url, Map<String, String> formData) throws Exception {
		HttpPost httpPost = new HttpPost(url);
		
        logger.debug("Executing POST request for " + username + " : " + httpPost.getRequestLine());
        
        MultipartEntityBuilder entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        
        ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String> entry: formData.entrySet()) {
        	entity.addTextBody(entry.getKey(), entry.getValue());
        	postParameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        
        HttpEntity httpEntity = entity.build();
        
        //httpPost.setEntity(httpEntity);
        httpPost.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));

        
        CloseableHttpResponse response = httpClient.execute(httpPost, context);
        try {
        	logger.debug(response.getStatusLine());

            // Get hold of the response entity
            HttpEntity respEntity = response.getEntity();
            
            // If the response does not enclose an entity, there is no need
            // to bother about connection release
            if (respEntity != null) {
            	String strResponse = EntityUtils.toString(respEntity);
            	EntityUtils.consume(respEntity);
            	return strResponse;
            }
        } finally {
            response.close();
        }
        return "";
	}
	
	
	// HTTP PUT request
	public String sendPut(String url, String content) throws Exception {
		HttpPut httpPost = new HttpPut(urlBase + url);
		
        logger.debug("Executing PUT request for " + username + " : " + httpPost.getRequestLine());
        
        StringEntity entity = new StringEntity(content);
        
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-Type", "application/xml");
        httpPost.setHeader("charset", "utf-8");

        
        CloseableHttpResponse response = httpClient.execute(httpPost, context);
        try {
        	logger.debug(response.getStatusLine());

            // Get hold of the response entity
            HttpEntity respEntity = response.getEntity();
            
            // If the response does not enclose an entity, there is no need
            // to bother about connection release
            if (entity != null) {
            	String strResponse = EntityUtils.toString(respEntity);
            	EntityUtils.consume(entity);
            	return strResponse;
            }
        } finally {
            response.close();
        }
        return "";
        
		
		/*

		byte[] postData       = urlParams.getBytes( StandardCharsets.UTF_8 );
		int    postDataLength = postData.length;
		
		URL obj = new URL(url);
		HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
		conn.setDoOutput( true );
		conn.setInstanceFollowRedirects( false );
		conn.setRequestMethod( "POST" );
		conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded"); 
		conn.setRequestProperty( "charset", "utf-8");
		conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
		conn.setUseCaches( false );
		try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) {
		   wr.write( postData );
		}
		
        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder returnStr = new StringBuilder();
        for ( int c = in.read(); c != -1; c = in.read() )
           returnStr.append((char)c);
		
		return returnStr.toString();
		*/
	}
	
	public static void main(String args[]) throws Exception {
		
		String userId = "1002996";

		Client c = new Client("https://finesse25.autobot.cvp:8445/finesse/api/", userId, "cisco");
		System.out.println("----- SYSTEM INFO ------\n" + c.sendGet("SystemInfo"));
		
		System.out.println("----- GET USER STATE ------\n" + c.sendGet("User/" + userId));
		
//		System.out.println("----- SET USER STATE ------\n" + c.sendPut("User/" + userId, "<User><state>READY</state></User>"));
		
		c.close();
	}
}
