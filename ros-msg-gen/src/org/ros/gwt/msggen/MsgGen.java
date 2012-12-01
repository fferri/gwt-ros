package org.ros.gwt.msggen;

import static java.lang.System.in;
import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * From a ros .msg file definition generates a Java bean
 * able to parse from and serialize to JSON strings.
 * 
 * @author Federico Ferri
 *
 */
public class MsgGen {
	static String getPackagePath(String classSpec) {
		int ldp = classSpec.lastIndexOf('.');
		return ldp < 0 ? "" : classSpec.substring(0, ldp + 1).replace('.', File.separatorChar);
	}
	
	static String getPackageName(String classSpec) {
		int ldp = classSpec.lastIndexOf('.');
		return ldp < 0 ? "" : classSpec.substring(0, ldp);
	}
	
	static String getClassName(String classSpec) {
		int ldp = classSpec.lastIndexOf('.');
		return ldp < 0 ? classSpec : classSpec.substring(ldp + 1);
	}
	
	static String getJavaType(String basePkg, String rosType) {
		if(rosType.equals("string")) return "String";
		if(rosType.equals("char")) return "char";
		if(rosType.equals("int8")) return "byte";
		if(rosType.equals("uint8")) return "byte";
		if(rosType.equals("int16")) return "short";
		if(rosType.equals("uint16")) return "short";
		if(rosType.equals("int32")) return "int";
		if(rosType.equals("uint32")) return "int";
		if(rosType.equals("int64")) return "long";
		if(rosType.equals("uint64")) return "long";
		if(rosType.equals("float32")) return "float";
		if(rosType.equals("float64")) return "double";
		if(rosType.equals("bool")) return "boolean";
		if(rosType.equals("duration")) return "Object";
		if(rosType.equals("time")) return "org.ros.gwt.client.msg_core.time";
		return basePkg + (basePkg.isEmpty() ? "" : ".") + rosType.replace('/', '.');
	}
	
	static boolean isNumericType(String rosType) {
		if(rosType.equals("int8")) return true;
		if(rosType.equals("uint8")) return true;
		if(rosType.equals("int16")) return true;
		if(rosType.equals("uint16")) return true;
		if(rosType.equals("int32")) return true;
		if(rosType.equals("uint32")) return true;
		if(rosType.equals("int64")) return true;
		if(rosType.equals("uint64")) return true;
		if(rosType.equals("float32")) return true;
		if(rosType.equals("float64")) return true;
		return false;
	}
	
	static boolean isPrimitiveType(String rosType) {
		if(rosType.equals("string")) return true;
		if(rosType.equals("char")) return true;
		if(rosType.equals("bool")) return true;
		if(rosType.equals("duration")) return true;
		if(isNumericType(rosType)) return true;
		return false;
	}
	
	static boolean isCoreType(String rosType) {
		if(rosType.equals("time")) return true;
		return false;
	}
	
	static String getBaseName(String s) {
		int i = s.lastIndexOf(File.separatorChar);
		int j = s.lastIndexOf('.');
		if(i < 0) i = 0; else i++;
		if(j < 0) j = s.length();
		return s.substring(i, j);
	}
	
	static List<String> getReversedPathComponents(File file) {
		List<String> pathComp = new LinkedList<String>();
		try {file = file.getCanonicalFile();} catch(IOException e) {}
		while(file != null) {
			pathComp.add(file.getName());
			file = file.getParentFile();
		}
		return pathComp;
	}
	
	static boolean matchPathNameToMsg(File file, String msgName) {
		if(!file.isFile()) return false;
		List<String> pathComp = getReversedPathComponents(file);
		if(pathComp.size() < 3) return false;
		ROSMsgType msgType = ROSMsgType.parse(msgName);
		return (pathComp.get(0).equals(msgType.type + ".msg")
				&& pathComp.get(1).equals("msg")
				&& (msgType.pkg == null || pathComp.get(2).equals(msgType.pkg)));
	}
	
	static Set<File> findMatchingMsgs(File root, String msgName, Set<File> result) {
		if(!root.isDirectory()) throw new IllegalArgumentException();
		File fs[] = root.listFiles();
		for(File f : fs) {
			if(matchPathNameToMsg(f, msgName))
				result.add(f);
			else if(f.isDirectory())
				findMatchingMsgs(f, msgName, result);
		}
		return result;
	}
	
	static File askUserWhichFile(Set<File> s) {
		if(s.isEmpty()) return null;
		if(s.size() == 1) return s.iterator().next();
		List<File> l = new ArrayList<File>();
		l.addAll(s);
		out.println("Found multiple matches:");
		for(int i = 0; i < l.size(); i++) {
			out.println("  " + (i + 1) + ") " + l.get(i).getAbsolutePath());
		}
		int j = -1;
		while(true) {
			out.print("Pick one: "); out.flush();
			try {
				String answer = new BufferedReader(new InputStreamReader(in)).readLine();
				j = Integer.parseInt(answer);
				if(j >= 1 && j <= l.size()) break;
			} catch(IOException e) {} catch(NumberFormatException e) {}
		}
		return l.get(--j);
	}
	
