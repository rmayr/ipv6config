package to.doc.android.ipv6config;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/** This helper class is used for receiving network connectivity change events. 
 * It then notifies the NetOpsService to react to any of those events in a 
 * background thread.
 * 
 * @author René Mayrhofer
 */
public class ConnectivityChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
    	ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    	NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
    	
    	if (networkInfo == null) {
    		Log.e(Constants.LOG_TAG, "Cannot get network info, something is seriously wrong here.");
    		return;
    	}
    	
        if (networkInfo.isConnected()) {
			Log.i(Constants.LOG_TAG, "Network state change: " + networkInfo.getTypeName() + " connected, triggering background service to re-evaluate 6to4 tunnel configuration");
			context.startService(new Intent(context, NetOpsService.class));
        } else {
            Log.i("APP_TAG", networkInfo.getTypeName() + " - DISCONNECTED");
			Log.i(Constants.LOG_TAG, "Network state change: disconnected, deconfiguring 6to4 tunnel");
			LinuxIPCommandHelper.deleteTunnelInterface(IPv6AddressesHelper.IPv6_6to4_TUNNEL_INTERFACE_NAME);
        }
    }
}
