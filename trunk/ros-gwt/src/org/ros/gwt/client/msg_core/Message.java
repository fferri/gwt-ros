package org.ros.gwt.client.msg_core;

import com.google.gwt.json.client.JSONValue;

public abstract class Message {
	public abstract boolean parse(JSONValue v);
	
	public abstract JSONValue toJSON();
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null) return false;
		if(obj instanceof Message) {
			return toJSON().equals(((Message)obj).toJSON());
		} else return false;
	}
}
