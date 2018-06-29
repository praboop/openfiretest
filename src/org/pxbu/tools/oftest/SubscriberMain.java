package org.pxbu.tools.oftest;

import java.net.Inet4Address;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.pxbu.tools.oftest.util.httpclient.HttpBulkData;
import org.pxbu.tools.oftest.util.httpclient.LoadHttpClient;
import org.pxbu.tools.oftest.util.xmpp.EventingService;
import org.pxbu.tools.oftest.util.xmpp.ICallback;
import org.pxbu.tools.oftest.util.xmpp.OpenfireClient;

public class SubscriberMain {
	
	final static Logger logger = Logger.getLogger(SubscriberMain.class);

	private static Integer AGENT_LOGINS_PER_SECOND = Integer.decode(System.getProperty("login.per.second", "100"));
	private static String AGENT_USER_PASSWORD = System.getProperty("agent.pwd", "cisco");
	private static String TOTAL_AGENTS= System.getProperty("total.agents","300");
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
	
	private static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
	
	class MessagingAgent implements Runnable {

		private EventingService event;
		private HttpBulkData<JSONObject> httpClient;
		private CountDownLatch latch;

		public MessagingAgent(HttpBulkData<JSONObject> bulkDispatcher, CountDownLatch latch, String user, String password, String protocol) throws Exception {
			this.event = new EventingService(user, password, protocol);
			this.latch = latch;
			this.httpClient = bulkDispatcher;
		}

		public void run() {
			try {
				
				this.event.register(new ICallback() {

					private JSONObject toRcvMsg(JSONObject event) {
						String requestId = event.getString("requestId");
						int userId = event.getJSONObject("data").getJSONObject("user").getInt("loginId");
						
						JSONObject jsonDetail = new JSONObject();
						jsonDetail.put("id", userId + "");
						jsonDetail.put("type", "ack");
						jsonDetail.put("event", requestId);
						
						return jsonDetail;
					}

					@Override
					public void notifyMatch(JSONObject event) {
						JSONObject trackMsg = toRcvMsg(event);
						
						try {
							httpClient.addMessage(trackMsg);;
							logger.debug("Acknowledging event " + event.toString());
							//System.out.println("Acknowledging event " + trackMsg.getString("event"));
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					
					@Override
					public void notifyListenerStatus(String userId, boolean status) {

						try {
							JSONObject listenerUpdate = new JSONObject();
							listenerUpdate.put("id", userId);
							listenerUpdate.put("type", "ListenerStatus");
							listenerUpdate.put("event", status);
							httpClient.addMessage(listenerUpdate);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					
				});
				
				this.event.init(true);
				
				latch.countDown();
				Thread.sleep(600000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	
	public void messagingTests(List<String> users, String protocol, int loginsPerSecond) throws Exception {
		ScheduledThreadPoolExecutor thpool = new ScheduledThreadPoolExecutor(2300);

		int batch_number = 0;
		CountDownLatch latch = new CountDownLatch(users.size());
		HttpBulkData<JSONObject> bulkData = new HttpBulkData<>();
		bulkData.start("http://" + MISSION_CONTROL_WEB_SERVICE + ":8085/");

		logger.info("Setting up event listener for " + users.size() + " users...");
		for (int i = 0; i < users.size(); i++) {
			batch_number = (int) i / loginsPerSecond;
			thpool.schedule(new MessagingAgent(bulkData, latch, users.get(i), AGENT_USER_PASSWORD, protocol), 
					batch_number, TimeUnit.SECONDS);
		}

		try {
			latch.await();
			if (EventingService.failCount.get() == 0) {
				logger.info("All " + users.size() + " users logged in.");
			} else {
				logger.info(EventingService.failCount.get() + " out of " + users.size() 
					+ " failed to login. Total node subscriptions failed: " + EventingService.failedSubscriptions.get());
			}
			thpool.awaitTermination(1, TimeUnit.HOURS);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public static void main(String[] args) throws Exception {
		
//		CertificateAcceptor.setupGullibleTrustManager();
//		HttpsURLConnection.setDefaultHostnameVerifier(new CertificateAcceptor.RelaxedHostNameVerifier());
		
		/*
		AbstractXMPPConnection conn = OpenfireClient.getXMPPConnection("1001001", "cisco", "BOSH");
		
		System.out.println("OK");
		*/
	
		
		
		String missionControlService = "http://" + MISSION_CONTROL_WEB_SERVICE + ":8085/";
		
		logger.info("Connecting to Mission Control " + missionControlService);
		
		LoadHttpClient httpClient = new LoadHttpClient(missionControlService, "", "");
		
		JSONObject openFireConfig = new JSONObject(httpClient.sendGet("ofconfig"));
		
		System.setProperty("presencelistener.pwd", openFireConfig.getString("presencePassword"));
		System.setProperty("openfire.host", openFireConfig.getString("xmppHost"));
		System.setProperty("xmpp.domain", openFireConfig.getString("xmppDomain"));
		TOTAL_AGENTS = openFireConfig.getString("totalConsumers");

		List<String> userList = OpenfireClient.getUsersInSystem(Integer.parseInt(TOTAL_AGENTS));
		
		
		JSONArray array = new JSONArray(userList);
		httpClient.sendPut("api/users", array.toString());
		

		String consumerType = openFireConfig.getString("listenerType").equals("BOSH") ? "BOSH" : "XMPP";
		
		new SubscriberMain().messagingTests(userList, consumerType, AGENT_LOGINS_PER_SECOND);
		
		
	}

}
