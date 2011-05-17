package to.doc.android.ipv6config;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.BitSet;
import java.util.Date;
import java.util.StringTokenizer;

/** This is a small helper class providing various helper methods for dealing
 * with strings. There should be no other dependency than on string 
 * manipulation!
 * @author Rene Mayrhofer, Andreas Wöckl, Richard Leitner
 */
public class StringHelper {
	/**returns true if a string is null or ""
	 * @param str	the string to check
	 * @return true if null or ""
	 */
	public static boolean isBlank(String str) {
		if (str == null) {
			return true;
		}
		if (str.trim().length() == 0) {
			return true;
		}
		if (str.equals("")) {
			return true;
		}
		if (str.equals("null") || str.equals("NULL"))  {
			return true;
		}
		return false;
	}

	/** converts a string that contains a number of bytes to a string that contains the corresponding number of MBs 
	 * @param bytes String that contains a amount of bytes to convert to 
	 * @param convertTo String that contains the unit to convert to (KB, MB, GB)
	 * @return The amount of bytes converted to the given amount in a String.
	 */
	public static String convertBytesTo(String bytes, String convertTo) {
		Long bytesInLong = new Long(bytes.trim());
		long bytesToCalculate = bytesInLong.longValue();
		if (convertTo.equalsIgnoreCase("KB")) {
			bytesToCalculate = bytesToCalculate / 1024;
		}
		else if (convertTo.equalsIgnoreCase("MB")) {
			bytesToCalculate = bytesToCalculate / 1048576;
		}
		else if (convertTo.equalsIgnoreCase("GB")) {
			bytesToCalculate = bytesToCalculate / 1073741824;
		}
		return new String(Long.toString(bytesToCalculate));
	}

	/** converts a string that contains a number of (Kilo-, Mega-, Giga-) bytes to a string that contains the corresponding number of bytes 
	 * @param value String that contains the value to convert to 
	 * @param convertFrom String that contains the unit to convert from (KB, MB, GB)
	 * @return The amount of bytes converted .
	 */
	public static String convertToBytes(String value, String convertFrom) {
		Long bytesInLong = new Long(value);
		long bytesToCalculate = bytesInLong.longValue();
		if (convertFrom.equalsIgnoreCase("KB")) {
			bytesToCalculate = bytesToCalculate * 1024;
		}
		else if (convertFrom.equalsIgnoreCase("MB")) {
			bytesToCalculate = bytesToCalculate * 1048576;
		}
		else if (convertFrom.equalsIgnoreCase("GB")) {
			bytesToCalculate = bytesToCalculate * 1073741824;
		}
		return new String(Long.toString(bytesToCalculate));
	}

	/**returns a specified token
	 * @param str	the full string
	 * @param delim	the delimiter
	 * @param token	the index of the token
	 * @return	specified token
	 */	
	public static String getToken(String str, String delim, int token) {
		StringTokenizer tok = new StringTokenizer(str, delim);
		String ret = null;
		int i = 0;
		while ((tok.hasMoreElements()) && (i < token)) {
			ret = tok.nextToken();
			i++;
		}
		if (i == token) {
			return ret;
		} else {
			return null;
		}
	}

	/**
	 * converts a Linux/Unix timestamp that is given as parameter to a human readable time
	 */
	public static String convertTimestamp(String stmp) {
		Long time = new Long(stmp);
		Date dt = new Date(time.longValue());
		//SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd HH:mm" );
		SimpleDateFormat df = new SimpleDateFormat( "dd.MM.yyyy HH:mm" );
		return df.format( dt );
	}

	/**
	 * converts a time string into a Unix time stamp. The format of the time string is 
	 * <code>dd.MM.yyyy HH:mm</code>.
	 * @param timeStr The time string that should be converted.
	 * @return The Unix timestamp that represents the date.
	 */
	public static long convertStringToTimestamp(String timeStr) {
		SimpleDateFormat formatter = new SimpleDateFormat ("dd.MM.yyyy HH:mm");
		// Parse the string into a Date.
		ParsePosition pos = new ParsePosition(0);
		Date date = formatter.parse(timeStr, pos);		
		return date.getTime()/1000;
	}

	/**
	 * Converts an integer value into a bit representation and returns a BitSet.
	 * @param curInt
	 * @return
	 */
	public static BitSet getBitSetFromInt(int curInt) {
		byte tmp = (byte)curInt;
		BitSet bb = new BitSet();
		for(int i = 7; i >=0; i--)
			if(((1 << i) &  tmp) != 0)
				bb.set(i);
		    else
		    	bb.clear(i);
		return bb;
	}

	public static String formatDuration(long lMs) {
	    // Validate
	    if (lMs > 0L) {
	            // -- Declare variables
	            String strDays;
	            String strHours;
	            String strMinutes;
	            String strSeconds;
	            //String strMillisecs; // not used ...
	            long lRest;
	
	            // -- Find values
	            // -- -- Days
	            strDays = String.valueOf(lMs / 86400000L);
	            lRest = lMs % 86400000L;
	            // -- -- Hours
	            strHours = String.valueOf(lRest / 3600000L);
	            lRest %= 3600000L;
	            // -- -- Minutes
	            strMinutes = String.valueOf(lRest / 60000L);
	            lRest %= 60000L;
	            // -- -- Seconds
	            strSeconds = String.valueOf(lRest / 1000L);
	            lRest %= 1000L;
	            // -- -- Milliseconds
	            //strMillisecs = String.valueOf(lRest); // not used ...
	            
	        return (new Integer(strDays).intValue() != 0 ? strDays + " days " : "") +
	        	strHours + ":" + strMinutes + ":" + strSeconds;
	    }
	    else
	    	return null;
	}

	/**
	 * This method converts a hexadecimal string to an integer value
	 * @param hexadecimal string like 0x7
	 * @param alwaysAssumeHex If true, interpret the String as hexadecimal even if not prefixed with "0x".
	 * @return integer value of the hexadecimal value
	*/ 
	public static int convertHexStringToInt(String hexString, boolean alwaysAssumeHex) 
	{	 
		try
		{
			int value;
			
			if (hexString.startsWith("0x")) { 
				hexString = hexString.substring(hexString.indexOf("x")+1); 
				value = Integer.parseInt(hexString, 16);
			}
			else if (alwaysAssumeHex)
				value = Integer.parseInt(hexString, 16);
			else
				value = Integer.parseInt(hexString, 10);
			
			return value;	
		}
		catch(Exception ex)
		{
			return -1;
		}
	}

	public static String convertIntToHex(int i) {
		String value = "0x" + Integer.toHexString(i);
		return value;
	}
	
/*	public static void debuglogWithStackTrace(Logger logger, String msg, Exception e) {
		  String stackTrace = "";
		  if (msg != null)
		   stackTrace = msg + "\n";
		  
		  if (logger.isDebugEnabled()) {
		   for (int j=0; j<e.getStackTrace().length; j++)
		    stackTrace += e.getStackTrace()[j].toString() + "\n";
		   logger.debug(stackTrace);
		  }		
	}*/
}
