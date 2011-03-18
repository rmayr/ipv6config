/*****************************************************************************
 *  Project: Gibraltar-Webinterface
 *  Description: webinterface for the firewall gibraltar
 *  Author: Andreas Woeckl
 *  Copyright: Andreas Woeckl 2001
 *****************************************************************************/

package to.doc.android.ipv6config;


/**
 *  The exception <code>ExitCodeException</code> is thrown if a systam command returned an error code != 0
 */
public class ExitCodeException extends GibraltarBaseException {

	private static final long serialVersionUID = 1L;

	private int exitCode;
	private String executedCommand;

	/**
	 * paremeterless Constructor
	 */
	public ExitCodeException(String executedCommand, int exitCode) {
		super();
		this.executedCommand = executedCommand;
		this.exitCode = exitCode;
	}

	/**
	 * Constructor that initializes with a message
	 *
	 * @param msg   the message to display
	 */
	public ExitCodeException(String msg, String executedCommand, int exitCode) {
		super(msg);
		this.executedCommand = executedCommand;
		this.exitCode = exitCode;
	}

	/**
	 * Constructor that initializes with a message and a elementName 
	 * 
	 * @param msg   		the message to display
	 * @param elementName	elementName that caused the exception
	 */
	public ExitCodeException(String msg, String elementName) {
		super(msg, elementName);
	}
	
	public int getExitCode() {
		return exitCode;
	}
	
	@Override
	public String toString() {
		return super.toString() + " [cmd '" + executedCommand + "' yielded exit code " + exitCode + "]";
	}
}