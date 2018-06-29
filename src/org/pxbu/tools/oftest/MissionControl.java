package org.pxbu.tools.oftest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.pxbu.tools.oftest.controller.api.OpenfireConfig;
import org.pxbu.tools.oftest.controller.api.Statistics;
import org.pxbu.tools.oftest.controller.api.Users;
import org.pxbu.tools.oftest.controller.report.WebReport;
import org.pxbu.tools.oftest.util.httpservice.HttpServiceUtil;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Launches Mission Control testing Openfire.
 * 
 * @author Prabhu Periasamy
 *
 */
public class MissionControl {

	private HttpServer httpServer;
	CountDownLatch latch = new CountDownLatch(1);

	public void startService() throws IOException {

		Map<String, HttpHandler> urlHandlers = new HashMap<String, HttpHandler>();
		Statistics statistics = new Statistics();
		WebReport webReport = new WebReport();
		OpenfireConfig config = new OpenfireConfig();
		
		urlHandlers.put(statistics.getStatsUrl(), statistics.getStatisticsHandler());
		urlHandlers.putAll(webReport.getHandlers());
		urlHandlers.putAll(new Users(statistics).getHandlers());
		urlHandlers.put(config.getConfigUrl(), config.getConfigHandler());
		
		// Register the handler and start the service
		int httpPort = 8085; // httpServer.getAddress().getPort();
		httpServer = HttpServiceUtil.getHttpService(urlHandlers, httpPort);
		String hostName = httpServer.getAddress().getHostName();
		String secondServiceUrl = "http://" + hostName + ":" + httpPort;
		System.out.println("Http service is listening in " + secondServiceUrl);
		for (String url: urlHandlers.keySet()) {
			if (!url.contains("third-party"))
				System.out.println("service: " + secondServiceUrl + url);
		}
		try {
			latch.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void stopService() {

		try {
			if (httpServer == null)
				return;
			httpServer.stop(0);
		} catch (Exception ignore) {
		}
	}

	public static void main(String[] args) throws IOException {

		MissionControl d = new MissionControl();
		d.startService();
		d.stopService();
	}
}
