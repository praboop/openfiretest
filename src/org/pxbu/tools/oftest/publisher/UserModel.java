package org.pxbu.tools.oftest.publisher;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UserModel {
	private String userName, extension;

	private SimpleDateFormat dateFormat;
	private static String USER_TEMPLATE;
	private static final String __USERID__ = "__USERID__", __EXTENSION__ = "__EXTENSION__", __STATE__ = "__STATE__", __TIME__ = "__TIME__", __REQID__ = "__REQUEST_ID__";
	static {
		try {
			USER_TEMPLATE = new String (Files.readAllBytes(
				    Paths.get(UserModel.class.getResource("/user.xml").toURI())), StandardCharsets.UTF_8);
		} catch (IOException | URISyntaxException e) {
			USER_TEMPLATE = null;
			e.printStackTrace();
		}
	}
	private int requestId = 0;
	private String userModal;
	private String requestIdStr = generateRequestId();
	
	public UserModel(String userName) {
		this.userName = userName;
		this.extension = userName;
		this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		
	}
	
	public String getUserId() {
		return this.userName;
	}
	
	public String getRequestId() {
		return requestIdStr;
	}
	
	private String generateRequestId() {
		return requestIdStr = "req-" + this.userName + "-" + requestId++;
	}


	public UserModel setState(AgentState state) {
		userModal = USER_TEMPLATE;
		userModal = userModal.replaceAll(__USERID__, this.userName);
		userModal = userModal.replaceAll(__EXTENSION__, this.extension);
		userModal = userModal.replaceAll(__STATE__, state.toString()); 
		userModal = userModal.replaceAll(__REQID__, generateRequestId());
		
		// 2018-05-29T06:22:41.930Z
		userModal = userModal.replaceAll(__TIME__, this.dateFormat.format(new Date()));
		return this;
	}
	
	public String toString() {
		return this.userModal;
	}

}
