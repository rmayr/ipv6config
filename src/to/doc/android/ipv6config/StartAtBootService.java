package to.doc.android.ipv6config;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.util.Log;

public class StartAtBootService extends Service 
{
	/** This helper class is used for receiving network connectivity change events. */
	private class ConnectivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        	NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        	if (info != null) {
        		switch (info.getState()) {
        		case CONNECTED:
        			Log.i(IPv6Config.LOG_TAG, "Network state change: connected, re-evaluating 6to4 tunnel configuration");
        			break;
        		case DISCONNECTED:
        			Log.i(IPv6Config.LOG_TAG, "Network state change: disconnected, deconfiguring 6to4 tunnel");
        			break;
        		}
        	}
        }
	}

	/** The reference to our network connectivity change listener. */
	private ConnectivityReceiver receiver = null;
	
	/** This service doesn't offer an interface. */
	public IBinder onBind(Intent intent) {
		return null;
	}
 
	@Override
	public void onCreate() {
		Log.v(IPv6Config.LOG_TAG, "StartAtBootService Created");
		
		// register for receiving connectivity change events
		receiver = new ConnectivityReceiver();
        registerReceiver(receiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}
 
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(IPv6Config.LOG_TAG, "StartAtBootService -- onStartCommand()");	        

		SharedPreferences prefsPrivate = getSharedPreferences(IPv6Config.PREFERENCES_STORE, Context.MODE_PRIVATE);
	        
		boolean autoStart = prefsPrivate.getBoolean(IPv6Config.PREFERENCE_AUTOSTART, false);
		boolean enablePrivacy = prefsPrivate.getBoolean(IPv6Config.PREFERENCE_ENABLE_PRIVACY, false);
		boolean enable6to4Tunnel = prefsPrivate.getBoolean(IPv6Config.PREFERENCE_CREATE_TUNNEL, false);
	        
		Log.w(IPv6Config.LOG_TAG, "Set to autostart: " + autoStart);
		Log.w(IPv6Config.LOG_TAG, "Set to enable privacy: " + enablePrivacy);

		if (autoStart) {
			Log.w(IPv6Config.LOG_TAG, "Now enabling address privacy on all currently known interfaces, this might take a few seconds...");
				// only force reloading addresses when we enable privacy, not when we explicitly disable it
				IPv6Config.applySettingsWithGuiFeedback(getApplicationContext(), 
	        			enablePrivacy, enablePrivacy, enable6to4Tunnel);
		}

		// service needs to be sticky to listen to connection change events
		return Service.START_STICKY;
	}
 
	/*
	 * In Android 2.0 and later, onStart() is depreciated.  Use
	 * onStartCommand() instead, or compile against API Level 5 and
	 * use both.
	 * http://android-developers.blogspot.com/2010/02/service-api-changes-starting-with.html
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		Log.v(IPv6Config.LOG_TAG, "StartAtBootService -- onStart()");
    		
		onStartCommand(intent, 0, startId);
	}
 
	@Override
	public void onDestroy() {
		Log.v(IPv6Config.LOG_TAG, "StartAtBootService Destroyed");
	}
}
