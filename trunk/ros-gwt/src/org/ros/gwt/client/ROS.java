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
	 * Generator of uniquely identified messages. This UID is used for
	 * tracking which response belongs to which request made to rosbridge.
	 */
	private class UIDGenerator {
		private long idCounter = 0;
		
		public String generate(String op, String name) {
			return op + ":" + name + ":" + ++idCounter;
		}
	}
	
	private final UIDGenerator uidGenerator = new UIDGenerator();
	
	/**
	 * Map of listeners keyed by op/UID.
	 */
	private Map<String, List<MessageListener>> messageListeners = new HashMap<String, List<MessageListener>>();

	/**
	 * Construct a {@link ROS} object for communicating with the rosbridge.
	 * 
	 * @param url {@link WebSocket} url to rosbridge.
	 * @param listener {@link ConnectionStateListener} that reports connection state changes.
	 */
	public ROS(String url, final ConnectionStateListener listener) {
		addMessageListener("png", new MessageListener() {
			public void onMessage(JSONObject message) {
				JSONString base64data = message.get("data").isString();
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
							ROS.this.onMessage(jsonData.toString());
						}
					});
					image.setUrl("data:image/png;base64," + base64data.stringValue());
				}
			}
		});
		addMessageListener("publish", new MessageListener() {
			public void onMessage(JSONObject message) {
				JSONString topic = message.get("topic").isString();
				JSONObject msg = message.get("msg").isObject();
				if(topic != null)
					callListeners(topic.stringValue(), msg);
			}
		});
		addMessageListener("service_response", new MessageListener() {
			public void onMessage(JSONObject message) {
				JSONString id = message.get("id").isString();
				JSONObject values = message.get("values").isObject();
				if(id != null)
					callListeners(id.stringValue(), values);
			}
		});
		
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
	 * Register a listener for a certain op (or UID).
	 * Multiple listener will be called sequentially.
	 * 
	 * @param op The op (or UID) to register handler for.
	 * @param listener The listener callback.
	 */
	public void addMessageListener(String op, MessageListener listener) {
		List<MessageListener> l = messageListeners.get(op);
		if(l == null)
			l = new ArrayList<MessageListener>();
		if(!l.contains(listener))
			l.add(listener);
		messageListeners.put(op, l);
	}

	/**
	 * Deregister a specific listener for a certain op (or UID).
	 * 
	 * @param op The op (or UID) where the listener belongs to.
	 * @param listener The messageCallback to deregister.
	 */
	public void removeMessageListener(String op, MessageListener listener) {
		List<MessageListener> l = messageListeners.get(op);
		if(l == null)
			return;
		if(l.contains(listener))
			l.remove(listener);
	}

	/**
	 * Deregister all listeners for a certain op (or UID).
	 *  
	 * @param op The op (or UID).
	 */
	public void removeAllMessageListeners(String op) {
		messageListeners.remove(op);
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
		else
			System.out.println("Unhandled JSON message: " + value.toString());
	}
	
	/**
	 * Handler for JS Objects coming from the {@link WebSocket}.
	 * @param message Message received.
	 */
	protected void onMessage(JSONObject message) {
		if(message == null) {
			System.out.println("onMessage(null) called.");
			return;
		}
		
		JSONString op = message.get("op").isString();
		if(op != null)
			callListeners(op.stringValue(), message);
		else
			System.out.println("onMessage(): malformed protocol message: " + message.toString());
	}
	
	/**
	 * Call handlers for a certain op (or UID).
	 * 
	 * @param key The op (or UID).
	 * @param arg Object argument.
	 */
	protected void callListeners(String key, JSONObject arg) {
		List<MessageListener> listeners = messageListeners.get(key);
		if(listeners != null) {
			for(MessageListener listener : listeners) {
				listener.onMessage(arg);
			}
		}
	}

	/**
	 * Retrieve topics from rosbridge, using the /rosapi/topics service call.
	 * 
	 * @param callback Async result callback.
	 */
	public void getTopics(final Callback<List<Topic>, Void> callback) {
		Service topicsClient = new Service("/rosapi/topics", "rosapi/Topics");
		topicsClient.callService(new JSONObject(), new MessageListener() {
			public void onMessage(JSONObject result) {
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
		});
	}
	
	/**
	 * Retrieve topics from rosbridge, using the /rosapi/services service call.
	 * 
	 * @param callback Async result callback.
	 */
	public void getServices(final Callback<List<Service>, Void> callback) {
		Service servicesClient = new Service("/rosapi/services", "rosapi/Services");
		servicesClient.callService(new JSONObject(), new MessageListener() {
			public void onMessage(JSONObject result) {
				List<Service> services = new ArrayList<Service>();
				JSONArray a = result.get("services").isArray();
				if(a != null) {
					for(int i = 0; i < a.size(); i++) {
						JSONString s = a.get(i).isString();
						if(s != null)
							services.add(new Service(s.stringValue(), null));
					}
				}
				callback.onSuccess(services);
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
		servicesClient.callService(new JSONObject(), new MessageListener() {
			public void onMessage(JSONObject result) {
				List<Param> params = new ArrayList<Param>();
				JSONArray a = result.get("names").isArray();
				if(a != null) {
					for(int i = 0; i < a.size(); i++) {
						JSONString s = a.get(i).isString();
						if(s != null)
							params.add(new Param(s.stringValue()));
					}
				}
				callback.onSuccess(params);
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
	 * {@link ROS#addMessageListener} and {@link ROS#removeMessageListener}.
	 * 
	 * @author Federico Ferri
	 *
	 */
	public static interface MessageListener {
		public void onMessage(JSONObject message);
	}
	
	public static interface ValueListener<T> {
		public void onValue(T value);
	}

	/**
	 * Create a new topic object.
	 * 
	 * @param name The ROS topic name.
	 * @param messageType The type of the exchanged messages.
	 * @return A {@link ROS.Topic} object.
	 */
	public Topic newTopic(String name, String messageType) {
		return new Topic(name, messageType);
	}
	
	/**
	 * Create a new service object.
	 * 
	 * @param name The ROS service name.
	 * @param serviceType The type of the exchanged messages.
	 * @return A {@link ROS.Service} object.
	 */
	public Service newService(String name, String serviceType) {
		return new Service(name, serviceType);
	}
	
	/**
	 * Create a new param object.
	 * 
	 * @param name The ROS param name.
	 * @return A {@link ROS.Param} object.
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

		public String getName() {
			return name;
		}

		public String getMessageType() {
			return messageType;
		}

		protected void setAdvertised(boolean advertised) {
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
		 * @param listener Async callback.
		 */
		public void subscribe(final MessageListener listener) {
			final String subscribeId = uidGenerator.generate("subscribe", name);
			addMessageListener(getName(), new MessageListener() {
				public void onMessage(JSONObject message) {
					listener.onMessage(message);
				}
			});
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
			removeAllMessageListeners(getName());
			final String unsubscribeId = uidGenerator.generate("unsubscribe", name);
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
			final String advertiseId = uidGenerator.generate("advertise", name);
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
			final String unadvertiseId = uidGenerator.generate("unadvertise", name);
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
			final String publishId = uidGenerator.generate("publish", name);
			JSONObject o = new JSONObject();
			o.put("op", new JSONString("publish"));
			o.put("id", new JSONString(publishId));
			o.put("topic", new JSONString(name));
			o.put("msg", message);
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
		
		public String getServiceType() {
			return serviceType;
		}
		
		@Override
		public String toString() {
			return getName();
		}

		/**
		 * Make a call to this service.
		 * 
		 * @param args Arguments to the service.
		 * @param listener Async result callback.
		 */
		public void callService(JSONObject args, final MessageListener listener) {
			final String serviceCallId = uidGenerator.generate("call_service", name);
			if(listener != null)
				addMessageListener(serviceCallId, new MessageListener() {
					public void onMessage(JSONObject message) {
						listener.onMessage(message);
						removeMessageListener(serviceCallId, this);
					}
				});
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
		public void get(final ValueListener<String> callback) {
			Service paramClient = new Service("/rosapi/get_param", "rosapi/GetParam");
			JSONObject serviceArgs = new JSONObject();
			serviceArgs.put("name", new JSONString(name));
			paramClient.callService(serviceArgs, new MessageListener() {
				public void onMessage(JSONObject message) {
					callback.onValue(message.get("value").isString().stringValue());
				}
			});
		}
		
		/**
		 * Set the value of this param.
		 * 
		 * @param value Param value to set.
		 */
		public void set(String value) {
			Service paramClient = new Service("/rosapi/set_param", "rosapi/SetParam");
			JSONObject serviceArgs = new JSONObject();
			serviceArgs.put("name", new JSONString(name));
			serviceArgs.put("value", new JSONString(value));
			paramClient.callService(serviceArgs, null);
		}
	}
}
