package to.doc.android.ipv6privacy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
 
public class StartAtBootServiceReceiver extends BroadcastReceiver 
{
	@Override
	public void onReceive(Context context, Intent intent) 
	{
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			Intent i = new Intent();
			i.setAction("com.example.ssab.StartAtBootService");
			context.startService(i);
		}
	}
}
