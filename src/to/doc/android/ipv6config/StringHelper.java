package to.doc.android.ipv6config;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.BitSet;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;


/** This is a small helper class providing various helper methods for dealing
 * with strings. There should be no other dependency than on string 
 * manipulation!
 * @author Rene Mayrhofer
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

	/**cuts an integer out of a string
	 * @param str the string with the integer
	 * @param pos	the position the integer starts
	 * @return  cutted integer
	 */
	public static int cutIntOfString(String str, int pos) {
		Integer anInt;
		anInt = new Integer(str.substring(pos, str.length()));
		return anInt.intValue();
	}

	/**returns the index of an ElemenGroup element (f.e: nameserver_1_1 -> 1 is returned)
	 * this method only works if there are less than 10 element groups - therfore in 99,9999999999%
	 * @param elementName	the name of the element
	 * @return index of the element
	 */
	public static int getElementIndex(String elementName) {
		if (elementName.lastIndexOf("_")!=-1) {
			return cutIntOfString(elementName,elementName.lastIndexOf("_")+1);
		} else {
			return -1;
		}
	}

	/** create a list with a give String divided by a delimiter
	 * for example if given a "test;test2" it will return a list with those 2 entries
	 */
	public static final LinkedList<String> createListOfString(String str, String delim) {
		StringTokenizer tok = new StringTokenizer(str,delim);
		LinkedList<String> ret = new LinkedList<String>();
		while (tok.hasMoreTokens()) {
			ret.add(tok.nextToken().toString());
		}
		return ret;
	}

	/**replaces the ocurence of find with replacement
	 * 
	 * */
	public static String replaceAllWords(String original, String find, String replacement, String delims) {
	    String result = "";
	    StringTokenizer st = new StringTokenizer(original, delims, true);
	    while (st.hasMoreTokens()) {
	        String w = st.nextToken();
	        if (w.equals(find)) {
	            result = result + replacement;
	        } else {
	            result = result + w;
	        }
	    }
	    return result;
	}

	/**searches for a changed entry - checks the index to assign which entry was which index+
		 * values have to come like that: 1;originValue;newValue
		 * Normally the value "1;originValue" is returned of getValue(ident) to have an identifier that gets
		 * deleted
		 * 
		 * @param originIndex	origin index of enry
		 * @param originEntry	origin entry
		 * @param list	list with (perhaps) changed entries
		 * @param devider devider to get the values 
		 * @return	null if not changed -> otherwise changed entry
		 */
		public static String getNewChangedEntry(String originIndex, String originEntry, LinkedList<?> list, String devider) {
	//		logger.debug("SEARCHING FOR NEW CHANGED ENTRY for origin Entry " + originEntry + " originINdex " + originIndex);
			for (int i=0; i<list.size(); i++) {
				//logger.debug("entry is " + list.get(i));
				String str = list.get(i).toString();
				String newIndex = getToken(str,devider,1);
				String newEntry = getToken(str,devider,3);
				//logger.debug("str is " + str + " originIndex is " + originIndex + " newEntry " + newEntry + " originEntry " + originEntry);
				if (newIndex.equals(originIndex)) {
					if (!originEntry.equals(newEntry)) {
						return newEntry;
					}
				}
			}
			
			return null;
		}

	/**cut out a char of a string
	 * 
	 * @param str	the string to cut out a char
	 * @param c	char to cut out
	 * @return
	 */
	public static String cutOutChar(String str, char c) {
		return str.replace(c,' ').trim();
	}

	/**
	 * cut out the char at the given position
	 * @param str the string to cut out a char
	 * @param pos the position to cut out the char
	 * @return the string without the char
	 */
	public static String cutOutCharAtPos(String str, int pos) {
		return str.substring(0, pos) + str.substring(pos+1);
	}

	/**creates a value range with a given list 
	 * 
	 * @param list
	 */
	public static String createValueRange(LinkedList<?> list) {
		String retVal = "";
		for (int i=0; i<list.size(); i++) {
			retVal += "<option value=\"" + list.get(i) + "\">" + list.get(i) + "</option>";
		}
		return retVal;			
	}

	/**creates a value range for a time (Every hour) */
	public static String createTimeRange() {
		LinkedList<String> list = new LinkedList<String>();
		// TODO: who did that??? please speak to Rene about a session with the clue bat...
		list.add("00:00");
		list.add("01:00");
		list.add("02:00");
		list.add("03:00");
		list.add("04:00");
		list.add("05:00");
		list.add("06:00");
		list.add("07:00");
		list.add("08:00");
		list.add("09:00");
		list.add("10:00");
		list.add("11:00");
		list.add("12:00");
		list.add("13:00");
		list.add("14:00");
		list.add("15:00");
		list.add("16:00");
		list.add("17:00");
		list.add("18:00");
		list.add("19:00");
		list.add("20:00");
		list.add("21:00");
		list.add("22:00");
		list.add("23:00");
		return createValueRange(list);
	}

	/**
	 * Replaces all occurances of > and < in the String <code>line</code> by the HTML code.
	 * @param line String that should be converted to HTML.
	 * @param ci
	 * @return String line with replacements.
	 */
	public static String replaceHTMLChars(String line) {
		String curLine = line;
		curLine = replaceAllStrings(curLine, "<", "&lt;");
		curLine = replaceAllStrings(curLine, ">", "&gt;");
		return curLine;
	}

	/**returns the index of an element in an arry
	 *
	 *@param arr Array with values
	 *@param value value to search
	 */
	public static int getIndex(String[] arr, String value) {
		for (int i=0; i<arr.length; i++)  {
			if (arr[i].equals(value)) {
				return i;
			}
		}
		return -1;
	}

	/**returns a list with only one entry */
	public static LinkedList<?> getSingleList(String value) {
		LinkedList<String> ret = new LinkedList<String>();
		ret.add(value);
		return ret;
	}

	/** converts an array of objects to an array of strings (uses the toString() method)
	 * @param arrOb	the array of object
	 * @return	converted array
	 */
	public static String[] convertToStringArr(Object[] arrOb) {
		String[] ret = new String[arrOb.length];
		for (int i=0; i<ret.length; i++) {
			ret[i] = arrOb[i].toString();
		}
		return ret;		
	}

	public static String convertListToString(List<?> list, String delim) {
		String retVal = "";
		for (int i = 0; i < list.size(); i++) {
			retVal += (String)list.get(i);
			retVal += delim;
		}
			
		return retVal;
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

	/** converts a List into a String array*/
	public static String[] convertListToStringArr(List<?> aList) {
		String[] str = new String[aList.size()];
		Iterator<?> it = aList.iterator();
		int i = 0;
		while (it.hasNext()) {
			str[i] = (String) it.next();
			i++;
		}
		return str;
	}

	/**returns a list of missing entries -> For example a form lists all IpSec-connections. The User
	 * deletes one ore more - so the next JSP-Site that has to delete the entries must know which entries miss.
	 * @param oldValus	Array with values when the form first started
	 * @param requestValues	Array with values from the request object
	 */
	public static LinkedList<?> getMissingEntries(String[] oldValues, String[] requestValues) {
		LinkedList<String> ret = new LinkedList<String>();
		Hashtable<String, String> oldTable = new Hashtable<String, String>();
		Hashtable<String, String> requestTable = new Hashtable<String, String>();
		for (int i=0; i<oldValues.length; i++) {
			oldTable.put(oldValues[i],oldValues[i]);
		}
		// filling the Hashtable with values of the request object
		for (int i=0; i<requestValues.length; i++) {
			requestTable.put(requestValues[i],requestValues[i]);
		}		
		
		Enumeration<?> enumer = oldTable.elements();
		while (enumer.hasMoreElements()) {
			String str = enumer.nextElement().toString();
			if (!requestTable.containsKey(str)) {
				ret.add(str);
			}
		}		
		
		return ret;
	}

	/** returns an array with one entry
	 * @param value	
	 * @return 	array with one entry
	 */
	public static String[] getSingleArr(String value) {
		String[] str = new String[1];
		str[0] = value;
		return str;
	}

	/**sorts keys by a given array with rankings 
	 * @param keyArr		array with keys
	 * @param rankingArr	array with rankings
	 * @return the sorted array
	 */
	//@SuppressWarnings("unchecked")
/*	public static String[] sort( int[] rankingArr,String[] keyArr) {
		if (keyArr.length < 2) return keyArr;
		String[] ret;
		RankingObject[] arrOb = new RankingObject[keyArr.length];
		for (int i=0; i<keyArr.length; i++) {
			RankingObject o = new RankingObject(rankingArr[i],keyArr[i]);
			arrOb[i] = o;
		}
		Arrays.sort(arrOb, new RankingComparator());
		ret = new String[arrOb.length];
		for (int i=0; i<arrOb.length; i++) {
			ret[i] = arrOb[i].o.toString();
		}
		
		return ret;
			
	}
*/
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

	/**returns the number of tokens
	 * @param str	the full string
	 * @param delim	the deliminiter
	 * @return	specified token
	 */	
	public static int countToken(String str, String delim) {
		StringTokenizer tok = new StringTokenizer(str, delim);
		return tok.countTokens();
	}

	/**returns a specified token
	 * @param str the full string
	 * @return	specified token
	 */
	public static String getWhitespaceToken(String str, int token) {
		return getToken(str," \t",token);
	}

	/**converts a value if null into a specified value*/
	public static String nullToValue(String str, String value) {
		if (isBlank(str)) {
			return value;
		}
		return str;
	}

	/**cuts every occourence of str in basic
	 * for example: "wurst","st" gets to "wur"
	 * 
	 * @param basic		String that contains str
	 * @param str		String to replace
	 * @param newStr	String instead of str
	 * @return	replaces String
	 */
	public static String cutOutString(String basic, String str) {
		if (basic.indexOf(str)==-1) {
			return basic;
		}
		if (basic==null) {
			return null;
		}
		boolean done = false;
		while (!done) {
			int index = basic.indexOf(str);
			if (index==-1) {
				done = true;
			} else {
				basic = basic.substring(0,index) + basic.substring(index+str.length(),basic.length());
			}
		}
		return basic;
	}

	/**
	 * Replaces the first occurance of the string <code>str</code> in the String <code>basic</code> with the String <code>newStr</code>.
	 * @param basic The String that should be tested.
	 * @param str The String that should be replaced.
	 * @param newStr The String that should be inserted instead of <code>str</code>.
	 * @return A String where the first occurance of the substring <code>str</code> is replaced by <code>newStr</code>.
	 * ATTENTION - only looks for the whole string - does not work with "{value}", "{}"
	 * since the string "{}" is not in "{value}"
	 */
	public static String replaceFirstString(String basic, String str, String newStr) {
		if (basic.indexOf(str)==-1) {
			return basic;
		}
		if (basic==null) {
			return null;
		}
		boolean done = false;
		while (!done) {
			int index = basic.indexOf(str);
			if (index==-1) {
				done = true;
			} else {
				basic = basic.substring(0, index) + newStr + basic.substring(index + str.length(), basic.length());
				done=true;
			}
		}
		return basic;
	}

	/**replaces als strings in strReplace with the string in str
	 * 
	 */
	public static String replaceAllStringWithTokens(String strBasic,
			String strSearch,String strReplace, String delim) {
		String ret = "";
		String tmp = "";
		StringTokenizer tok = new StringTokenizer(strBasic,delim);
		while (tok.hasMoreTokens()) {
			tmp = tok.nextToken();
			if (tmp.equals(strSearch)) {
				tmp = strReplace;
			}
			ret += tmp;
			if (tok.hasMoreTokens()) {
				ret+=delim;
			}
		}
		return ret;
	}

	/**
	 * Replaces all occurances of the string <code>str</code> in the String <code>basic</code> with the String <code>newStr</code>.
	 * @param basic The String that should be tested.
	 * @param str The String that should be replaced.
	 * @param newStr The String that should be inserted instead of <code>str</code>.
	 * @return A String where all occurances of the substring <code>str</code> are replaced by <code>newStr</code>.
	 * ATTENTION - only looks for the whole string - does not work with "{value}", "{}"
	 * since the string "{}" is not in "{value}"
	 */
	public static String replaceAllStrings(String basic, String str, String newStr) {
		if (basic.indexOf(str)==-1) {
			return basic;
		}
		if (basic==null) {
			return null;
		}
		boolean done = false;
		while (!done) {
			int index = basic.indexOf(str);
			if (index==-1) {
				done = true;
			} else {
				basic = basic.substring(0, index) + newStr + basic.substring(index + str.length(), basic.length());
			}
		}
		return basic;
	}

	/**
	 * Cuts all occurances of any chars the string <code>str</code> in the String <code>basic</code>.
	 * @param basic The String that should be tested.
	 * @param str The String that should be replaced.
	 * @return A String where all occurances of the substring <code>str</code> are replaced by <code>newStr</code>.
	 * ATTENTION - ONLY works if the characters are at the beginning and at the end of the string
	 */
	public static String replaceAllChars(String basic, String str) {
		for (int i=0; i<str.length(); i++) {
			basic = basic.replace(str.charAt(i),' ').trim();
		}
		return basic;
	}

	/**
	
	/**checks whether a value exists more than one time in a specific String Array
	 * @param str array of Strings with the values
	 * @return  -1 if not double values occur - otherwise first index of double value
	 */
	public static int checkMultipleValues(String[] str) {
		if (str.length==1) {	// only one value - can not be multiple
			return -1;
		}
		for (int i = 0; i < str.length; i++) {
			String temp = str[i];
			for (int j = 0; j < str.length; j++) {
				if (!isBlank(temp)) {
					if (temp.equals(str[j])
						&& (i != j)
						&& (temp.length() >= 1)) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	/** returns the string representation for the style sheet class - eg: class=textbox or an empty string*/
	public static String getClassString(String styleClass) {
		if (styleClass!=null) {
			return " class=\"" + styleClass + "\" ";
		} else {
			return "";
		}
	}

	/** Checks if the string is a valid range for ports or length 
		 * @param str The String to check.
		 * @return True if the string is a valid range for ports or length
		 */
		public static boolean isCorrectRange(String str) {
	// The following lines work only with Java 1.4 or later
	//		String[] parts = str.split(":");
	//		if ((parts.length == 2) && !isBlank(parts[0]) && !isBlank(parts[1])) {
	//			return (str.matches("[0-9]*:[0-9]*") && ((new Integer(parts[0])).intValue() < (new Integer(parts[1])).intValue()));
	//		} else {
	//			return str.matches("[0-9]*:[0-9]*");
	//		}
			boolean isRange = true;
			int indexOfColon = str.indexOf(":");
			int i1 = 0;
			int i2 = 0;
			String part1, part2 = "";
			if (indexOfColon > 0) {
				part1 = str.substring(0, indexOfColon);
				try {
					i1 = Integer.parseInt(part1);
					if ((i1 <= 0) || (i1 > 65535)) {
						isRange = false;
					}
				} catch (NumberFormatException e) {
					isRange = false;
				}
			}
			if (indexOfColon < str.length()-1) {
				part2 = str.substring(indexOfColon+1);
				try {
					i2 = Integer.parseInt(part2);
					if ((i2 <= 0) || (i2 > 65535)) {
						isRange = false;
					}
				} catch (NumberFormatException e) {
					isRange = false;
				}
			}
			if (i1>i2) {
				isRange = false;
			}
			return isRange;
		}

	/**
	 * concatenates the values of a string array to one string
	 * @param arr The String array conaining the elements to concat
	 * @param sep The separator between the values of the array
	 * @return The concatenated String.
	 */
	public static String getStringFromArray(String[] arr, String sep) {
		String value = "";
		for (int i = 0; i < arr.length; i++) {
			value += arr[i].trim();
			if (i < arr.length-1) {
				value += sep;
			}
		}
		return value;
	}

	/**
	 * creates a random integer between 0 (inclusive) and <code>upper</code> (exclusive) and returns
	 * the int converted to a String. Used for Cronjobs to create different times for executing the jobs.
	 * @param upper The upper border of the range for the random number (exclusive).
	 * @return The random number converted to a string.
	 */
	public static String getRandomIntAsString(int upper) {
		Random rand = new Random();
		return new String((new Integer(rand.nextInt(upper))).toString());
	}

	/**
	 * splits the String <code>basis</code> into an array with <code>seperator</code> as seperator using
	 * org.apache.regexp.RE.
	 * @param basis
	 * @param seperator
	 * @return splitted String as array
	 */    
/*	public static String[] splitString(String basis,String seperator) {
		RE regex_pattern=null;
		try {
			regex_pattern = new RE(seperator);
		} catch (RESyntaxException e) {
			return null;
		}
		return regex_pattern.split(basis);
	}
*/
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

	/**converts a number of minutes in the format the cron jobber needs it
	 * @param minutes
	 * @param command	cron command
	 */
	/*// I'm sure this caused some bugs ...
	public static String convertMinutesToCronFormat(int minutes, String command) {
		//0 * * * * root  sh /etc/network/shaping.sh
		String str="";
		int rest=minutes%60;
		return str;
	}*/

	/**
	 * returns true if a vector contains a specified string
	 * 
	 * @param	vec	the vector to search in
	 * @param	search the string to search for
	 * 
	 * @return true if the vector contains the string
	 */
	public static boolean isStringInVector(Vector<?> vec, String search) {
		for (int i = 0; i < vec.size(); i++) {
			if (vec.get(i).toString().equals(search)) {
				return true;
			}
		}
		return false;
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
	
	
	
	
	/**
	 * @param line 
	 * @param value the searched value
	 * @return
	 * 
	 * Looks, if the provided line contains the value.
	 * The algorithm checks if the found string that matches the value, is indeed the real value,
	 * and not just a different string which contains the value.
	 * The difference-algorithm uses characters, that may not appear in real words (_, ?, %,....) to execute
	 * "boundary" checks and determine if the lookup value is not within another "real" word.
	 */
	public static boolean containsExactValue(String line, String value)
	{
		if(StringHelper.containsPrefix(line, value) && StringHelper.containsSuffix(line, value) )
			return true;
		else
			return false;
	}
	/**
	 * @param line
	 * @param value
	 * @return
	 * 
	 * Looks if the provided value is at the end of the line, or if the line contains the value+suffix.
	 */
	private static boolean containsSuffix(String line, String value)
	{
		// is value at the end of the line?!
		if(line.contains(value+Character.toString((char)3)))
			return true;
		
		// check different non-alphabetical characters to determine if line contains the exact value
		for(int i=32;i<=47;i++)
		{
			String suffix = Character.toString((char)i);
			if(line.contains(value+suffix))
				return true;
		}
		for(int i=58;i<=63;i++)
		{
			String suffix = Character.toString((char)i);
			if(line.contains(value+suffix))
				return true;
		}
		for(int i=91;i<=96;i++)
		{
			String suffix = Character.toString((char)i);
			if(line.contains(value+suffix))
				return true;
		}
		return false;
	}
	/**
	 * @param line
	 * @param value
	 * @return
	 * 
	 * Looks if the provided value is at the beginning of the line, or if the line contains the prefix+value.
	 */
	private static boolean containsPrefix(String line, String value)
	{
		// is value at the beginning of the line?!
		if(line.contains(Character.toString((char)2)+value))
			return true;
		
		// check different non-alphabetical characters to determine if line contains the exact value
		for(int i=32;i<=47;i++)
		{
			String prefix = Character.toString((char)i);
			if(line.contains(prefix+value))
				return true;
		}
		for(int i=58;i<=63;i++)
		{
			String prefix = Character.toString((char)i);
			if(line.contains(prefix+value))
				return true;
		}
		for(int i=91;i<=96;i++)
		{
			String prefix = Character.toString((char)i);
			if(line.contains(prefix+value))
				return true;
		}
		return false;
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
