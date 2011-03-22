package to.doc.android.ipv6config;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
 
public class StartAtBootServiceReceiver extends BroadcastReceiver 
{
	@Override
	public void onReceive(Context context, Intent intent) 
	{
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			Intent i = new Intent(context, StartAtBootService.class);
			//i.setAction("to.doc.android.StartAtBootService");
			context.startService(i);

		
			i = new Intent(context, IPv6Config.class);
			context.startService(i);
		}
	}
}
