package to.doc.android.ipv6config;

import java.net.Inet4Address;
import java.net.UnknownHostException;

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
import android.widget.Toast;

public class StartAtBootService extends Service {
	
	/** By adding a parameter with this name to the Intent that is starting 
	 * the service, specific behavior can be triggered depending on the 
	 * value of this parameter.
	 */
	public final static String SERVICE_COMMAND_PARAM = "command";
	/** Sending the "noop" command to the StartAtBootService will cause it to 
	 * only register for changes, but don't do anything else.
	 */
	public final static String SERVICE_COMMAND_NOOP = "noop";
	/** Forces a reload of network interface addresses. */
	public final static String SERVICE_COMMAND_RELOAD = "reload";
	
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
        registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}
 
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(IPv6Config.LOG_TAG, "StartAtBootService -- onStartCommand()");	        

		if (intent.getExtras().containsKey(SERVICE_COMMAND_PARAM) && 
			SERVICE_COMMAND_NOOP.equals(intent.getExtras().getString(SERVICE_COMMAND_NOOP))) {
			Log.w(IPv6Config.LOG_TAG, "StartAtBootService skipping all actions because noop command received via intent");
		}

		SharedPreferences prefsPrivate = getSharedPreferences(IPv6Config.PREFERENCES_STORE, Context.MODE_PRIVATE);
	        
		boolean autoStart = prefsPrivate.getBoolean(IPv6Config.PREFERENCE_AUTOSTART, false);
		boolean enablePrivacy = prefsPrivate.getBoolean(IPv6Config.PREFERENCE_ENABLE_PRIVACY, false);
		boolean enable6to4Tunnel = prefsPrivate.getBoolean(IPv6Config.PREFERENCE_CREATE_TUNNEL, false);

		boolean overrides = false;
		/* if the intent has extra parameters, these override the settings and 
		 * cause the service to perform its actions in any case (even if 
		 * autoStart is not set) 
		 */
		if (intent.getExtras().containsKey(IPv6Config.PREFERENCE_ENABLE_PRIVACY)) {
			enablePrivacy = intent.getExtras().getBoolean(IPv6Config.PREFERENCE_ENABLE_PRIVACY, enablePrivacy);
			overrides = true;
		}
		if (intent.getExtras().containsKey(IPv6Config.PREFERENCE_CREATE_TUNNEL)) {
			enable6to4Tunnel = intent.getExtras().getBoolean(IPv6Config.PREFERENCE_CREATE_TUNNEL, enable6to4Tunnel);
			overrides = true;
		}

		boolean reload = false;
		if (intent.getExtras().containsKey(SERVICE_COMMAND_PARAM))
			reload = SERVICE_COMMAND_RELOAD.equals(intent.getExtras().getString(SERVICE_COMMAND_NOOP));
	        
		Log.w(IPv6Config.LOG_TAG, "Set to autostart: " + autoStart);
		Log.w(IPv6Config.LOG_TAG, "Set to enable privacy: " + enablePrivacy);
		Log.w(IPv6Config.LOG_TAG, "Set to create 6to4 tunnel: " + enable6to4Tunnel);
		Log.w(IPv6Config.LOG_TAG, "Forcing address reload: " + reload);
		Log.w(IPv6Config.LOG_TAG, "Overrides taken from intent: " + overrides);

		if (autoStart || overrides || reload) {
			Log.w(IPv6Config.LOG_TAG, "Now enabling address privacy on all currently known interfaces, this might take a few seconds...");
				// only force reloading addresses when we enable privacy, not when we explicitly disable it
				applySettingsWithGuiFeedback(getApplicationContext(), 
	        			enablePrivacy, reload, enable6to4Tunnel);
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


    private static void applySettingsWithGuiFeedback(Context context, 
    		boolean enablePrivacy, boolean forceReload,
    		boolean enable6to4Tunnel) {
    	if (LinuxIPCommandHelper.enableIPv6AddressPrivacy(enablePrivacy, forceReload))
		    Toast.makeText(context, 
		    		enablePrivacy ? context.getString(R.string.toastEnableSuccess) : context.getString(R.string.toastDisableSuccess), 
	        		Toast.LENGTH_LONG).show();
		else
		    Toast.makeText(context, 
		    		enablePrivacy ? context.getString(R.string.toastEnableFailure) : context.getString(R.string.toastDisableFailure),
	        		Toast.LENGTH_LONG).show();

    	if (enable6to4Tunnel) {
			// determine outbound IPv4 address based on routes
			Inet4Address outboundIPv4Addr = LinuxIPCommandHelper.getOutboundIPv4Address();
			// determine outbound IPv4 address as seen from the outside
    		String globalIPv4AddrStr = IPv6AddressesHelper.getOutboundIPAddress(false);
    		Inet4Address globalIPv4Addr;
			try {
				globalIPv4Addr = (Inet4Address) Inet4Address.getByName(globalIPv4AddrStr);
			} catch (UnknownHostException e) {
				Log.w(IPv6Config.LOG_TAG, "Unable to parse globally visible IPv4 address '" +
						globalIPv4AddrStr + "', probably unable to contact resolver server", e);
				globalIPv4Addr = null;
			} catch (ClassCastException e) {
				Log.e(IPv6Config.LOG_TAG, "Unable to parse globally visible IPv4 address '" +
						globalIPv4AddrStr + "', unknown reason", e);
				globalIPv4Addr = null;
			}
			
				// check if we should create a tunnel now (i.e. if there is any IPv6 default route)
			if (! LinuxIPCommandHelper.existsIPv6DefaultRoute() &&
				// check if we could create a tunnel now (i.e. if local and global IPv4 addresses match)
				globalIPv4Addr != null && outboundIPv4Addr.equals(globalIPv4Addr)) {
				
				// both yes: do it. first delete tunnel if it exists (if it doesn't, don't mind)
				LinuxIPCommandHelper.deleteTunnelInterface(IPv6AddressesHelper.IPv6_6to4_TUNNEL_INTERFACE_NAME);

				// then create tunnel
				if (LinuxIPCommandHelper.create6to4TunnelInterface(
						IPv6AddressesHelper.IPv6_6to4_TUNNEL_INTERFACE_NAME, 
						outboundIPv4Addr, 
						IPv6AddressesHelper.compute6to4Prefix(outboundIPv4Addr), 0))
				    Toast.makeText(context, 
				    		context.getString(R.string.toast6to4Success), 
			        		Toast.LENGTH_LONG).show();
				else
				    Toast.makeText(context, 
				    		context.getString(R.string.toast6to4Failure), 
			        		Toast.LENGTH_LONG).show();
			}
			else if (globalIPv4Addr == null || ! outboundIPv4Addr.equals(globalIPv4Addr)) {
			    Toast.makeText(context, 
			    		context.getString(R.string.toast6to4AddressMismatch), 
		        		Toast.LENGTH_LONG).show();
			}
		}
    }
}
