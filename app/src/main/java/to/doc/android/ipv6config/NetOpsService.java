package to.doc.android.ipv6config;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

/** This class implements the heavy lifting of network operations: it queries 
 * the current network status and will execute the root commands to change 
 * system settings. It is implemented as an IntentService because network 
 * operations and root (SU) operations may take some time.
 * 
 * Note that it no longer listens to network change events (it no longer 
 * registers a BroadcastReceiver), because it seems that IntentServices cannot
 * correctly receive these events. The new ConnectivityChangeReceiver is
 * registered in Android Manifest to do that now.
 * 
 * @author René Mayrhofer
 */
public class NetOpsService extends IntentService {
	/** By adding a parameter with this name to the Intent that is starting 
	 * the service, specific behavior can be triggered depending on the 
	 * value of this parameter.
	 */
	public final static String SERVICE_COMMAND_PARAM = "command";
	/** Forces a reload of network interface addresses. */
	public final static String SERVICE_COMMAND_RELOAD = "reload";
	
	/** Need a handler for displaying toast messages. */
	private Handler toastHandler;
	
	/** Simple helper function for displaying a toast message in the correct
	 * (main UI) thread. */
	private void displayToast(final String msg) {
		toastHandler.post(new Runnable() {  
	        @Override  
	        public void run() {  	   
	        	Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); 
            }
		});
	}
	
	public NetOpsService() {
		super("IPv6Config-NetOpsService");
		toastHandler = new Handler();
		setIntentRedelivery(true);	// make sure that it is re-executed if the thread dies (cf. START_STICKY for service)
	}
	
	@Override
	protected void onHandleIntent (Intent intent) {
		Log.v(Constants.LOG_TAG, "NetOpsService.onHandleIntent starting");	        

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
	    			displayToast(enablePrivacy ? getApplicationContext().getString(R.string.toastEnableSuccess) : getApplicationContext().getString(R.string.toastDisableSuccess));
	    	}
			else {
				displayToast(enablePrivacy ? getApplicationContext().getString(R.string.toastEnableFailure) : getApplicationContext().getString(R.string.toastDisableFailure));
			}
	    	
	    	if (enable6to4Tunnel)
	    		create6to4Tunnel(getApplicationContext(), force6to4Tunnel, displayNotifications);
		}
		// as IntentService, we just register setIntentRedelivery(true) in the constructor instead of returning START_STICKY
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
	private boolean is6to4TunnelPossible(Inet4Address outboundIPv4Addr, boolean force6to4Tunnel) {
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
	private boolean create6to4Tunnel(Context context, boolean force6to4Tunnel, boolean displayNotifications) {
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
				displayToast(context.getString(R.string.toast6to4AddressMismatch));
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
					displayToast(context.getString(R.string.toast6to4Success));
				return true;
			}
			else {
				if (displayNotifications)
					displayToast(context.getString(R.string.toast6to4Failure));
				return false;
			}
		}
	}
}
