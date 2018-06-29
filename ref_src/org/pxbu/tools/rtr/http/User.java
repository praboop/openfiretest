package org.pxbu.tools.rtr.http;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.json.XML;
import java.util.*;

import org.pxbu.tools.rtr.http.Constants.*;
import org.pxbu.tools.rtr.xmpp.EventingService;
import org.pxbu.tools.rtr.xmpp.ICallback;
public class User {
	
	private static Logger logger = Logger.getLogger(User.class);
	
	private Client api;
	public static int PRETTY_PRINT_INDENT_FACTOR = 4;
	private State targetState;
	private State currentState;
	private Integer extension;
	private AtomicBoolean userEventReceived = new AtomicBoolean(false);
	
	class EventListener implements ICallback {

		@Override
		public boolean matches(JSONObject json) {
			userEventReceived.set(true);
			
			if (!json.has("user")) {
				return false;
			} else {
				json = json.getJSONObject("user");
				currentState = State.valueOf(json.getString("state"));
				return currentState == targetState;
			}
		}

		@Override
		public void notifyMatch(JSONObject user) {
			logger.debug("====> MATCHED THE TARGET STATE: " + targetState.toString() + " for " + api.getUserName());
		}

		@Override
		public void notifyListenerStatus(String userId, boolean status) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	public User(Client api, EventingService event) {
		this.api = api;
		event.register(new EventListener());
	}
	
	public void login(int extension) throws Exception {
		this.extension = extension;
		String url = "https://finesse25.autobot.cvp:8445/desktop/container/j_security_check?locale=en_US";
		Map<String, String> formData = new HashMap<String, String>();
		formData.put("j_username", api.getUserName());
		formData.put("j_password", api.getPassword());
		formData.put("extension_login_user", extension+"");
		formData.put("mobile_agent_mode", "CALL_BY_CALL");
		formData.put("mobile_agent_dialnumber", "");
		
		this.targetState = State.NOT_READY;
		logger.info("Logging in the user " + api.getUserName() + " extension " + extension);
		this.api.sendPOST(url, formData);
		this.setState(State.LOGIN, this.targetState, extension+"");
	}
	
	public int getExtension() {
		return this.extension;
	}
	
	public State getState() throws IOException {
		String response = api.sendGet("User/" + api.getUserName());
		JSONObject xmlJSONObj = XML.toJSONObject(response);
       // String jsonPrettyPrintString = xmlJSONObj.toString(PRETTY_PRINT_INDENT_FACTOR);
       // System.out.println(jsonPrettyPrintString);
		currentState = State.valueOf(((JSONObject) xmlJSONObj.get("User")).getString("state"));
		
		if (currentState != State.LOGOUT)
			extension = xmlJSONObj.getJSONObject("User").getInt("extension");
        return currentState;
	}
	
	public State getLastKnownState() {
		return currentState;
	}
	
	public void setState(State newState, State targetState, String extension) throws Exception {
		this.targetState = targetState;
		String extPart = (extension.isEmpty()) ? "": "<extension>" + extension.toString() + "</extension>";
		
		String content = "<User><state>" + newState.toString() + "</state>" + extPart + "</User>";
		userEventReceived.set(false);
		String response = api.sendPut("User/" + api.getUserName(), content);
		logger.debug("Set " + api.getUserName() + " to " + newState + " : " + response);
	}
	
	public void setState(State newState, State targetState) throws Exception {
		setState(newState, targetState, "");
	}
	
	public static void sleep(long millis) {
		try {
			Thread.currentThread().sleep(millis);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void expectEvent() throws Exception {
		long maxTimeToWait = 10000;
		long stepTime = 100;
		while(!userEventReceived.get() && maxTimeToWait > 0) {
			sleep(stepTime);
			maxTimeToWait -= stepTime;
			//System.out.println("WAITING FOR RIGHT EVENT..." + maxTimeToWait);
		}
		if (maxTimeToWait <= 0) {
			throw new Exception("Timedout waiting for state change." + this.api.getUserName() + " from " + this.currentState + " to " + this.targetState);
		}
	}
}