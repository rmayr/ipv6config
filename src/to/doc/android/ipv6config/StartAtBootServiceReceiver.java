package to.doc.android.ipv6config;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
 
/** This is a separate mini-class to simply start the two services 
 * (NetOpsService and NetChangeListenerService) at device bootup. It is not
 * an inner class because it is referenced from the Android Manifest and not
 * registered programmatically as a BroadCastReceiver.
 * 
 * @author René Mayrhofer
 */
public class StartAtBootServiceReceiver extends BroadcastReceiver 
{
	@Override
	public void onReceive(Context context, Intent intent) 
	{
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			/* Do the major processing in a background service that will 
			 * terminate after it's done so as not to block the main thread.
			 */
			context.startService(new Intent(context, NetOpsService.class));
		}
	}
}
