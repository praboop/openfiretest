package org.pxbu.tools.oftest.controller.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class Statistics {

	final static Logger logger = Logger.getLogger(Statistics.class);
	private final String STATS_URL = "/api/statistics";
	private Map<Integer, UserEvent> mapUserData = new ConcurrentHashMap<>();
	private Boolean isPaused = false;
	
	static final char PATH_SEP = File.separatorChar;
	static final String STAT_DIR = System.getProperty("user.home") + PATH_SEP + "ofload" + PATH_SEP;
	static {
		new File(STAT_DIR).mkdir();
	}
	
	private Boolean isCollectingStats = false;
	
	private StatisticsRecorder recorder = new StatisticsRecorder();
	
	static SimpleDateFormat recordierFileFormat = new SimpleDateFormat("HH:mm:ss.SSS");

	/*
	 * JSONArray allUsers, int index, int id, int sent, int rcv
	 */
	private JSONObject getStatus() throws JSONException {

		JSONObject outer = new JSONObject();
		JSONArray jsonUserData = new JSONArray();
		outer.put("data", jsonUserData);
		for (Map.Entry<Integer, UserEvent> entry : mapUserData.entrySet()) {
			UserEvent ue = entry.getValue();
			JSONArray jsonUser = new JSONArray();
			jsonUser.put(entry.getKey()).put(ue.getListenerStatus()).put(ue.getAckEventsCount()).put(ue.getUnAckEventCount());
			jsonUserData.put(jsonUser);
		}
		return outer;
	}

	public void setUsers(JSONArray users) throws JSONException {
		mapUserData.clear();
		for (int i=0; i<users.length(); i++) {
			mapUserData.put(users.getInt(i), new UserEvent());
		}
	}
	
	class StatisticsRecorder {
		final long FREQUENCY_SEC = 1;
		ScheduledExecutorService singleThreadExecutor;
		File logFile;
		private FileOutputStream fos;

		public String start() {
			if (singleThreadExecutor != null) {
				return ("Wont start since it is already running!");
			}
			singleThreadExecutor = Executors.newSingleThreadScheduledExecutor();
			singleThreadExecutor.scheduleAtFixedRate(getRunnable(), 0, FREQUENCY_SEC, TimeUnit.SECONDS);
			String fileName = STAT_DIR + "Stat-Recording-" + recordierFileFormat.format(new Date()) + ".csv";
			this.logFile = new File(fileName);
			try {
				this.fos = new FileOutputStream(logFile);
				String line = "TIME, LISTENING, NOT_LISTENING, ACK, UNACK\n";
				fos.write(line.toString().getBytes());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return "Recording in " + fileName;
		}
		
		private Runnable getRunnable() {
			return new Runnable() {
				public void run() {
					StringBuilder line = new StringBuilder();
					line.append(recordierFileFormat.format(new Date())).append(",");
					int agentListening = 0;
					int agentListeningFailed = 0;
					int acknowledged = 0;
					int unacknowledged = 0;
					for (UserEvent userEvent: mapUserData.values()) {
						if (userEvent.isListening != null) {
							if (userEvent.isListening) 
								agentListening++;
							else 
								agentListeningFailed++;
						}
						acknowledged += userEvent.getAckEventsCount();
						unacknowledged += userEvent.getUnAckEventCount();
					}
					line.append(agentListening).append(",").append(agentListeningFailed).append(",").append(acknowledged).append(",").append(unacknowledged).append("\n");
					try {
						fos.write(line.toString().getBytes());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
		}
		
		public String stop() {
			if (singleThreadExecutor != null && !singleThreadExecutor.isTerminated()) {
				singleThreadExecutor.shutdown();
				try {Thread.sleep(1000);} catch (Exception e) {};
				if (!singleThreadExecutor.isTerminated()) {
					singleThreadExecutor.shutdownNow();
				}
				singleThreadExecutor = null;
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return "Stopped recording. Results are in " + logFile.getName();
			}
			return "Already Stopped";
		}
	}

	class UserEvent {

		private List<String> unAckEvents = new ArrayList<String>();
		private Integer totalAckEvents = 0;
		private Boolean isListening = null;

		void addNewEvent(String event) {

			unAckEvents.add(event);
		}

		void ackEvent(String event) {

			if (unAckEvents.remove(event))
				totalAckEvents++;
		}

		int getUnAckEventCount() {

			return unAckEvents.size();
		}
		
		String[] getUnAckEvents() {
			return unAckEvents.toArray(new String[0]);
		}

		int getAckEventsCount() {

			return totalAckEvents.intValue();
		}

		public void setListenerStatus(Boolean status) {
			isListening = status;
		}
		
		public Boolean getListenerStatus() {
			return isListening;
		}
	}

	private void updateData(Integer id, String eventType, String event) throws JSONException {

		UserEvent userEvent = mapUserData.get(id);
		if (userEvent == null) {
			userEvent = new UserEvent();
			mapUserData.put(id, userEvent);
		}
		if (eventType.equalsIgnoreCase("ack")) {
			userEvent.ackEvent(event);
		} else if (eventType.equalsIgnoreCase("new")) {
			userEvent.addNewEvent(event);
		} else if (eventType.equalsIgnoreCase("ListenerStatus")) {
			userEvent.setListenerStatus(new Boolean(event));
		} 
		else {
			System.err.println("Unknown event type: " + eventType);
			System.exit(0);
		}
	}

	private HttpHandler statsHandler = new HttpHandler() {
		
		private String executeAction(String action) {
			if (action.equalsIgnoreCase("clear")) {
				System.out.println("Clearing map");
				mapUserData.clear();
				return "Cleared statistics";
			} else if (action.equals("unackevents")) {
				JSONArray array = new JSONArray();
				for (Map.Entry<Integer, UserEvent> entry : mapUserData.entrySet()) {
					UserEvent ue = entry.getValue();
					String[] unackEvents = ue.getUnAckEvents();
					if (unackEvents.length > 0)
						for (String reqId: unackEvents) {
							array.put(reqId);
						}
				}
				return array.toString();
			} else if (action.equalsIgnoreCase("Pause")) {
				isPaused = true;
				return "Pausing event publish";
			} else if (action.equalsIgnoreCase("Resume")) {
				isPaused = false;
				return "Resuming event publish";
			} else if (action.equalsIgnoreCase("actions")) {
				JSONObject status = new JSONObject();
				try {
					status.put("pauseResumeButtonText", isPaused ? "Pause" : "Resume");
					status.put("statsButtonText", !isCollectingStats ? "Record Stats" : "Stop Stats Recording");
				} catch (JSONException e) {
					e.printStackTrace();
				}

				return status.toString();
			} else if (action.equalsIgnoreCase("Record Stats")) {
				isCollectingStats = true;
				return recorder.start();
			} else if (action.equalsIgnoreCase("Stop Stats Recording")) {
				isCollectingStats = false;
				return recorder.stop();
			}

			return "Unknown action " + action;
		}

		public void handle(HttpExchange t) throws IOException {

			try {
				Map params = (Map) t.getAttribute("parameters");
				String method = t.getRequestMethod();
				String returnContent = "Unhandled request method: " + method;
				if (method.equals("PUT")) {
					StringWriter writer = new StringWriter();
					IOUtils.copy(t.getRequestBody(), writer, "UTF-8");
					String theString = writer.toString();
					//System.out.println(Thread.currentThread().getName() + ": Got the string: " + theString);
					logger.info("CONSUMER EVENT: " + theString);
					JSONArray users = new JSONArray(theString);
					for (int i=0; i< users.length(); i++) {
						JSONObject user = users.getJSONObject(i);
						Integer id = user.getInt("id");
						String eventType = user.getString("type");
						String event = eventType.equals("ListenerStatus") ? user.getBoolean("event") + "" : user.getString("event");
						updateData(id, eventType, event);
					}

					returnContent = new Boolean(isPaused).toString();
					t.getResponseHeaders().add("Content-Type", MediaType.TEXT_PLAIN);
				} else if (method.equals("GET")) {
					t.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON);
					if (params.containsKey("action")) {
						returnContent = executeAction(params.get("action").toString());
					} else if (params.containsKey("actions")) {
						returnContent = executeAction("actions");
					}
					else {
						returnContent = getStatus().toString();
					}
				}
				t.sendResponseHeaders(200, returnContent.length());
				OutputStream os = t.getResponseBody();
				os.write(returnContent.getBytes());
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};


	public HttpHandler getStatisticsHandler() {

		return statsHandler;
	}

	public String getStatsUrl() {

		return STATS_URL;
	}
}
