package org.pxbu.tools.oftest;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.SmackException.NotLoggedInException;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.disco.packet.DiscoverItems.Item;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.Node;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.jivesoftware.smackx.pubsub.Subscription;
import org.pxbu.tools.rtr.http.Client;
import org.pxbu.tools.rtr.http.Constants.State;
import org.pxbu.tools.rtr.xmpp.EventingService;
import org.pxbu.tools.rtr.xmpp.OpenfireClient;
import org.pxbu.tools.rtr.http.User;
import org.pxbu.tools.rtr.model.UserModel;
/**
 * Does a BOSH login of about 'n' number of users in 'x' seconds. Those n and x values are configurable via VM options.
 * @author Prabhu Periasamy
 *
 */
public class StartFineseLoad {

	final static Logger logger = Logger.getLogger(StartFineseLoad.class);

	private static Integer START_AGENT_INDEX = Integer.decode(System.getProperty("start.agent.index", "0"));
	private static Integer END_AGENT_INDEX = Integer.decode(System.getProperty("end.agent.index", "1"));
	private static Integer AGENT_LOGINS_PER_SECOND = Integer.decode(System.getProperty("login.per.second", "5"));
	private static String AGENT_USER_PASSWORD = System.getProperty("agent.pwd", "cisco");
	private static String TOTAL_AGENTS= System.getProperty("total.agents","300");
	private static String API_BASE = "https://finesse25.autobot.cvp:8445/finesse/api/";


	private static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
	
	static class Agent implements Runnable {

		private Client api;
		private User userApi;
		private EventingService event;
		private int extension;

		public Agent(CountDownLatch latch, String user, String password, int extension, String protocol) throws Exception {
			this.event = new EventingService(user, password, protocol);
			this.event.init();
			this.api = new Client(API_BASE, user, password);
			this.userApi = new User(this.api, this.event);		
			this.extension = extension;
		}

		public void run() {
			try {

				State currentState = userApi.getState();
				logger.info("The current user state is: " + currentState);
				
				if (currentState == State.LOGOUT) {
					userApi.login(this.extension);
					userApi.expectEvent();
					logger.info("<---- LOGGED IN USER " + api.getUserName() + " EXTENSION " + userApi.getExtension() + "--->: State is " + userApi.getState());
				} else {
					logger.info("<---- USER IS LOGGED IN " + api.getUserName() + " EXTENSION " + userApi.getExtension() + "--->: State is " + currentState);
				}
				
				State nextState;
				
				int max = 20;
				
				for (int i=0; i<max; i++) {
					
					nextState = (userApi.getLastKnownState() == State.READY) ? State.NOT_READY : State.READY;
					
					long startTime = System.currentTimeMillis();
					userApi.setState(nextState, nextState);
					userApi.expectEvent();
					long totalTime = System.currentTimeMillis() - startTime;
					logger.info(this.api.getUserName() + " State is: " + userApi.getLastKnownState() 
						+ " attempt " + (i+1) + "/" + max + ". Took " + totalTime + " milliseconds.");
				}
			
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	static class MessagingAgent implements Runnable {

		private UserModel agent;
		private EventingService event;

		public MessagingAgent(String user, String password, int extension, String protocol) throws Exception {
			this.event = new EventingService(user, password, protocol);
			this.event.init();
			this.agent = new UserModel(user, extension + "", State.NOT_READY, event);
		}

		public void run() {
			try {
/*
				agent.getState();
				for (int i=0; i<1; i++) {
					agent.setState(State.READY);
					agent.expectEvent();
					State currentState = agent.getState();
					logger.info(agent.getUserName() + " state is: " + currentState + " .In " + i + " request ");
					Thread.sleep(10000);
				}
				*/
				Thread.sleep(600000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	
	

	public static void loginAndSubscriptionTests(List<String> users, String protocol, int loginsPerSecond) throws Exception {
		ScheduledThreadPoolExecutor thpool = new ScheduledThreadPoolExecutor(2300);

		int batch_number = 0;
		CountDownLatch latch = new CountDownLatch(users.size());
		
		Integer extension = 1001001;

		for (int i = 0; i < users.size(); i++) {
			batch_number = (int) i / loginsPerSecond;
			thpool.schedule(new Agent(latch, users.get(i), AGENT_USER_PASSWORD, extension, protocol), batch_number, TimeUnit.SECONDS);
			extension++;
		}

		try {
			latch.await();
			logger.info(EventingService.failCount.get() + " out of " + users.size() 
				+ " failed to login. Total node subscriptions failed: " + EventingService.failedSubscriptions.get());
			thpool.shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void messagingTests(List<String> users, String protocol, int loginsPerSecond) throws Exception {
		ScheduledThreadPoolExecutor thpool = new ScheduledThreadPoolExecutor(2300);

		int batch_number = 0;
		CountDownLatch latch = new CountDownLatch(users.size());
		
		int extensions = 1001000;

		for (int i = 0; i < users.size(); i++) {
			batch_number = (int) i / loginsPerSecond;
			thpool.schedule(new MessagingAgent(users.get(i), AGENT_USER_PASSWORD, extensions++, protocol), batch_number, TimeUnit.SECONDS);
		}

		try {
			latch.await();
			logger.info(EventingService.failCount.get() + " out of " + users.size() 
				+ " failed to login. Total node subscriptions failed: " + EventingService.failedSubscriptions.get());
			thpool.shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	private static void boshLoginOne(String id, String pwd, String protocol) throws Exception{
		//AbstractXMPPConnection con1 = getXMPPConnection("1001080", "cisco", "XMPP");
		//AbstractXMPPConnection con1 = getXMPPConnection("admin", "evgtSFGuCH6YWr", "XMPP");
		AbstractXMPPConnection con1 = OpenfireClient.getXMPPConnection(id, pwd, protocol);
//		PubSubManager _pubsubman = new PubSubManager(con1, "pubsub." + con1.getServiceName());
		PubSubManager _pubsubman = PubSubManager.getInstance(con1);
		List<Subscription> subs = _pubsubman.getSubscriptions();
		DiscoverItems items = _pubsubman.discoverNodes(null);
		List<Item> itr = items.getItems();
		LeafNode node;
		for (Item item: itr) {
			node = (LeafNode) _pubsubman.getNode(item.getNode());
			logger.info("Item.getName()=" + item.getName() + ", Item.getNode()= " + item.getNode());
		}
		
		Node systemInfoNode = _pubsubman.getNode("/finesse/api/SystemInfo");
		logger.info("Got SystemInfo node");
		
		int waitPeriod = 100000;
		while (--waitPeriod > 0) {
			System.out.println(waitPeriod + " Sleeping");
			Thread.sleep(1000);
		}
		
		if (true) 
		{
			con1.disconnect();
			System.exit(0);
		}
	}
	

	private static void startAgentThing()
			throws Exception, NotLoggedInException, NotConnectedException, InterruptedException {
		
		List<String> userList = OpenfireClient.getUsersInSystem(Integer.parseInt(TOTAL_AGENTS));
		
		// loginAndSubscriptionTests(subList, "BOSH", AGENT_LOGINS_PER_SECOND);
		
		messagingTests(userList, "BOSH", AGENT_LOGINS_PER_SECOND);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		try {
			SmackConfiguration.DEBUG = true;

			SmackConfiguration.setDefaultPacketReplyTimeout(15000);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		//boshLoginOne("1001003", "cisco", "BOSH");
	
		startAgentThing();

	}

}
