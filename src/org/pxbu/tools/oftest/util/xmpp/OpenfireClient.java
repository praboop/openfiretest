package org.pxbu.tools.oftest.util.xmpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.SSLContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.bosh.BOSHConfiguration;
import org.jivesoftware.smack.bosh.XMPPBOSHConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.parts.Resourcepart;

public class OpenfireClient {
	
	private static Logger logger = Logger.getLogger(OpenfireClient.class);
	private static String PRESENCE_USER = System.getProperty("presencelistener", "presencelistener");
	private static String PRESENCE_LISTENER_PWD = System.getProperty("presencelistener.pwd","bH8m)SGf67yxxgb"); // Find this in /opt/cisco/desktop/openfire/embedded_db/openfire.script
	private static String XMPP_HOST = System.getProperty("openfire.host", "finesse25.autobot.cvp");
	public static String XMPP_DOMAIN = System.getProperty("xmpp.domain", "finesse25.autobot.cvp");
	private static Integer HTTP_BIND_PORT = Integer.decode(System.getProperty("http.bind.port", "7443"));// 7443 for HATTPS or 7071 for HTTP
	private static DummySSLSocketFactory sslFactory = new DummySSLSocketFactory();
	static {
		try {
			SmackConfiguration.DEBUG = false;

			SmackConfiguration.setDefaultReplyTimeout(15000);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	/**
	 * Get's the XMPPConnection to XMPP server.
	 * 
	 * @param username
	 * @param password
	 * @param connType
	 *            - can be XMPP or BOSH
	 * @return XMPPConnection object
	 * @throws Exception
	 */
	public static AbstractXMPPConnection getXMPPConnection(String username, String password, String connType) throws Exception {

		AbstractXMPPConnection XMPPConnection = null;
		if (connType.equals("XMPP")) {
			XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder()
					.setXmppDomain(XMPP_DOMAIN)
				    .setHost(XMPP_HOST)
				    .setPort(5223)
				    .setUsernameAndPassword(username, password);
			
			config.setSecurityMode(SecurityMode.ifpossible);
			
			config.setCustomSSLContext(SSLContext.getInstance("TLS"));
			config.setEnabledSSLProtocols(new String[] {"TLSv1.2"});
				    
				    
//			config.setSecurityMode(SecurityMode.disabled);
			config.setSocketFactory(sslFactory);

			XMPPConnection = new XMPPTCPConnection(config.build());
		} else if (connType.equals("BOSH")) {
			boolean isHttps = HTTP_BIND_PORT == 7443 ? true : false;
			
			BOSHConfiguration.Builder config = BOSHConfiguration.builder()
					.setXmppDomain(XMPP_DOMAIN)
				    .setHost(XMPP_HOST)
				    .setFile("/http-bind/")
				    .setPort(HTTP_BIND_PORT)
				    .setUsernameAndPassword(username, password)
				    .setUseHttps(isHttps).setDebuggerEnabled(false);
	

			config.setSecurityMode(SecurityMode.ifpossible);
			
			config.setCustomSSLContext(SSLContext.getInstance("TLS"));
			config.setEnabledSSLProtocols(new String[] {"TLSv1.2"});

			XMPPConnection = new XMPPBOSHConnection(config.build());
		} else {
			validateXMPPConnectionType(connType);
		}

		try {
			Logger.getLogger("org.jivesoftware.smack").setLevel(Level.INFO);
			XMPPConnection.connect();
		} catch (XMPPException e) {
			throw new Exception(
					"Error connecting to Openfire using " + connType + " protocol. Check XMPPConnection properties", e);
		}
		if (!XMPPConnection.isConnected()) {
			throw new Exception(
					"Error connecting to Openfire using " + connType + " protocol. Check XMPPConnection properties");
		}
		try {
			XMPPConnection.login(username, password, Resourcepart.from("Resource-" + new Random().nextInt(999999)));
			if (XMPPConnection.isAuthenticated() == false) {
                logger.error("Disconnecting " + username + " since not authenticated");
				try {XMPPConnection.disconnect();}catch (Exception e) {}
                throw new Exception ("Connected xmpp agent " + username + " but not authenticated - disconnected");
            }
		} catch (XMPPException e) {
			throw new Exception(
					"Failed to login as user " + username + " and password " + password + ". Double check the properties.", e);
		}

		return XMPPConnection;
	}

	private static void validateXMPPConnectionType(String XMPPConnection_TYPE) {
		if (!XMPPConnection_TYPE.equals("BOSH") && !XMPPConnection_TYPE.equals("XMPP")) {
			logger.error("Unknown value passed to XMPPConnection.type");
			System.exit(-1);
		}
	}


	/**
	 * Validates XMPPConnection can be made via BOSH and XMPP.
	 */
	public static Map<String, Boolean> validateXMPPConnection() {

		final Map<String, Boolean> status = new HashMap<String, Boolean>();
		status.put("BOSH_STATUS", false);
		status.put("XMPP_STATUS", false);

		final List<Exception> XMPPConnectionError = new ArrayList<Exception>();
		Thread validator = new Thread("XMPP XMPPConnection validator") {
			public void run() {
				AbstractXMPPConnection c;
				Presence offlinePres = new Presence(Presence.Type.unavailable, "Lunch", 1, Presence.Mode.away);
				try {
					logger.info("Validating XMPP XMPPConnection... Presence User: " + PRESENCE_USER + " Password: " + PRESENCE_LISTENER_PWD);
					c = getXMPPConnection(PRESENCE_USER, PRESENCE_LISTENER_PWD, "XMPP");
					c.disconnect(offlinePres);
					logger.info("Can connect using XMPP");
					status.put("XMPP_STATUS", true);
				} catch (Exception e) {
					logger.error("Unable to connect using XMPP");
					XMPPConnectionError.add(e);
				}

				try {
					logger.info("Validating BOSH XMPPConnection... using same credentials");
					c = getXMPPConnection(PRESENCE_USER, PRESENCE_LISTENER_PWD, "BOSH");
					c.disconnect(offlinePres);
					logger.info("Can connect using BOSH");
					status.put("BOSH_STATUS", true);
				} catch (Exception e) {
					e.printStackTrace();
					logger.error("Unable to connect using BOSH - " + e.getMessage());
					XMPPConnectionError.add(e);
				}

			}
		};

		try {
			validator.start();
			validator.join();
			if (!XMPPConnectionError.isEmpty()) {
				if (validator.isAlive())
					validator.interrupt();
				logger.error("XMPP XMPPConnection configuration appears to be incorrect");
			}
		} catch (InterruptedException e) {
			logger.error("XMPP XMPPConnection configuration appears to be incorrect");
		}

		return status;
	}
	
	public static List<String> getUsersInSystem(int totalTestUsers) throws Exception {
		
		/*
		Map<String, Boolean> XMPPConnectionStatus = validateXMPPConnection();
		for (String key : XMPPConnectionStatus.keySet()) {
			if (!XMPPConnectionStatus.get(key))
				System.exit(-1);
		}
		*/
		
		
		List<String> userList = new ArrayList<String>();

		String presenceUser = "presencelistener";

		AbstractXMPPConnection con = getXMPPConnection(presenceUser, PRESENCE_LISTENER_PWD, "XMPP");
		
		logger.info("Gathering users subscribed to presence listener node...");
		
		Roster roster = Roster.getInstanceFor(con);
		//roster.setRosterLoadedAtLogin(false);

		roster.reload();
		
		while (!roster.isLoaded()) {
			logger.info("Waiting for roster to be loaded");
			Thread.sleep(1000);
		}

		for (RosterEntry entry : roster.getEntries()) {
			String userName = entry.getJid().toString().split("@")[0];
			if (userName.startsWith("10")) // Add all usernames starting with pattern.
				userList.add(userName);
		}
		
		List<String> subList = new ArrayList<>(userList.subList(0, totalTestUsers));;

		con.disconnect();
		
		logger.info("Total users in the system: " + roster.getEntries().size()
		+ ". Total users used to test: " +  subList.size());
		
		return subList;
	}
	
}
