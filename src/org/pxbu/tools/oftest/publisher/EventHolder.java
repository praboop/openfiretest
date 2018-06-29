package org.pxbu.tools.oftest.publisher;

import org.json.JSONObject;

public class EventHolder {
	public EventHolder(JSONObject trackMsg, IEventSent iEventSent) {
		this.event = trackMsg;
		this.callback = iEventSent;
	}
	public JSONObject event;
	public IEventSent callback;
}
