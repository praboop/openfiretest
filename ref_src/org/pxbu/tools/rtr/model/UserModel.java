package org.pxbu.tools.rtr.model;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.pxbu.tools.rtr.http.User;
import org.pxbu.tools.rtr.http.Constants.*;
import org.pxbu.tools.rtr.xmpp.EventingService;
import org.pxbu.tools.rtr.xmpp.EventingService.NODE_TYPE;
import org.pxbu.tools.rtr.xmpp.ICallback;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserModel {
	private static Logger logger = Logger.getLogger(UserModel.class);
	
	private String userName, extension;
	private State currentState;
	private State targetState;
	private SimpleDateFormat dateFormat;
	private EventingService service;
	private static String USER_TEMPLATE;
	private static final String __USERID__ = "__USERID__", __EXTENSION__ = "__EXTENSION__", __STATE__ = "__STATE__", __TIME__ = "__TIME__";
	static {
		try {
			USER_TEMPLATE = new String (Files.readAllBytes(
				    Paths.get(UserModel.class.getResource("/user.xml").toURI())), StandardCharsets.UTF_8);
		} catch (IOException | URISyntaxException e) {
			USER_TEMPLATE = null;
			e.printStackTrace();
		}
	}
	private AtomicBoolean userEventReceived = new AtomicBoolean(false);
	
	
	public UserModel(String userName, String extension, State state, EventingService service) {
		this.userName = userName;
		this.extension = extension;
		this.currentState = this.targetState = state;
		this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		this.service = service;
		this.service.register(new ICallback() {

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
			public void notifyMatch(JSONObject event) {
				logger.debug("====> MATCHED THE TARGET STATE: " + targetState.toString());
				
			}

			@Override
			public void notifyListenerStatus(String userId, boolean status) {
				// TODO Auto-generated method stub
				
			}
			
		});
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getExtension() {
		return extension;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public State getState() {
		return currentState;
	}

	public void setState(State state) {
		String userModal = USER_TEMPLATE;
		userModal = userModal.replaceAll(__USERID__, this.userName);
		userModal = userModal.replaceAll(__EXTENSION__, this.extension);
		userModal = userModal.replaceAll(__STATE__, state.toString()); 
		
		// 2018-05-29T06:22:41.930Z
		userModal = userModal.replaceAll(__TIME__, this.dateFormat.format(new Date()));
		this.targetState = state;
		this.userEventReceived.set(false);
		
		service.sendEvent(NODE_TYPE.USER, userModal);
	}
	
	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
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
			throw new Exception("Timedout waiting for state change." + this.userName + " from " + this.currentState + " to " + this.targetState);
		}
	}

}
