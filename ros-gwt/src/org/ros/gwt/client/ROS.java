package org.ros.gwt.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.CanvasPixelArray;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.ImageData;
import com.google.gwt.core.client.Callback;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import de.csenk.gwt.ws.client.WebSocket;
import de.csenk.gwt.ws.client.WebSocketCallback;
import de.csenk.gwt.ws.client.js.JavaScriptWebSocketFactory;

/**
 * {@link ROS} class encapsulate the stateful communication to rosbridge.
 * 
 * @author Federico Ferri
 *
 */
public class ROS {
	/**
	 * HTML5 {@link WebSocket} communication with rosbridge.
	 */
	private WebSocket socket;
	
	/**
	 * Counter for generating uniquely identified messages. This UID is used for
	 * tracking which response belongs to which request made to rosbridge.
	 */
	private long idCounter = 0;
	
	/**
	 * Map of handlers. Key is the UID, and value is a callback listening
	 * for some {@link JSONObject}.
	 */
	private Map<String, List<Callback<JSONObject, Void>>> messageHandlers = new HashMap<String, List<Callback<JSONObject, Void>>>();

	/**
	 * Construct a {@link ROS} object for communicating with the rosbridge.
	 * 
	 * @param url {@link WebSocket} url to rosbridge.
	 * @param listener {@link ConnectionStateListener} that reports connection state changes.
	 */
	public ROS(String url, final ConnectionStateListener listener) {
		JavaScriptWebSocketFactory jsWebSocketFactory = new JavaScriptWebSocketFactory();
		socket = jsWebSocketFactory.createWebSocket(url, new WebSocketCallback() {
			public void onOpen(WebSocket webSocket) {
				listener.onOpen();
			}

			public void onMessage(WebSocket webSocket, String message) {
				ROS.this.onMessage(message);
			}

			public void onError(WebSocket webSocket) {
				listener.onError();
			}

			public void onClose(WebSocket webSocket) {
				listener.onClose();
			}
		});
	}

	/**
	 * Closes the connection with the rosbridge.
	 */
	public void disconnect() {
		socket.close();
	}

	/**
	 * Send a raw ({@link String}) message over the {@link WebSocket}.
	 * 
	 * @param message Raw message string to send.
	 */
	protected void send(String message) {
		socket.send(message);
	}
	
	/**
	 * Send a JS Object over the {@link WebSocket}.
	 * 
	 * @param message Message object to send.
	 */
	protected void send(JSONObject message) {
		send(message.toString());
	}

	/**
	 * Register a handler for a certain op (or UID).
	 * Multiple handlers will be called sequentially.
	 * A op equal to * will be called for every op (or UID).
	 * 
	 * @param op The op (or UID) to register handler for.
	 * @param messageCallback The handler callback.
	 */
	public void addMessageHandler(String op, Callback<JSONObject, Void> messageCallback) {
		List<Callback<JSONObject, Void>> l = messageHandlers.get(op);
		if(l == null)
			l = new ArrayList<Callback<JSONObject, Void>>();
		if(!l.contains(messageCallback))
			l.add(messageCallback);
		messageHandlers.put(op, l);
	}

	/**
	 * Deregister a specific handler for a certain op (or UID).
	 * 
	 * @param op The op (or UID) where messageCallback belongs to.
	 * @param messageCallback The messageCallback to deregister.
	 */
	public void removeHandler(String op, Callback<JSONObject, Void> messageCallback) {
		List<Callback<JSONObject, Void>> l = messageHandlers.get(op);
		if(l == null)
			return;
		if(l.contains(messageCallback))
			l.remove(messageCallback);
	}

	/**
	 * Deregister all handlers for a certain op (or UID).
	 *  
	 * @param op The op (or UID).
	 */
	public void removeAllHandlers(String op) {
		messageHandlers.remove(op);
	}
	
	/**
	 * Handler for raw ({@link String}) messages coming from the {@link WebSocket}.
	 * 
	 * @param rawMessage Raw message string received.
	 */
	protected void onMessage(String rawMessage) {
		JSONValue value = JSONParser.parseStrict(rawMessage);
		JSONObject obj;
		if((obj = value.isObject()) != null)
			ROS.this.onMessage(obj);
	}
	
