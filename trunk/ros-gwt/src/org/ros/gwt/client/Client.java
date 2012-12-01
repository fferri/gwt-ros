package org.ros.gwt.client;

import java.util.List;

import org.ros.gwt.client.ROS.ConnectionStateListener;
import org.ros.gwt.client.msg.Twist;
import org.ros.gwt.client.msg.Vector3;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;

import de.csenk.gwt.ws.client.js.JavaScriptWebSocket;

public class Client implements EntryPoint {
	private ROS ros;
	
	private final TextBox txtAddr = new TextBox();
	private final Button btnConnect = new Button("Connect");
	private final Button btnDisconnect = new Button("Disconnect");
	
	private final Button btnGetTopics = new Button("Get topics");
	private final Button btnGetServices = new Button("Get services");
	private final Button btnGetParams = new Button("Get params");

	private final TextBox txtTopicName = new TextBox();
	private final TextBox txtTopicType = new TextBox();
	private final TextBox txtTopicPayload = new TextBox();
	private final Button btnPublish = new Button("Publish");

	private final TextBox txtTopicNamePub = new TextBox();
	private final TextBox txtTopicTypePub = new TextBox();
	private final Button btnSubscribe = new Button("Subscribe");
	private final Button btnUnsubscribe = new Button("Unsubscribe");

	private final TextBox txtServiceArgs = new TextBox();
	private final TextBox txtServiceName = new TextBox();
	private final TextBox txtServiceType = new TextBox();
	private final Button btnCallService = new Button("Call Service");
	private final TextBox txtServiceResult = new TextBox();
	
	private final TextBox txtParamName = new TextBox();
	private final TextBox txtParamValue = new TextBox();
	private final Button btnParamSet = new Button("Set");
	private final Button btnParamGet = new Button("Get");

	private final ConnectionStateListener connStateListener = new ConnectionStateListener() {
		public void onOpen() {
			log("Opened socket");
			btnConnect.setEnabled(false);
			btnDisconnect.setEnabled(true);
		}
		
		public void onError() {
			log("Error in socket");
		}
		
		public void onClose() {
			log("Socket closed");
			btnConnect.setEnabled(true);
			btnDisconnect.setEnabled(false);
		}
	};
	
	public void onModuleLoad() {
		if(!JavaScriptWebSocket.IsSupported()) {
			Window.alert("WebSockets not supported by your browser.\n\nUpgrade your browser.");
            return;
		}
		
		RootPanel.get("addr").add(txtAddr);
		RootPanel.get("btnConnect").add(btnConnect);
		RootPanel.get("btnDisconnect").add(btnDisconnect);
		txtAddr.setText("ws://ubuntu.local:9090");
		btnDisconnect.setEnabled(false);
		btnConnect.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ros = new ROS(txtAddr.getText(), connStateListener);
			}
		});
		btnDisconnect.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				if(ros == null) return;
				ros.disconnect();
				ros = null;
			}
		});
		
		RootPanel.get("getTopics").add(btnGetTopics);
		RootPanel.get("getServices").add(btnGetServices);
		RootPanel.get("getParams").add(btnGetParams);
		btnGetTopics.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ros.getTopics(new Callback<List<ROS.Topic>, Void>() {
					public void onFailure(Void reason) {
					}

					public void onSuccess(List<ROS.Topic> result) {
						Window.alert(result.toString());
					}
				});
			}
		});
		btnGetServices.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ros.getServices(new Callback<List<ROS.Service>, Void>() {
					public void onFailure(Void reason) {
					}

					public void onSuccess(List<ROS.Service> result) {
						Window.alert(result.toString());
					}
				});
			}
		});
		btnGetParams.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ros.getParams(new Callback<List<ROS.Param>, Void>() {
					public void onFailure(Void reason) {
					}

					public void onSuccess(List<ROS.Param> result) {
						Window.alert(result.toString());
					}
				});
			}
		});
		
		RootPanel.get("topicName").add(txtTopicName);
		RootPanel.get("topicType").add(txtTopicType);
		RootPanel.get("topicPayload").add(txtTopicPayload);
		RootPanel.get("btnPublish").add(btnPublish);
		txtTopicName.setText("/cmd_vel");
		txtTopicType.setText("geometry_msgs/Twist");
		txtTopicPayload.setText(new Twist(new Vector3(1, 0, 0), new Vector3(0, 0, 0)).toJSON().toString());
		btnPublish.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ROS.Topic topic = ros.newTopic(txtTopicName.getText(), txtTopicType.getText());
				topic.publish(JSONParser.parseStrict(txtTopicPayload.getText()));
			}
		});
		
		RootPanel.get("topicName2").add(txtTopicNamePub);
		RootPanel.get("topicType2").add(txtTopicTypePub);
		RootPanel.get("btnSubscribe").add(btnSubscribe);
		RootPanel.get("btnUnsubscribe").add(btnUnsubscribe);
		txtTopicNamePub.setText("/tf");
		txtTopicTypePub.setText("tf/tfMessage");
		btnUnsubscribe.setEnabled(false);
		btnSubscribe.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ROS.Topic topic = ros.newTopic(txtTopicNamePub.getText(), txtTopicTypePub.getText());
				topic.subscribe(new ROS.MessageListener() {
					public void onMessage(JSONObject result) {
						log(result.toString());
					}
				});
				btnSubscribe.setEnabled(false);
				btnUnsubscribe.setEnabled(true);
			}
		});
		btnUnsubscribe.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ROS.Topic topic = ros.newTopic(txtTopicNamePub.getText(), txtTopicTypePub.getText());
				topic.unsubscribe();
				btnSubscribe.setEnabled(true);
				btnUnsubscribe.setEnabled(false);
			}
		});

		RootPanel.get("srvArg").add(txtServiceArgs);
		RootPanel.get("srvName").add(txtServiceName);
		RootPanel.get("srvType").add(txtServiceType);
		RootPanel.get("btnCall").add(btnCallService);
		RootPanel.get("reply").add(txtServiceResult);
		txtServiceArgs.setText("{\"a\": 2, \"b\": 5}");
		txtServiceName.setText("/mysrv");
		txtServiceType.setText("homework0/Sum");
		btnCallService.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				btnCallService.setEnabled(false);
				ROS.Service s = ros.newService(txtServiceName.getText(), txtServiceType.getText());
				s.callService(JSONParser.parseStrict(txtServiceArgs.getText()).isObject(), new ROS.MessageListener() {
					public void onMessage(JSONObject result) {
						txtServiceResult.setText(result.toString());
						btnCallService.setEnabled(true);
					}
				});
			}
		});
		
		RootPanel.get("paramName").add(txtParamName);
		RootPanel.get("paramValue").add(txtParamValue);
		RootPanel.get("btnSet").add(btnParamSet);
		RootPanel.get("btnGet").add(btnParamGet);
		txtParamName.setText("");
		txtParamValue.setText("");
		btnParamSet.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ROS.Param param = ros.newParam(txtParamName.getText());
				param.set(txtParamValue.getText());
			}
		});
		btnParamGet.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				ROS.Param param = ros.newParam(txtParamName.getText());
				param.get(new ROS.ValueListener<String>() {
					public void onValue(String value) {
						Window.alert(txtParamName.getText() + " := " + value);
					}
				});
			}
		});
	}
	
	private void log(String s) {
		RootPanel.get("log").add(new Label(s));
	}
}
