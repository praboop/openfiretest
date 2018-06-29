package org.pxbu.tools.oftest;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.pxbu.tools.oftest.publisher.EventHolder;
import org.pxbu.tools.oftest.publisher.ScenarioPlayer;
import org.pxbu.tools.oftest.util.httpclient.HttpBulkData;
import org.pxbu.tools.oftest.util.httpservice.HttpClient;
import org.pxbu.tools.oftest.util.xmpp.EventingService;
import org.jivesoftware.smackx.pubsub.Node;

public class PublisherMain {

	private static Logger logger = Logger.getLogger(PublisherMain.class);

	// Password for xmpprootowner
	public static String PUBLISHER_PWD = "nt1cgR=xCD17hjj"; // 4.2.3
//	public final static String publish_user_pwd = "mz3Fxdz85A(tdzA"; // 4.0.3
	
	private static String MISSION_CONTROL_WEB_SERVICE;
	/**
	 * This service is started by Running {@link com.cisco.ccbu.finesse.event.test.web.MissionControl}
	 */
	static {
		try {
			MISSION_CONTROL_WEB_SERVICE = Inet4Address.getLocalHost().getHostAddress();
			MISSION_CONTROL_WEB_SERVICE = System.getProperty("mission.web.host", MISSION_CONTROL_WEB_SERVICE);
		} catch (Exception e) {
			MISSION_CONTROL_WEB_SERVICE = "localhost";
		}
	}
	
	/**
	 * Publish event at this frequency for EACH user.
	 */
	public static long MESSAGE_INTERVAL_MILLIS=1000;
	private static String PRESENCE_LISTENER_PWD;
	

	public List<String> getUsersInSystem() throws Exception {
		HttpClient httpClient = new HttpClient("http://" + MISSION_CONTROL_WEB_SERVICE + ":8085/", "", "");
		String response = httpClient.sendGet("api/users");
//		httpClient.close();
		JSONArray users = new JSONArray(response);
		
		List<String> arrayList = new ArrayList<>();
		for (int i=0; i<users.length(); i++)
			arrayList.add(users.getString(i));
		
		logger.info("To send events for : " + arrayList.size() + " users ");
		
		return arrayList;
	}
	
	public PublisherMain() {

	}
	
	public void startTest() throws Exception {
//		List<String> usersToTest = getUsersInSystem(Integer.parseInt(TOTAL_AGENTS));
		List<String> usersToTest = getUsersInSystem();
		ScheduledThreadPoolExecutor thpool = new ScheduledThreadPoolExecutor(usersToTest.size());
		CountDownLatch latch = new CountDownLatch(usersToTest.size());
		HttpBulkData<EventHolder> bulkData = new HttpBulkData();
		bulkData.start("http://" + MISSION_CONTROL_WEB_SERVICE + ":8085/");
		
		EventingService service = new EventingService("xmpprootowner", PUBLISHER_PWD, "XMPP");
		service.init(false);
		
		for (int i = 0; i < usersToTest.size(); i++) {
			String user = usersToTest.get(i);
			Node userNode = service.getUserNode(user);
			thpool.schedule(new ScenarioPlayer(service, bulkData, latch, usersToTest.get(i), userNode), 0, TimeUnit.SECONDS);
		}
		try {
			latch.await();
			thpool.shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	public static void main(String[] args) throws Exception {
		
		HttpClient httpClient = new HttpClient("http://" + MISSION_CONTROL_WEB_SERVICE + ":8085/", "", "");
		
		JSONObject openFireConfig = new JSONObject(httpClient.sendGet("ofconfig"));
		
		PRESENCE_LISTENER_PWD =  openFireConfig.getString("presencePassword");
		PUBLISHER_PWD = openFireConfig.getString("publishPassword");

		System.setProperty("openfire.host", openFireConfig.getString("xmppHost"));
		System.setProperty("xmpp.domain", openFireConfig.getString("xmppDomain"));
		MESSAGE_INTERVAL_MILLIS = 1000 / openFireConfig.getInt("publishRate");
		
		PublisherMain publisher = new PublisherMain();
		publisher.startTest();
	}
}
