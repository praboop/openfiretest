package org.pxbu.tools.oftest.util.xmpp;

import org.json.JSONObject;

public interface ICallback {
	void notifyMatch(JSONObject event);
	
	void notifyListenerStatus(String userId, boolean status);
}
