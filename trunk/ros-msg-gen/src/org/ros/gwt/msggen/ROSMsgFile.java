package org.ros.gwt.msggen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ROS .msg file parser.
 * 
 * @author Federico Ferri
 *
 */
public class ROSMsgFile {
	public final List<ROSMsgField> fields = new ArrayList<ROSMsgField>();
	
	protected ROSMsgFile() {}
	
	public static ROSMsgFile parse(File f) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(f));
		String line;
		ROSMsgFile ret = new ROSMsgFile();
		while((line = reader.readLine()) != null) {
			line = stripComments(line);
			if(line.equals("")) continue;
			ret.fields.add(ROSMsgField.parse(line));
		}
		return ret;
	}
	
	static String stripComments(String line) {
		int p = line.indexOf('#');
		if(p >= 0)
			line = line.substring(0, p);
		line = line.trim();
		return line;
	}
}