	static void generateJavaMsg(String rosMsgType, String basePkg, Set<String> generatedMsgs, File rosMsgSearchDir) throws IOException {
		// avoid cycles:
		if(generatedMsgs.contains(rosMsgType)) return;
		generatedMsgs.add(rosMsgType);
		
		Set<File> msgSearchResult = findMatchingMsgs(rosMsgSearchDir, rosMsgType, new HashSet<File>());
		File rosMsgFile = askUserWhichFile(msgSearchResult);
		if(rosMsgFile == null)
			throw new RuntimeException("ROS Message '" + rosMsgType + "' not found in " + rosMsgSearchDir.getAbsolutePath());
		
		ROSMsgFile rosMsgObj = ROSMsgFile.parse(rosMsgFile);
		
		// generate recursively:
		for(ROSMsgField field : rosMsgObj.fields) {
			if(!isPrimitiveType(field.fullType) && !isCoreType(field.fullType))
				generateJavaMsg(field.fullType, basePkg, generatedMsgs, rosMsgSearchDir);
		}
		
		ROSMsgType rosMsgTypeObj = ROSMsgType.parse(rosMsgType);
		String targetBasePkg = basePkg;
		if(rosMsgTypeObj.pkg != null) targetBasePkg += (targetBasePkg.isEmpty() ? "" : ".") + rosMsgTypeObj.pkg;
		String pkgPath = targetBasePkg.replace('.', File.separatorChar) + File.separator;
		new File(pkgPath).mkdirs();
		
		String outClassName = rosMsgTypeObj.type;
		File outFile = new File(pkgPath + outClassName + ".java");
		
		BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
		boolean _1st = true;
		String nl = System.getProperty("line.separator");
		if(!targetBasePkg.equals("")) {
			writer.write("package " + targetBasePkg + ";" + nl);
			writer.write("" + nl);
		}
		writer.write("import org.ros.gwt.client.msg_core.Message;" + nl);
		writer.write("import org.ros.gwt.client.msg_core.time;" + nl);
		writer.write("" + nl);
		writer.write("import com.google.gwt.json.client.*;" + nl);
		writer.write("" + nl);
		writer.write("public class " + outClassName + " extends Message {" + nl);
		for(ROSMsgField f : rosMsgObj.fields) {
			writer.write("	public " + getJavaType(basePkg, f.fullType) + (f.array ? "[]" : "") + " " + f.name + ";" + nl);
		}
		writer.write("	" + nl);
		writer.write("	public " + outClassName + "() {}" + nl);
		writer.write("	" + nl);
		writer.write("	public " + outClassName + "(");
		_1st = true;
		for(ROSMsgField f : rosMsgObj.fields) {
			if(!_1st) writer.write(", ");
			else _1st = false;
			writer.write(getJavaType(basePkg, f.fullType) + (f.array ? "[]" : "") + " " + f.name + "_");
		}
		writer.write(") {");
		_1st = true;
		for(ROSMsgField f : rosMsgObj.fields) {
			if(!_1st) writer.write(" ");
			else _1st = false;
			writer.write(f.name + "=" + f.name + "_;");
		}
		writer.write("}" + nl);
		writer.write("	" + nl);
		
		ROSMsgType msgTypeObj = ROSMsgType.parse(rosMsgType);
		writer.write("	@Override" + nl);
		writer.write("	public String getTypeName() {" + nl);
		writer.write("		return \"" + msgTypeObj.type + "\";" + nl);
		writer.write("	}" + nl);
		writer.write("	" + nl);
		writer.write("	@Override" + nl);
		writer.write("	public String getPackageName() {" + nl);
		if(msgTypeObj.pkg == null)
			writer.write("		return null;" + nl);
		else
			writer.write("		return \"" + msgTypeObj.pkg + "\";" + nl);
		writer.write("	}" + nl);
		writer.write("	" + nl);
		writer.write("	@Override" + nl);
		writer.write("	public String getFullTypeName() {" + nl);
		if(msgTypeObj.pkg == null)
			writer.write("		return \"" + msgTypeObj.type + "\";" + nl);
		else
			writer.write("		return \"" + msgTypeObj.pkg + "/" + msgTypeObj.type + "\";" + nl);
		writer.write("	}" + nl);
		writer.write("	" + nl);
		
		writer.write("	@Override" + nl);
		writer.write("	public boolean parse(JSONValue v) {" + nl);
		writer.write("		try {" + nl);
		writer.write("			JSONObject obj = v.isObject();" + nl);
		for(ROSMsgField f : rosMsgObj.fields) {
			if(f.array) {
				writer.write("			{" + nl);
				writer.write("				JSONArray a = obj.get(\"" + f.name + "\").isArray();" + nl);
				writer.write("				" + f.name + " = new " + getJavaType(basePkg, f.fullType) + "[a.size()];" + nl);
				writer.write("				for(int i = 0; i < " + f.name + ".length; i++) {" + nl);
				if(isPrimitiveType(f.type)) {
					if(isNumericType(f.type))
						writer.write("					" + f.name + "[i] = (" + getJavaType(basePkg, f.fullType) + ")a.get(i).isNumber().doubleValue();" + nl);
					else if(f.type.equals("string"))
						writer.write("					" + f.name + "[i] = a.get(i).isString().stringValue();" + nl);
					else
						writer.write("					// TODO: parsing of ROS type '" + f.type + "' not implemented yet!" + nl);
				} else {
					writer.write("					if(!(" + f.name + "[i] = new " + getJavaType(basePkg, f.fullType) + "()).parse(a.get(i))) return false;" + nl);				
				}
				writer.write("				}" + nl);
				writer.write("			}" + nl);
			} else {
				if(isPrimitiveType(f.type)) {
					if(isNumericType(f.type))
						writer.write("			" + f.name + " = (" + getJavaType(basePkg, f.fullType) + ")obj.get(\"" + f.name + "\").isNumber().doubleValue();" + nl);
					else if(f.type.equals("string"))
						writer.write("			" + f.name + " = obj.get(\"" + f.name + "\").isString().stringValue();" + nl);
					else
						writer.write("			// TODO: parsing of ROS type '" + f.type + "' not implemented yet!" + nl);
				} else {
					writer.write("			if(!(" + f.name + " = new " + getJavaType(basePkg, f.fullType) + "()).parse(obj.get(\"" + f.name + "\"))) return false;" + nl);
				}
			}
		}
		writer.write("			return true;" + nl);
		writer.write("		} catch(Exception e) {" + nl);
		writer.write("			e.printStackTrace();" + nl);
		writer.write("			return false;" + nl);
		writer.write("		}" + nl);
		writer.write("	}" + nl);
		writer.write("	" + nl);
		writer.write("	@Override" + nl);
		writer.write("	public JSONValue toJSON() {" + nl);
		writer.write("		JSONObject o = new JSONObject();" + nl);
		for(ROSMsgField f : rosMsgObj.fields) {
			if(f.array) {
				writer.write("		{" + nl);
				writer.write("			JSONArray a = new JSONArray();" + nl);
				writer.write("			for(int i = 0; i < " + f.name + ".length; i++) {" + nl);
				if(isPrimitiveType(f.type)) {
					if(isNumericType(f.type))
						writer.write("				a.set(i, new JSONNumber(" + f.name + "[i]));" + nl);
					else if(f.type.equals("string"))
						writer.write("				a.set(i, new JSONString(" + f.name + "[i]));" + nl);
					else
						writer.write("				// TODO: conversion of type " + getJavaType(basePkg, f.fullType) + " to JSON not implemented yet!" + nl);
				} else {
					writer.write("				a.set(i, " + f.name + "[i].toJSON());" + nl);
				}
				writer.write("			}" + nl);
				writer.write("			o.put(\"" + f.name + "\", a);" + nl);
				writer.write("		}" + nl);
			} else {
				if(isPrimitiveType(f.type)) {
					if(isNumericType(f.type))
						writer.write("		o.put(\"" + f.name + "\", new JSONNumber(" + f.name + "));" + nl);
					else if(f.type.equals("string"))
						writer.write("		o.put(\"" + f.name + "\", new JSONString(" + f.name + "));" + nl);
					else
						writer.write("		// TODO: conversion of type " + getJavaType(basePkg, f.fullType) + " to JSON not implemented yet!" + nl);
				} else {
					writer.write("		o.put(\"" + f.name + "\", " + f.name + ".toJSON());" + nl);
				}
			}
		}
		writer.write("		return o;" + nl);
		writer.write("	}" + nl);
		writer.write("}" + nl);
		writer.close();
		out.println("Generated " + outFile.getAbsolutePath());
	}
	
	public static void main(String[] args) throws IOException {
		if(args.length != 1) {
			out.println("usage: java " + MsgGen.class + " msgType");
			out.println("");
			out.println("recognized properties:");
			out.println("  " + MsgGen.class.getName() + ".targetPkg");
			out.println("  " + MsgGen.class.getName() + ".msgSearchPath");
			out.println("");
			System.exit(1);
		}
		
		String targetPkg = System.getProperty(MsgGen.class.getName() + ".targetPkg", "");
		File msgSearchPath = new File(System.getProperty(MsgGen.class.getName() + ".msgSearchPath"));
		String rosMsg = args[0];
		
		generateJavaMsg(rosMsg, targetPkg, new HashSet<String>(), msgSearchPath);
	}
}
