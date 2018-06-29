package org.pxbu.tools.oftest.util.xmpp;

import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smackx.pubsub.Item;
import org.jivesoftware.smackx.pubsub.ItemPublishEvent;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.Node;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.jivesoftware.smackx.pubsub.SimplePayload;
import org.jivesoftware.smackx.pubsub.listener.ItemEventListener;
import org.json.JSONObject;
import org.json.XML;


public class EventingService {
	
	private static Logger logger = Logger.getLogger(EventingService.class);
	private static final AtomicInteger totalLoggedInUsers = new AtomicInteger(0);
	private String user;
	private String password;
	private String protocol;
	private PubSubManager _pubsubman;
	private ICallback callback;
	public static final AtomicInteger failCount = new AtomicInteger(0);
	public static final AtomicInteger failedSubscriptions = new AtomicInteger(0);
	
	/*
	private static String[] SUBSCRIPTION_NODES = {"/finesse/api/User/%s", 
			"/finesse/api/User/%s/Dialogs",
	        "/finesse/api/User/%s/ClientLog",
	        "/finesse/api/User/%s/Queues",
	        "/finesse/api/SystemInfo",
	        "/finesse/api/User/%s/Media",
	       "/finesse/api/User/%s/Dialogs/Media"};
	*/
	
	private static String[] SUBSCRIPTION_NODES = {"/finesse/api/User/%s"};
	
	public static enum NODE_TYPE {USER, DIALOG, CLIENT_LOG, QUEUES, SYSTEM_INFO, MEDIA, DLG_MIDIA };
	
	private Map<NODE_TYPE, LeafNode> userNodes = new HashMap<>();
	
	private void doSubscriptions(AbstractXMPPConnection con) {
		
		int index=0;
		
		for (String nodePath: SUBSCRIPTION_NODES) {
			String nodename = new Formatter().format(nodePath, user).toString();
			try {
				Node subnode = _pubsubman.getNode(nodename);
				//System.out.println("Subscribed to node: " + nodename);
				subnode.addItemEventListener(new ItemEventListener() {
					@Override
					public void handlePublishedItems(ItemPublishEvent publishedEvent) {
						PayloadItem payload = (PayloadItem) publishedEvent.getItems().get(0);
						String xmlString = StringEscapeUtils.unescapeXml(payload.toXML());
						

						JSONObject json = XML.toJSONObject(xmlString);
						try {
						json = json.getJSONObject("item").getJSONObject("notification").getJSONObject("Update");
						logger.info("GOT MESSAGE: " + json.toString());
						callback.notifyMatch(json);
						} catch (Exception e) {
							throw new RuntimeException("Received JSON is: " + json.toString(), e);
						}

					}
				});
				
				userNodes.put(NODE_TYPE.values()[index++], ((LeafNode) subnode));
				
				// subnode.subscribe(con.getUser()); // Already subscribed within Finesse. 
				logger.info(user + " - listener setup success for" + nodename);
				this.callback.notifyListenerStatus(this.user, true);
			} catch (Exception e) {
				failedSubscriptions.incrementAndGet();
				logger.error(user + " - listener setup failed for " + nodename + ". " + ExceptionUtils.getStackTrace(e));
				this.callback.notifyListenerStatus(this.user, false);			}
		
		}
	}
	
	public static String writeStackTraceToString(StackTraceElement[] elements) {
		StringBuffer elementString = new StringBuffer();
		if (elements != null) {
			for (StackTraceElement el : elements) {
				elementString = elementString.append("\n" + el.toString());
			}
			return elementString.toString();
		}
		return "";
	}
	
	public void init(boolean subscribe) {
		try {
			AbstractXMPPConnection con = OpenfireClient.getXMPPConnection(user, password, protocol);
			_pubsubman = PubSubManager.getInstance(con);
			
			if (subscribe) {
				doSubscriptions(con);
				logger.info("User number: " + totalLoggedInUsers.incrementAndGet() + ". Listening for events " + user);
			}
			

		} catch (Exception e) {
			logger.error("Failed to connect user: " + user + " " + writeStackTraceToString(e.getStackTrace()));
			failCount.incrementAndGet();
			return;
		} 
	}
	
	public Node getUserNode(String user) throws Exception {
		String nodename = new Formatter().format(SUBSCRIPTION_NODES[0], user).toString();
		return _pubsubman.getNode(nodename);
	}
	
	public void sendEvent(Node userNode, String payload) {
		String escapedXml = StringEscapeUtils.escapeXml10(payload);
		Item item = new PayloadItem<SimplePayload>(null, new SimplePayload(null, null,"<notification>" + escapedXml + "</notification>"));
		try {
			((LeafNode) userNode).publish(item);
		} catch (NotConnectedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void sendEvent(NODE_TYPE nodeType, String payload) {
		try {
			AbstractXMPPConnection con = OpenfireClient.getXMPPConnection(user, password, "XMPP");
			//PubSubManager adminPub = new PubSubManager(con, "pubsub." + OpenfireClient.XMPP_DOMAIN);
			PubSubManager adminPub = PubSubManager.getInstance(con);
			
			LeafNode node = (LeafNode) adminPub.getNode(userNodes.get(nodeType).getId());
			
			con.disconnect();
			sendEvent(node, payload);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public void register(ICallback callback) {
		this.callback = callback;
	}
	
	public EventingService(String user, String password, String protocol) {
		this.user = user;
		this.password = password;
		this.protocol = protocol;
	}

}
