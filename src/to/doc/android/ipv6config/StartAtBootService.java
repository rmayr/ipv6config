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
        	ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        	NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        	
        	if (networkInfo == null) {
        		Log.e(Constants.LOG_TAG, "Cannot cat network info, something is seriously wrong here.");
        		return;
        	}
        	
            if (networkInfo.isConnected()) {
    			Log.i(Constants.LOG_TAG, "Network state change: " + networkInfo.getTypeName() + " connected, re-evaluating 6to4 tunnel configuration");
    			SharedPreferences prefsPrivate = getSharedPreferences(Constants.PREFERENCES_STORE, Context.MODE_PRIVATE);
    			boolean enable6to4Tunnel = prefsPrivate.getBoolean(Constants.PREFERENCE_CREATE_TUNNEL, false);
    			boolean force6to4Tunnel = prefsPrivate.getBoolean(Constants.PREFERENCE_FORCE_TUNNEL, false);
    			boolean displayNotifications = prefsPrivate.getBoolean(Constants.PREFERENCE_DISPLAY_NOTIFICATIONS, true);
    			Log.d(Constants.LOG_TAG, "Set to create 6to4 tunnel: " + enable6to4Tunnel);
    			Log.d(Constants.LOG_TAG, "Set to force 6to4 tunnel: " + force6to4Tunnel);
    	    	if (enable6to4Tunnel) 
    	    		create6to4Tunnel(getApplicationContext(), force6to4Tunnel, displayNotifications);
            } else {
                Log.i("APP_TAG", networkInfo.getTypeName() + " - DISCONNECTED");
    			Log.i(Constants.LOG_TAG, "Network state change: disconnected, deconfiguring 6to4 tunnel");
				LinuxIPCommandHelper.deleteTunnelInterface(IPv6AddressesHelper.IPv6_6to4_TUNNEL_INTERFACE_NAME);
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
		Log.v(Constants.LOG_TAG, "StartAtBootService Created");
		
		// register for receiving connectivity change events
		receiver = new ConnectivityReceiver();
        registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}
 
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(Constants.LOG_TAG, "StartAtBootService -- onStartCommand()");	        

		if (intent != null && intent.getExtras() != null && 
				intent.getExtras().containsKey(SERVICE_COMMAND_PARAM) && 
				SERVICE_COMMAND_NOOP.equals(intent.getExtras().getString(SERVICE_COMMAND_PARAM))) {
			Log.w(Constants.LOG_TAG, "StartAtBootService skipping all actions because noop command received via intent");
			return Service.START_STICKY;
		}

		SharedPreferences prefsPrivate = getSharedPreferences(Constants.PREFERENCES_STORE, Context.MODE_PRIVATE);
	        
		boolean autoStart = prefsPrivate.getBoolean(Constants.PREFERENCE_AUTOSTART, false);
		boolean displayNotifications = prefsPrivate.getBoolean(Constants.PREFERENCE_DISPLAY_NOTIFICATIONS, true);
		boolean enablePrivacy = prefsPrivate.getBoolean(Constants.PREFERENCE_ENABLE_PRIVACY, false);
		boolean enable6to4Tunnel = prefsPrivate.getBoolean(Constants.PREFERENCE_CREATE_TUNNEL, false);
		boolean force6to4Tunnel = prefsPrivate.getBoolean(Constants.PREFERENCE_FORCE_TUNNEL, false);

		boolean overrides = false;
		/* if the intent has extra parameters, these override the settings and 
		 * cause the service to perform its actions in any case (even if 
		 * autoStart is not set) 
		 */
		if (intent != null && intent.getExtras() != null && 
				intent.getExtras().containsKey(Constants.PREFERENCE_ENABLE_PRIVACY)) {
			enablePrivacy = intent.getExtras().getBoolean(Constants.PREFERENCE_ENABLE_PRIVACY, enablePrivacy);
			overrides = true;
		}
		if (intent != null && intent.getExtras() != null && 
				intent.getExtras().containsKey(Constants.PREFERENCE_CREATE_TUNNEL)) {
			enable6to4Tunnel = intent.getExtras().getBoolean(Constants.PREFERENCE_CREATE_TUNNEL, enable6to4Tunnel);
			overrides = true;
		}
		if (intent != null && intent.getExtras() != null && 
				intent.getExtras().containsKey(Constants.PREFERENCE_FORCE_TUNNEL)) {
			force6to4Tunnel = intent.getExtras().getBoolean(Constants.PREFERENCE_FORCE_TUNNEL, force6to4Tunnel);
			overrides = true;
		}

		boolean reload = false;
		if (intent != null && intent.getExtras() != null && 
				intent.getExtras().containsKey(SERVICE_COMMAND_PARAM))
			reload = SERVICE_COMMAND_RELOAD.equals(intent.getExtras().getString(SERVICE_COMMAND_PARAM));
	        
		Log.i(Constants.LOG_TAG, "Set to autostart: " + autoStart);
		Log.i(Constants.LOG_TAG, "Set to enable privacy: " + enablePrivacy);
		Log.i(Constants.LOG_TAG, "Set to create 6to4 tunnel: " + enable6to4Tunnel);
		Log.i(Constants.LOG_TAG, "Set to force 6to4 tunnel: " + force6to4Tunnel);
		Log.i(Constants.LOG_TAG, "Forcing address reload: " + reload);
		Log.i(Constants.LOG_TAG, "Overrides taken from intent: " + overrides);

		if (autoStart || overrides || reload) {
			Log.w(Constants.LOG_TAG, "Now enabling address privacy on all currently known interfaces, this might take a few seconds...");
	    	if (LinuxIPCommandHelper.enableIPv6AddressPrivacy(enablePrivacy, reload)) {
	    		if (displayNotifications)
	    			Toast.makeText(getApplicationContext(), 
			    		enablePrivacy ? getApplicationContext().getString(R.string.toastEnableSuccess) : getApplicationContext().getString(R.string.toastDisableSuccess), 
		        		Toast.LENGTH_LONG).show();
	    	}
			else {
			    Toast.makeText(getApplicationContext(), 
			    		enablePrivacy ? getApplicationContext().getString(R.string.toastEnableFailure) : getApplicationContext().getString(R.string.toastDisableFailure),
		        		Toast.LENGTH_LONG).show();
			}
	    	
	    	if (enable6to4Tunnel)
	    		create6to4Tunnel(getApplicationContext(), force6to4Tunnel, displayNotifications);
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
		Log.v(Constants.LOG_TAG, "StartAtBootService -- onStart()");
    		
		onStartCommand(intent, 0, startId);
	}
 
	@Override
	public void onDestroy() {
		Log.v(Constants.LOG_TAG, "StartAtBootService Destroyed");
	}

	/** Helper method to determine of a 6to4 tunnel can be established, i.e. if
	 * the internally visible 6to4 address matches the one visible globally.
	 * 
	 * @param outboundIPv4Addr The internally visible, outbound IPv4 address
	 *        associated with the local default route.
	 * @param force6to4Tunnel If set to true, this method will always return true.
	 */
	private static boolean is6to4TunnelPossible(Inet4Address outboundIPv4Addr, boolean force6to4Tunnel) {
		if (force6to4Tunnel) return true;
		if (outboundIPv4Addr == null) {
			Log.w(Constants.LOG_TAG, "Unknown IPv4 outbound addresss, cannot establish 6to4 tunnel");
			return false;
		}
		
    	Log.d(Constants.LOG_TAG, "test10");
		
		// determine outbound IPv4 address as seen from the outside
		String globalIPv4AddrStr = IPv6AddressesHelper.getOutboundIPAddress(false);
		Inet4Address globalIPv4Addr = null;
    	Log.d(Constants.LOG_TAG, "test11");
		try {
	    	Log.d(Constants.LOG_TAG, "test12");
			if (globalIPv4AddrStr != null)
				globalIPv4Addr = (Inet4Address) Inet4Address.getByName(globalIPv4AddrStr);
	    	Log.d(Constants.LOG_TAG, "test13");
		} catch (UnknownHostException e) {
			Log.w(Constants.LOG_TAG, "Unable to parse globally visible IPv4 address '" +
					globalIPv4AddrStr + "', probably unable to contact resolver server", e);
			globalIPv4Addr = null;
		} catch (ClassCastException e) {
			Log.e(Constants.LOG_TAG, "Unable to parse globally visible IPv4 address '" +
					globalIPv4AddrStr + "', unknown reason", e);
			globalIPv4Addr = null;
		}
    	Log.d(Constants.LOG_TAG, "test15");
		// check if we could create a tunnel now (i.e. if local and global IPv4 addresses match)
		if (globalIPv4Addr != null && outboundIPv4Addr.equals(globalIPv4Addr))
			return true;
		else
			return false;
	}
	
	/** Helper method to create a 6to4 tunnel.
	 * 
	 * @param force6to4Tunnel If set to true, tunnel creation will be attempted
	 *        even if the IPv4 addresses do not indicate it possible. 
	 * @return true when a tunnel interface was established, false otherwise.
	 */
	private static boolean create6to4Tunnel(Context context, boolean force6to4Tunnel, boolean displayNotifications) {
		// first delete tunnel if it exists (if it doesn't, don't mind)
		LinuxIPCommandHelper.deleteTunnelInterface(IPv6AddressesHelper.IPv6_6to4_TUNNEL_INTERFACE_NAME);

		// check if we should create a tunnel now (i.e. if there is any IPv6 default route)
		if (LinuxIPCommandHelper.existsIPv6DefaultRoute()) {
			Log.i(Constants.LOG_TAG, "Not creating a 6to4 tunnel because an IPv6 default route already exists.");
			return false;
		}
		
		// determine outbound IPv4 address based on routes
		Inet4Address outboundIPv4Addr = LinuxIPCommandHelper.getOutboundIPv4Address();
    	Log.d(Constants.LOG_TAG, "test3");
		
		if (! is6to4TunnelPossible(outboundIPv4Addr, force6to4Tunnel)) {
	    	Log.d(Constants.LOG_TAG, "test4");

			if (displayNotifications)
				Toast.makeText(context,	context.getString(R.string.toast6to4AddressMismatch), 
	        		Toast.LENGTH_LONG).show();
		    return false;
		}
		else {
	    	Log.d(Constants.LOG_TAG, "test5");
			
			String v6prefix = IPv6AddressesHelper.compute6to4Prefix(outboundIPv4Addr); 
			Log.i(Constants.LOG_TAG, "Creating IPv6 tunnel via output IPv4 address " +
					outboundIPv4Addr + ": IPv6 prefix is now " + v6prefix);
			// finally create tunnel
			if (LinuxIPCommandHelper.create6to4TunnelInterface(
					IPv6AddressesHelper.IPv6_6to4_TUNNEL_INTERFACE_NAME, 
					outboundIPv4Addr, v6prefix, 0)) {
				if (displayNotifications)
					Toast.makeText(context,	context.getString(R.string.toast6to4Success), 
		        		Toast.LENGTH_LONG).show();
				return true;
			}
			else {
				if (displayNotifications)
					Toast.makeText(context,	context.getString(R.string.toast6to4Failure), 
		        		Toast.LENGTH_LONG).show();
				return false;
			}
		}
	}
}
