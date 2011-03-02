package to.doc.android.ipv6privacy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
 
public class StartAtBootService extends Service 
{
	    public IBinder onBind(Intent intent)
	    {
	    	return null;
	    }
 
	    @Override
	    public void onCreate() 
	    {
	    	Log.v("StartServiceAtBoot", "StartAtBootService Created");
	    }
 
	    @Override
	    public int onStartCommand(Intent intent, int flags, int startId) 
	    {
	    	Log.v("StartServiceAtBoot", "StartAtBootService -- onStartCommand()");	        
 
	        // We want this service to continue running until it is explicitly
	        // stopped, so return sticky.
	        return START_STICKY;
	    }
 
	    /*
	     * In Android 2.0 and later, onStart() is depreciated.  Use
	     * onStartCommand() instead, or compile against API Level 5 and
	     * use both.
	     * http://android-developers.blogspot.com/2010/02/service-api-changes-starting-with.html
	    	@Override
	    	public void onStart(Intent intent, int startId)
	    	{
	    		Log.v("StartServiceAtBoot", "StartAtBootService -- onStart()");	        
	    	}
	     */
 
	    @Override
	    public void onDestroy() 
	    {
	    	Log.v("StartServiceAtBoot", "StartAtBootService Destroyed");
	    }
}
