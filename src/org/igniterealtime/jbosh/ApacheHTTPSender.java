package org.igniterealtime.jbosh;

/*
 * Copyright 2009 Guenther Niess
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

/**
 * Implementation of the {@code HTTPSender} interface which uses the
 * Apache HttpClient API to send messages to the connection manager.
 */
final class ApacheHTTPSender implements HTTPSender {
	
	private static PoolingHttpClientConnectionManager httpsCM;
	private static PoolingHttpClientConnectionManager httpCM;
	
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
		
		httpsCM = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		
		int MAX_CONNECTIONS = 400;
		
		
		// Increase max total connection to MAX_CONNECTIONS
		httpsCM.setMaxTotal(MAX_CONNECTIONS);
		// Increase default max connection per route to MAX_CONNECTIONS
		httpsCM.setDefaultMaxPerRoute(MAX_CONNECTIONS);
		
		httpCM = new PoolingHttpClientConnectionManager();
		httpCM.setMaxTotal(MAX_CONNECTIONS);;
		httpCM.setDefaultMaxPerRoute(MAX_CONNECTIONS);;

	}

    /**
     * Lock used for internal synchronization.
     */
    private final Lock lock = new ReentrantLock();

    /**
     * Session configuration.
     */
    private BOSHClientConfig cfg;

    /**
     * HttpClient instance to use to communicate.
     */
    private HttpClient httpClient;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors:

    /**
     * Prevent construction apart from our package.
     */
    ApacheHTTPSender() {
        // Load Apache HTTP client class
        HttpClient.class.getName();
    }

    ///////////////////////////////////////////////////////////////////////////
    // HTTPSender interface methods:

    /**
     * {@inheritDoc}
     */
    public void init(final BOSHClientConfig session) {
        lock.lock();
        try {
            cfg = session;
            httpClient = initHttpClient(session);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    public void destroy() {
        lock.lock();
        try {
            if (httpClient != null) {
                httpClient.getConnectionManager().shutdown();
            }
        } finally {
            cfg = null;
            httpClient = null;
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public HTTPResponse send(
            final CMSessionParams params,
            final AbstractBody body) {
        HttpClient mClient;
        BOSHClientConfig mCfg;
        lock.lock();
        try {
            if (httpClient == null) {
                httpClient = initHttpClient(cfg);
            }
            mClient = httpClient;
            mCfg = cfg;
        } finally {
            lock.unlock();
        }
        return new ApacheHTTPResponse(mClient, mCfg, params, body);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Package-private methods:

    @SuppressWarnings("deprecation")
    private static synchronized HttpClient initHttpClient(final BOSHClientConfig config) {
    	
    	/*
        // Create and initialize HTTP parameters
        org.apache.http.params.HttpParams params = new org.apache.http.params.BasicHttpParams();
        org.apache.http.conn.params.ConnManagerParams.setMaxTotalConnections(params, 100);
        org.apache.http.params.HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        org.apache.http.params.HttpProtocolParams.setUseExpectContinue(params, false);
        if (config != null &&
                config.getProxyHost() != null &&
                config.getProxyPort() != 0) {
            HttpHost proxy = new HttpHost(
                    config.getProxyHost(),
                    config.getProxyPort());
            params.setParameter(org.apache.http.conn.params.ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        // Create and initialize scheme registry 
        org.apache.http.conn.scheme.SchemeRegistry schemeRegistry = new org.apache.http.conn.scheme.SchemeRegistry();
        schemeRegistry.register(
                new org.apache.http.conn.scheme.Scheme("http", org.apache.http.conn.scheme.PlainSocketFactory.getSocketFactory(), 80));
            org.apache.http.conn.ssl.SSLSocketFactory sslFactory = org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory();
            sslFactory.setHostnameVerifier(org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            schemeRegistry.register(
                    new org.apache.http.conn.scheme.Scheme("https", sslFactory, 443));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        org.apache.http.conn.ClientConnectionManager cm = new org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager(params, schemeRegistry);
        return new org.apache.http.impl.client.DefaultHttpClient(cm, params);
        */
    	
		HttpClientBuilder builder = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy());
		
		boolean isSecure = config.getURI().toString().startsWith("https");
		
		if (isSecure) {
           builder = builder.setConnectionManager(httpsCM).
        		   setSSLHostnameVerifier(new NoopHostnameVerifier());
		} else {
			builder = builder.setConnectionManager(httpCM);
		}
		
		return builder.build();
    	
    }
}
