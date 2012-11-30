package org.ros.gwt.msggen;

/**
 * ROS .msg field (line) parser.
 * 
 * @author Federico Ferri
 *
 */
public class ROSMsgField {
	public final String type;
	public final boolean array;
	public final String name;
	public final String pkg;
	public final String fullType;
	
	protected ROSMsgField(String pkg, String type, boolean array, String name) {
		this.pkg = pkg;
		this.type = type;
		this.array = array;
		this.name = name;
		if(pkg != null) fullType = pkg + "/" + type;
		else fullType = type;
	}
	
	public static ROSMsgField parse(String line) {
		String type, name; boolean array;
		type = line.substring(0, line.indexOf(' ')).trim();
		name = line.substring(line.indexOf(' ')).trim();
		if(type.length() > 2 && type.substring(type.length() - 2).equals("[]")) {
			array = true;
			type = type.substring(0, type.length() - 2);
		} else {
			array = false;
		}
		String pkg = null;
		int ios = type.lastIndexOf('/');
		if(ios >= 0) {
			pkg = type.substring(0, ios);
			type = type.substring(ios + 1);
		}
		return new ROSMsgField(pkg, type, array, name);
	}
}