	/**
	 * Handler for JS Objects coming from the {@link WebSocket}.
	 * @param message Message received.
	 */
	protected void onMessage(JSONObject message) {
		JSONObject o = message.isObject();
		if(o != null) {
			String op = o.get("op").isString().stringValue();
			if(op.equals("png")) {
				JSONString base64data = o.get("data").isString();
				if(base64data != null) {
					final com.google.gwt.user.client.ui.Image image = new com.google.gwt.user.client.ui.Image();
					image.addLoadHandler(new LoadHandler() {
						public void onLoad(LoadEvent event) {
							Canvas canvas = Canvas.createIfSupported();
							Context2d context = canvas.getContext2d();
							context.drawImage(ImageElement.as(image.getElement()), 0, 0);
							ImageData imageData = context.getImageData(0, 0, image.getWidth(), image.getHeight());
							CanvasPixelArray rawData = imageData.getData();
							StringBuffer jsonData = new StringBuffer();
							for(int i = 0; i < rawData.getLength(); i += 1) {
								if(rawData.get(i) > 0) {
									jsonData.append(String.valueOf(rawData.get(i)));
								}
							}
							onMessage(jsonData.toString());
						}
					});
					image.setUrl("data:image/png;base64," + base64data.stringValue());
				}
			} else if(op.equals("publish")) {
				JSONString topic = o.get("topic").isString();
				JSONObject msg = o.get("msg").isObject();
				if(topic != null)
					callHandlers(topic.stringValue(), msg);
			} else if(op.equals("service_response")) {
				JSONString id = o.get("id").isString();
				JSONObject values = o.get("values").isObject();
				if(id != null)
					callHandlers(id.stringValue(), values);
			}

			callHandlers(op, message);
		}
	}

	/**
	 * Call handlers for a certain op (or UID).
	 * 
	 * @param key The op (or UID).
	 * @param arg Object argument.
	 */
	protected void callHandlers(String key, JSONObject arg) {
		List<Callback<JSONObject, Void>> handlers = messageHandlers.get(key);
		if(handlers != null) {
			for(Callback<JSONObject, Void> handler : handlers) {
				handler.onSuccess(arg);
			}
		} else {
		}
		if(!key.equals("*")) {
			callHandlers("*", arg);
		}
	}

	/**
	 * Retrieve topics from rosbridge, using the /rosapi/topics service call.
	 * 
	 * @param callback Async result callback.
	 */
	public void getTopics(final Callback<List<Topic>, Void> callback) {
		Service topicsClient = new Service("/rosapi/topics", "rosapi/Topics");
		topicsClient.callService(new JSONObject(), new Callback<JSONObject, Void>() {
			public void onSuccess(JSONObject result) {
				List<Topic> topics = new ArrayList<Topic>();
				JSONArray a = result.get("topics").isArray();
				if(a != null) {
					for(int i = 0; i < a.size(); i++) {
						JSONString s = a.get(i).isString();
						if(s != null)
							topics.add(new Topic(s.stringValue(), null));
					}
				}
				callback.onSuccess(topics);
			}
			
			public void onFailure(Void reason) {
				callback.onFailure(reason);
			}
		});
	}
	
	/**
	 * Retrieve topics from rosbridge, using the /rosapi/services service call.
	 * 
	 * @param callback Async result callback.
	 */
	public void getServices(final Callback<List<Service>, Void> callback) {
		Service servicesClient = new Service("/rosapi/services", "rosapi/Services");
		servicesClient.callService(new JSONObject(), new Callback<JSONObject, Void>() {
			public void onSuccess(JSONObject result) {
				List<Service> services = new ArrayList<Service>();
				JSONArray a = result.get("topics").isArray();
				if(a != null) {
					for(int i = 0; i < a.size(); i++) {
						JSONString s = a.get(i).isString();
						if(s != null)
							services.add(new Service(s.stringValue(), null));
					}
				}
				callback.onSuccess(services);
			}
			
			public void onFailure(Void reason) {
				callback.onFailure(reason);
			}
		});
	}
	
