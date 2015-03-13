

# Introduction #

There are 3 functionalities covered by rosbridge, which are:

  * Topics (advertise/publish/subscribe of messages)
  * Services
  * Params

# Basic usage #

Set up the connection to rosbridge and listen for connection state change or erors:

```java

ConnectionStateListener connStateListener = new ConnectionStateListener() {
public void onOpen() {
log("Opened socket");
}

public void onError() {
log("Error in socket");
}

public void onClose() {
log("Socket closed");
}
};

if(!JavaScriptWebSocket.IsSupported()) {
Window.alert("WebSockets not supported by your browser.\n\nUpgrade your browser.");
} else {
ros = new ROS("ws://rosbridge-server:9090/", connStateListener);
// connection is opened automatically on object creation
}
```

# ROS Introspection #

You can ask the list of topics using the `getTopics` method:

```java

ros.getTopics(new MessageListener() {
public void onMessage(List<ROS.Topic> result) {
Window.alert(result.toString());
}
});
```

and in a similar way, works also for services and params, using `getServices` and `getParams`.

# Topics #

Construct a topic object:

```java

ROS.Topic cmdVel = ros.newTopic("/cmd_vel", "geometry_msgs/Twist");
```

now it can be used for publishing messages:

```java

topic.publish(msg);
// note: the topic will be advertised if it has not done before
```

subscribing:

```java

topic.subscribe(new MessageListener() {
public void onMessage(JSONObject result) {
log(result.toString());
}
});
```

and unsubscribing:

```java

topic.unsubscribe();
```

# Services #

Construct a service object:

```java

ROS.Service s = ros.newService("/myService", "mypkg/MyServiceType");
```

```java

s.callService(args, new MessageListener() {
public void onMessage(JSONObject result) {
// use the result
}
});
```

# Params #

Construct a param object:

```java

ROS.Param param = ros.newParam("/myparam");
```

Get it:

```java

param.get(new ValueListener<String>() {
public void onValue(String value) {
Window.alert("param value is " + value);
}
});
```

or set it:

```java

param.set(paramValue);
```

# Javadoc #

Here is a [link to Javadoc](http://gwt-ros.googlecode.com/svn/trunk/ros-gwt/doc/index.html)