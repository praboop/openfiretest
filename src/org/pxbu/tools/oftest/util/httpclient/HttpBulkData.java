package org.pxbu.tools.oftest.util.httpclient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.pxbu.tools.oftest.publisher.EventHolder;

/**
 * Handles data that is received from openfire by adding them to a internal queue for further processing
 * @author Prabhu Periasamy
 *
 */
public class HttpBulkData<T> {
	private static Logger logger = Logger.getLogger(HttpBulkData.class);
	final int maxDispatchSize = 10;
	final int numWorkers = 5;
	final ExecutorService service = Executors.newFixedThreadPool(numWorkers);
	private final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
	
	
	private boolean isPaused = false;


	public boolean addMessage(T message) {

		try {
			queue.put(message);
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		return isPaused;
	}
	
	public boolean isPaused() {
		return isPaused;
	}
	
	class WorkerThread implements Runnable {
		private LoadHttpClient httpClient;
		List<T> retrievedItems = new ArrayList<>();
		
		public WorkerThread(String url) {
			try {
				this.httpClient = new LoadHttpClient(url, "", "");
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		
		public void publish() throws Exception {
			JSONArray jsonArray = new JSONArray();
			for (T item: retrievedItems) {
				if (item instanceof EventHolder) {
					jsonArray.put(((EventHolder) item).event);
				} else {
					jsonArray.put(item);
				}
			}
			
			String returnMsg = this.httpClient.sendPut("api/statistics",jsonArray.toString());
			logger.info("Published " + jsonArray.toString());
			
			for (T item: retrievedItems) {
				if (item instanceof EventHolder) {
					((EventHolder) item).callback.eventSent();
				}
			}
			
			retrievedItems.clear();
			
			isPaused = Boolean.valueOf(returnMsg);
		}
		
		public void run() {

			while (!Thread.currentThread().isInterrupted()) {
				try {
					T item = queue.poll(1, TimeUnit.SECONDS);
					if (item == null && !retrievedItems.isEmpty()) {
						// Send out the previously retrieved items.
						publish();
						continue;
					}
					
					if (item != null) {
						retrievedItems.add(item);
					}
					
					if (retrievedItems.size() >= maxDispatchSize) {
						// Send out the retrieved items
						publish();
					}
					
				} catch (Exception ex) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}

	public void start(String url) {
		for (int i = 0; i < numWorkers; i++) {
			service.submit(new WorkerThread(url));
		}
	}
}