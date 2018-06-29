package org.pxbu.tools.oftest.publisher;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.jivesoftware.smackx.pubsub.Node;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.pxbu.tools.oftest.PublisherMain;
import org.pxbu.tools.oftest.util.httpclient.HttpBulkData;
import org.pxbu.tools.oftest.util.xmpp.EventingService;
import org.pxbu.tools.oftest.util.xmpp.EventingService.NODE_TYPE;

public class ScenarioPlayer extends Thread {
	private static Logger logger = Logger.getLogger(ScenarioPlayer.class);
	private UserModel user;
	private HttpBulkData<EventHolder> httpBulkUpdate;
	private long delay = PublisherMain.MESSAGE_INTERVAL_MILLIS;
	private EventingService service;
	private Node userNode;
	
	public ScenarioPlayer( EventingService service, HttpBulkData<EventHolder> bulkData, CountDownLatch latch, String userId, Node userNode) {
		this.service = service;
		user = new UserModel(userId);
		this.httpBulkUpdate = bulkData;
		this.userNode = userNode;
	}
	

	public void run() {
		try {
		AgentState state = AgentState.READY;
		Boolean isPaused = false;
		for (int i=0; i<100000; i++) {

			if (!isPaused) {
				state = (state == AgentState.READY) ? AgentState.NOT_READY : AgentState.READY;
				
						
						final JSONObject trackMsg = toSentMsg(user.setState(state));
						
						isPaused = httpBulkUpdate.addMessage(new EventHolder(trackMsg, new IEventSent() {

							@Override
							public void eventSent() {
									try {
										service.sendEvent(userNode, user.toString());
										// System.out.println(xmlPublished);
		
										logger.info("PUBLISHED EVENT " + trackMsg.toString() );
									} catch (Exception e) {
										try {
											logger.error("Unable to publish event " + trackMsg.getString("event") + " user " + trackMsg.getInt("id"));
										} catch (JSONException e1) {
											logger.error(e1);
										}
									} 
							}
							
						}));	
				
			} else {
				isPaused = httpBulkUpdate.isPaused();
				System.out.println("Waiting for resume " + user.getUserId() + ". Msg " + i);	
				Thread.sleep(3000);
				if (i > 0) i--;
			}

			Thread.sleep(delay);
		}
		System.out.println("Test complete for agent " + user.getUserId());
		} catch (Exception e) {
			new Exception(user.getUserId() + " stopped to run due to this error", e).printStackTrace();
		}
	}


	private JSONObject toSentMsg(UserModel userModel) throws JSONException {

		JSONObject jsonDetail = new JSONObject();
		jsonDetail.put("id", userModel.getUserId());
		jsonDetail.put("type", "new");
		jsonDetail.put("event", userModel.getRequestId());
		
		return jsonDetail;
	}
}
