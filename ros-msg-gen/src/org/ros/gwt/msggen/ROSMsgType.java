package org.ros.gwt.msggen;

/**
 * ROS .msg type parser.
 * 
 * @author Federico Ferri
 *
 */
public class ROSMsgType {
	public final String pkg;
	public final String type;
	public final boolean array;
	
	protected ROSMsgType(String pkg, String type, boolean array) {
		this.pkg = pkg;
		this.type = type;
		this.array = array;
	}
	
	public static ROSMsgType parse(String line) {
		String type, pkg = null; boolean array;
		type = line.trim();
		if(type.length() > 2 && type.substring(type.length() - 2).equals("[]")) {
			array = true;
			type = type.substring(0, type.length() - 2);
		} else {
			array = false;
		}
		int ios = type.lastIndexOf('/');
		if(ios >= 0) {
			pkg = type.substring(0, ios);
			type = type.substring(ios + 1);
		}
		return new ROSMsgType(pkg, type, array);
	}
}