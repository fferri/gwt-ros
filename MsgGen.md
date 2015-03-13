# Usage #

## Synopsis ##

`java <properties> org.ros.gwt.msggen.MsgGen <msgName>`

## Example ##

```
java -cp path/to/MsgGen \
    -Dorg.ros.gwt.msggen.MsgGen.targetPkg="your.gwt.client.package" \
    -Dorg.ros.gwt.msggen.MsgGen.msgSearchPath="/opt/ros/fuerte" \
    org.ros.gwt.msggen.MsgGen msgName
```

It will generate **msgName.java** in the **your.gwt.client.package package**.

It will recursively generate any non-primitive type fields found in the message definitions.

If the msgName argument is not a fully qualified message, and multiple matches are found, the user will be asked to choose which of the potential matches is the right one.

## Properties ##

**org.ros.gwt.msggen.MsgGen.targetPkg**: generated files will be put in this package

**org.ros.gwt.msggen.MsgGen.msgSearchPath**: path to search for .msg files

## Arguments ##

**msgName**: The message name to generate the Java class for.