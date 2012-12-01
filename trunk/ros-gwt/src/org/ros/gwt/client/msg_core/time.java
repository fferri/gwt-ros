package org.ros.gwt.client.msg_core;

import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONValue;

public class time extends Message {
	public long secs;
	public long nsecs;

	public time() {}
	
	public time(long secs_, long nsecs_) {secs=secs_; nsecs=nsecs_;}

	@Override
	public String getTypeName() {
		return "time";
	}

	@Override
	public String getPackageName() {
		return null;
	}

	@Override
	public String getFullTypeName() {
		return "time";
	}
	
	@Override
	public boolean parse(JSONValue v) {
		try {
			JSONObject obj = v.isObject();
			secs = (long)obj.get("secs").isNumber().doubleValue();
			nsecs = (long)obj.get("nsecs").isNumber().doubleValue();
			return true;
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	@Override
	public JSONValue toJSON() {
		JSONObject o = new JSONObject();
		o.put("secs", new JSONNumber(secs));
		o.put("nsecs", new JSONNumber(nsecs));
		return o;
	}
}
