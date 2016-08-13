package org.eu.chege.socketclusterchat;


/**
 * Dariusz Krempa
 */
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.JSONValue;

import java.util.HashMap;
import java.util.Map;

import io.socketcluster.socketclusterandroidclient.SCSocketService;


public class MainActivity extends Activity {

    private boolean notifications;
    private boolean vibrate;
    private String ringtone;
    private static String TAG = "Activity";
    private SCSocketService scSocket;
    private Boolean bound = false;
    private String options;
    private boolean logout;
    private Intent scSocketService;
    private MessageReceiver messageMsgHandler;
    private SharedPreferences SP;
    private TextView textView;
    private ToggleButton toggleButton;
    private static String userName;
    private boolean connected;
    private ScrollView scrollview;
    private boolean ready;
    private static boolean exists = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String host = SP.getString("server_address", "");
        String port = SP.getString("server_port", "");
        String channelPrefix = SP.getString("channelPrefix", "");
        boolean secure = SP.getBoolean("secure", true);
        String query = SP.getString("query", "");
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
        userName = getIntent().getStringExtra("userName");
        messageMsgHandler = new MessageReceiver(this);
        textView = (TextView) findViewById(R.id.textView);
        final EditText edit = (EditText) findViewById(R.id.editText);
        Map map = new HashMap();

        toggleButton = (ToggleButton) findViewById(R.id.connected);
        Button exit = (Button) findViewById(R.id.exit);
        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout = true;
                exists = false;
                bound = false;
                scSocket.disconnect();
                connected = false;
                MyBroadcastReceiver.setService(null);
                messageMsgHandler.mNotificationManager.cancelAll();
                unbindService(conn);
                stopService(scSocketService);
                finish();
            }
        });

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(ready){
                    if(connected)
                        scSocket.disconnect();
                    else
                        scSocket.connect(options);
                }
            }
        });


        map.put("hostname", host);
        map.put("port", port);
        map.put("channelPrefix", channelPrefix);
        map.put("secure", secure);
        map.put("query", query);
        options = JSONValue.toJSONString(map);

        scrollview = ((ScrollView) findViewById(R.id.scrollView));

        edit.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    // Perform action on key press
                    Map map = new HashMap();
                    map.put("msg", edit.getText().toString());
                    map.put("sender", userName);
                    scSocket.publish("msg", JSONValue.toJSONString(map));
                    edit.setText("");
                    return true;
                }
                return false;
            }
        });
    }

    private ServiceConnection conn = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName component, IBinder binder){
            SCSocketService.SCSocketBinder scSocketBinder = (SCSocketService.SCSocketBinder) binder;
            scSocket = scSocketBinder.getBinder();
            scSocket.setDelegate(MainActivity.this);
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName component){
            bound = false;
        }
    };

    /**
     * Bind service is required to access methods in SCSocketService
     * startService is required, even if service is bound, to keep SCSocketCluster alive when activity isn't foreground app
     * this let to stay application connected to server and receive events and subscribed messages
     */
    @Override
    protected void onStart(){
        super.onStart();
        scSocketService = new Intent(this, SCSocketService.class);
        bindService(scSocketService, conn, Context.BIND_AUTO_CREATE);
        startService(scSocketService);

    }

    @Override
    protected void onStop(){
        super.onStop();
        if(bound){
            unbindService(conn);
            bound = false;
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(!exists) {
            LocalBroadcastManager.getInstance(this).registerReceiver(messageMsgHandler, new IntentFilter("io.socketcluster.eventsreceiver"));
            exists = true;
        }
        notifications = SP.getBoolean("notifications_new_message", false);
        vibrate = SP.getBoolean("notifications_new_message_vibrate", false);
        ringtone = SP.getString("notifications_new_message_ringtone", "");


    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
//        super.onBackPressed();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageMsgHandler);
    }

    /**
     * BroadcastReceiver to receive messages from SCSocketClusterService to handle events
     * Broadcast receiver can be changed or even implemented at new class but has to be to handle events from socketcluster client
     */
    class MessageReceiver extends MyBroadcastReceiver {

        private java.lang.String authToken;

        public MessageReceiver(Context context) {
            super(context);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String event = intent.getStringExtra("event");
            String data = intent.getStringExtra("data");
            handleEvents(event, data);
        }

        @Override
        public void handleEvents(String event, String data) {
            switch (event) {

                default:

                    break;

                case SCSocketService.EVENT_ON_READY:
                    textView.append("READY: "+userName+"\n");

                    ready = true;
                    if(null==socket&&!connected) {
                        scSocket.connect(options);
                        setService(scSocket);
                    }

                    Log.d(TAG, "ready");
                    break;

                case SCSocketService.EVENT_ON_CONNECT:
                    textView.append("Connected."+"\n");
                    scSocket.subscribe("msg");
                    toggleButton.setChecked(true);
                    connected = true;

                    Log.d(TAG, "connected: " + data);
                    break;

                case SCSocketService.EVENT_ON_DISCONNECT:
                    toggleButton.setChecked(false);
                    connected = false;

                    if (!logout)
                        scSocket.authenticate(authToken);

                    Log.d(TAG, "disconnected");
                    break;

                case SCSocketService.EVENT_ON_SUBSCRIBED_MESSAGE:

                    String msg = "";
                    String sender = "";
                    try {
                        JSONObject json = new JSONObject(data);
                        String dataMsg = json.getString("data");
                        dataMsg = (String) JSONValue.parse(dataMsg);
                        json = new JSONObject(dataMsg);
                        dataMsg = json.getString("data");
                        json = new JSONObject(dataMsg);

                        msg = json.getString("msg");
                        sender = json.getString("sender");
                    }catch(JSONException e){

                    }

                    newMsg(sender,msg);

                    if(notifications&&!sender.equals(userName)) {
                        String newmsg = getString(R.string.new_message);
                        if (vibrate)
                            mBuilder.setVibrate(new long[]{1500, 1500, 1500});

                        mBuilder.setContentTitle(newmsg);
                        mBuilder.setSound(Uri.parse(ringtone));
                        inboxStyle = new NotificationCompat.InboxStyle();

                        String[] events = new String[2];
                        events[0] = sender;
                        events[1] = msg;

                        for (int i=0; i < events.length; i++) {
                            inboxStyle.addLine(events[i]);
                        }

                        mBuilder.setStyle(inboxStyle);

                        notification();
                    }
                    Log.d(TAG, "subscribed message: " + data);
                    break;

                case SCSocketService.EVENT_ON_SUBSCRIBE:
                    Log.d(TAG, "subscribed message: " + data);
                    break;
            }
        }
    }

    private void newMsg(String sender, String msg){
        textView.append(sender+": "+msg+"\n");

        scrollview.post(new Runnable() {
            @Override
            public void run() {
                scrollview.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });

    }

}