	/**
	 * Retrieve params from rosbridge, using the /rosapi/get_param_names service call.
	 * 
	 * @param callback Async result callback.
	 */
	public void getParams(final Callback<List<Param>, Void> callback) {
		Service servicesClient = new Service("/rosapi/get_param_names", "rosapi/GetParamNames");
		servicesClient.callService(new JSONObject(), new Callback<JSONObject, Void>() {
			public void onSuccess(JSONObject result) {
				List<Param> params = new ArrayList<Param>();
				JSONArray a = result.get("topics").isArray();
				if(a != null) {
					for(int i = 0; i < a.size(); i++) {
						JSONString s = a.get(i).isString();
						if(s != null)
							params.add(new Param(s.stringValue()));
					}
				}
				callback.onSuccess(params);
			}
			
			public void onFailure(Void reason) {
				callback.onFailure(reason);
			}
		});
	}

	/**
	 * {@link ConnectionStateListener} is notified about connection state
	 * changes in the {@link WebSocket}.
	 * 
	 * @author Federico Ferri
	 *
	 */
	public static interface ConnectionStateListener {
		public void onOpen();

		public void onError();

		public void onClose();
	}

	/**
	 * {@link MessageListener} is notified when a message is received from rosbridge.
	 * 
	 * {@link MessageListener}s are registered/deregistered using
	 * {@link ROS#addMessageHandler} and {@link ROS#removeHandler}.
	 * 
	 * @author Federico Ferri
	 *
	 */
	public static interface MessageListener {
		public void onMessage(JSONObject message);
	}

	/**
	 * Create a new topic object.
	 * 
	 * @param name
	 * @param messageType
	 * @return
	 */
	public Topic newTopic(String name, String messageType) {
		return new Topic(name, messageType);
	}
	
	/**
	 * Create a new service object.
	 * 
	 * @param name
	 * @param serviceType
	 * @return
	 */
	public Service newService(String name, String serviceType) {
		return new Service(name, serviceType);
	}
	
	/**
	 * Create a new param object.
	 * 
	 * @param name
	 * @return
	 */
	public Param newParam(String name) {
		return new Param(name);
	}

	/**
	 * Topic class.
	 * 
	 * Use this class to subscribe, unsubscribe, and publish messages to topics.
	 * 
	 * An instance of this class can be obtained with {@link ROS#newTopic}.
	 * 
	 * @author Federico Ferri
	 *
	 */
	public class Topic {
		private String name;
		private String messageType;
		private boolean advertised = false;
		private String compression = "none";

		protected Topic(String name, String messageType) {
			this.name = name;
			this.messageType = messageType;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setMessageType(String messageType) {
			this.messageType = messageType;
		}

		public String getMessageType() {
			return messageType;
		}

		public void setAdvertised(boolean advertised) {
			this.advertised = advertised;
		}

		public boolean isAdvertised() {
			return advertised;
		}

		public void setCompression(String compression) {
			this.compression = compression;
		}

		public String getCompression() {
			return compression;
		}
		
		@Override
		public String toString() {
			return getName();
		}

		/**
		 * Register a handler for subscribing to this topic.
		 * 
		 * @param callback Async callback.
		 */
		public void subscribe(Callback<JSONObject, Void> callback) {
			String subscribeId = "subscribe:" + name + ':' + ++idCounter;
			addMessageHandler(getName(), callback);
			// TODO: queue message if not connected
			JSONObject o = new JSONObject();
			o.put("op", new JSONString("subscribe"));
			o.put("id", new JSONString(subscribeId));
			o.put("type", new JSONString(messageType));
			o.put("topic", new JSONString(name));
			o.put("compression", new JSONString(compression));
			send(o);
		}

		/**
		 * Unsubscribe from this topic (unregister all handlers!).
		 * 
		 */
		public void unsubscribe() {
			removeAllHandlers(getName());
			String unsubscribeId = "unsubscribe:" + name + ":" + ++idCounter;
			// TODO: queue message if not connected
			JSONObject o = new JSONObject();
			o.put("op", new JSONString("unsubscribe"));
			o.put("id", new JSONString(unsubscribeId));
			o.put("topic", new JSONString(name));
			send(o);
		}

		/**
		 * Advertise other nodes that this topic is being published.
		 * 
		 */
		public void advertise() {
			String advertiseId = "advertise:" + name + ":" + ++idCounter;
			JSONObject o = new JSONObject();
			o.put("op", new JSONString("advertise"));
			o.put("id", new JSONString(advertiseId));
			o.put("type", new JSONString(messageType));
			o.put("topic", new JSONString(name));
			send(o);
			setAdvertised(true);
		};

		/**
		 * Stop advertising other nodes that this topic is being published.
		 * 
		 */
		public void unadvertise() {
			String unadvertiseId = "unadvertise:" + name + ":" + ++idCounter;
			JSONObject o = new JSONObject();
			o.put("op", new JSONString("unadvertise"));
			o.put("id", new JSONString(unadvertiseId));
			o.put("topic", new JSONString(name));
			send(o);
			setAdvertised(false);
		};

		/**
		 * Publish a message to this topic.
		 * 
		 * If this topic has not been advertised yet, it will be
		 * automatically advertised.
		 * 
		 * @param message Message to publish.
		 */
		public void publish(JSONValue message) {
			if(!isAdvertised()) {
				advertise();
			}
			String publishId = "publish:" + name + ":" + ++idCounter;
			JSONObject o = new JSONObject();
			o.put("op", new JSONString("publish"));
			o.put("id", new JSONString(publishId));
			o.put("topic", new JSONString(name));
			o.put("msg", message);
			System.out.println("ROS.publish(" + o.toString() + ")");
			send(o);
		}
	}
	
	/**
	 * Service class.
	 * 
	 * Use this class for calling ROS services.
	 * 
	 * An instance of this class can be obtained with {@link ROS#newService}.
	 * 
	 * @author Federico Ferri
	 *
	 */
	public class Service {
		private String name;
		private String serviceType;

		protected Service(String name, String serviceType) {
			this.name = name;
			this.serviceType = serviceType;
		}
		
		public String getName() {
			return name;
		}
		
		@Override
		public String toString() {
			return getName();
		}

		/**
		 * Make a call to this service.
		 * 
		 * @param args Arguments to the service.
		 * @param callback Async result callback.
		 */
		public void callService(JSONObject args, final Callback<JSONObject, Void> callback) {
			String serviceCallId = "call_service:" + name + ":" + ++idCounter;
			addMessageHandler(serviceCallId, callback);
			JSONObject o = new JSONObject();
			o.put("op", new JSONString("call_service"));
			o.put("id", new JSONString(serviceCallId));
			o.put("service", new JSONString(name));
			o.put("args", args);
			send(o);
		}
	}
	
	/**
	 * Param class.
	 * 
	 * Use this class to get or set ROS params.
	 * 
	 * An instance of this class can be obtained with {@link ROS#newParam}.
	 * 
	 * @author Federico Ferri
	 *
	 */
	public class Param {
		private String name;
		
		protected Param(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
		
		@Override
		public String toString() {
			return getName();
		}
		
		/**
		 * Get the value of this param.
		 * 
		 * @param callback Async result callback.
		 */
		public void get(final Callback<JSONObject, Void> callback) {
			Service paramClient = new Service("/rosapi/get_param", "rosapi/GetParam");
			JSONObject serviceArgs = new JSONObject();
			serviceArgs.put("name", new JSONString(name));
			serviceArgs.put("value", new JSONString(""));
			paramClient.callService(serviceArgs, new Callback<JSONObject, Void>() {
				public void onSuccess(JSONObject result) {
					callback.onSuccess(result);
				}
				
				public void onFailure(Void reason) {
					callback.onFailure(reason);
				}
			});
		}
		
		/**
		 * Set the value of this param.
		 * 
		 * @param value Param value to set.
		 * @param callback Async result callback.
		 */
		public void set(JSONValue value, final Callback<JSONObject, Void> callback) {
			Service paramClient = new Service("/rosapi/set_param", "rosapi/SetParam");
			JSONObject serviceArgs = new JSONObject();
			serviceArgs.put("name", new JSONString(name));
			serviceArgs.put("value", value);
			paramClient.callService(serviceArgs, new Callback<JSONObject, Void>() {
				public void onSuccess(JSONObject result) {
					callback.onSuccess(result);
				}
				
				public void onFailure(Void reason) {
					callback.onFailure(reason);
				}
			});
		}
	}
}
