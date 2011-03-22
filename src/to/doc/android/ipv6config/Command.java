/*****************************************************************************
 *  Project: Gibraltar-Webinterface
 *  Description: webinterface for the firewall gibraltar
 *  Author: Rene Mayrhofer
 *  Copyright: Rene Mayrhofer, 2001-2011
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3 
 * as published by the Free Software Foundation.
 *****************************************************************************/

package to.doc.android.ipv6config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** This class implements a simple interface to executing system commands. The
 * first option is synchronous execution, to wait for the command to finish. In
 * this case, the command and its parameters can be passed in a single String 
 * (with parameters delimited by blanks as usual), some String argument can 
 * optionally be passed to the process as its standard input, and the output 
 * it creates will be returned as another String.
 *
 * @author Rene Mayrhofer
 */
public class Command {
	/** Our logger for this class. */
	private final static Logger logger = Logger.getLogger(Command.class.getName());
	
	/** Buffer size used for BufferReader and BufferWriter. */
	private final static int IOBufferSize = 4192;

	/** The following states of background processes are possible:
	 * - key is not in map: command is not running and is not scheduled to run
	 * - key maps to integer value 0: command is not running, but is waiting to be started
	 * - key maps to integer value 1: command is currently running and no further runs are scheduled
	 * - key maps to integer value 2: command is currently running and another run is scheduled
	 * - key maps to integer value 3: command is continuously running in background and should not stop 
	 */
	private static HashMap<String, Integer> sysCommandList = new HashMap<String, Integer>();
	
	/** For continuous commands, that is those with value=3 in sysCommandList,
	 * this map holds the associated process object.
	 */
	private static HashMap<String, Process> continuousCommands = new HashMap<String, Process>();

	/** This is a helper method to execute processes in the background. It 
	 * checks if that specific process is already running and waits for it to
	 * terminate before starting another instance. When multiple requests for
	 * background execution of the same command are made, they are queued and
	 * executed only once.
	 */
	private static class AsyncRunHelper extends Thread {
		String command;
		boolean editsSystem;
		boolean requiresSU;

		public AsyncRunHelper(String command, boolean editsSystem, boolean requiresSU) {
			super();
			this.command = command;
			this.editsSystem = editsSystem;
			this.requiresSU = requiresSU;
		}
		
		public void run()  {		
			try {
				logger.finer("Sleeping for 5 seconds before executing " + command);
				sleep(5 * 1000);
				do { // do while value = 2 (rescheduling)
					synchronized (sysCommandList) {
						logger.finest("AsyncRunHelper.run Put in List " + command);
						// starting the command right now, so set state to 1
						sysCommandList.put(command, new Integer(1));
					}				
					// execute the command
					logger.finer("Immediately BEFORE running Command " + command);
					executeCommand(command, editsSystem, requiresSU, null);
					logger.finer("Immediately AFTER running Command " + command);
					synchronized (sysCommandList) {
						logger.finest("AsyncRunHelper.run remove command now: " + command);
						// if the state is still 1 and hasn't been changed to 2, then remove from the list
						if (getSysCommandState(command) == 1)
							sysCommandList.remove(command);
					}				
				} while (getSysCommandState(command) == 2);
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Error at CommandThread.Run with command " + command, e);
			}
			logger.finer("AnsyncRunHelper_Finished with: " + command);
		}
	}
	
	private static int getSysCommandState(String command) {
		synchronized (sysCommandList) {
			if (sysCommandList.containsKey(command))
				return sysCommandList.get(command).intValue();
			else
				return -1;
		}
	}
	
	private static Process getContinuousProcess(String command) {
		synchronized (sysCommandList) {
			if (getSysCommandState(command) != 3) {
				return null;
			}
			else {
				synchronized (continuousCommands) {
					if (! continuousCommands.containsKey(command)) {
						logger.log(Level.WARNING, "Command " + command + " is in state 3 but has not stored process object, can not return reference. This should not happen!");
						throw new InternalError("Command " + command + " is in state 3 but has not stored process object, can not return reference."); 
					}
					
					logger.finer("Command " + command + " has already been started continuously, returning old handle");
					return continuousCommands.get(command);
				}
			}
		}
	}
	
	/** Executed a command in the background. That is, this method returns 
	 * immediately instead of waiting for the command execution to finish. The
	 * command will be queued and not be executed in parallel to another one 
	 * with the same command string.
	 */
    public static void executeExtraProcessCommand(String systemCommand, boolean editsSystem, boolean requiresSU) 
    		throws ExitCodeException, IOException {
		synchronized (sysCommandList) {
			// only add if not already running or scheduled to run
			if (!sysCommandList.containsKey(systemCommand)) {
				logger.info("ADDING new syscommand " + systemCommand + " and starting thread");
				sysCommandList.put(systemCommand, new Integer(0));
				// new to the list, starting thread for this system command
		    	AsyncRunHelper t = new AsyncRunHelper(systemCommand, editsSystem, requiresSU);
				t.start();
/*				ErrorLog.log("Content of systemCommandList at t.start of " + systemCommand);
				Iterator it = sysCommandList.values().iterator();
				while (it.hasNext()) {
					ErrorLog.log("Content: " + it.next().toString());
				}*/
			} else {
				logger.info("SKIPPING to add syscommand " + systemCommand + ", as it is already in the list");
				int curState = getSysCommandState(systemCommand);
				if (curState == 1) {
					logger.info("COMMAND is already executing, scheduling for re-execution");
					sysCommandList.put(systemCommand, new Integer(2));
				}
			}
		}
    }
    
    /** Executes a (reading) command that should not terminate but continuously 
     * produce output. It is implicitly assumed that this command will not 
     * modify the system.
     * @param combinedCommand
     */
	public static InputStream executeContinuousCommand(String combinedCommand, boolean requiresSU) throws IOException {
		Process proc;
		synchronized (sysCommandList) {
			// only start it if it is not already running or scheduled to run
			if (!sysCommandList.containsKey(combinedCommand)) {
				logger.info("ADDING new continuous syscommand " + combinedCommand + " and starting process");
				sysCommandList.put(combinedCommand, new Integer(3));
				proc = checkAndExecute(combinedCommand, null, false, requiresSU, null);
				// started process, so now remember the Process object
				synchronized (continuousCommands) {
					continuousCommands.put(combinedCommand, proc);
				}
				return proc.getInputStream();
			}
			else if ((proc = getContinuousProcess(combinedCommand)) != null) {
				return proc.getInputStream();
			}
			else {
				logger.log(Level.WARNING, "Can not execute command " + combinedCommand + 
						" continuously in the background, as it has state " +
						getSysCommandState(combinedCommand));
				return null;
			}
		}
	}
	
	/** Destroys a process that has previously been started with executeContinuousCommand.
	 * @param combinedCommand The same string that has previously been passed to executeContinuousCommand.
	 * @return true if the process was still running, false if this command was not known.
	 * @throws ForbiddenCommandException
	 * @throws IOException 
	 */
	public static boolean stopContinuousCommand(String combinedCommand) throws IOException {
		Process proc = getContinuousProcess(combinedCommand);
		if (proc == null) {
			logger.finer("Can not stop command " + combinedCommand + ", not in list");
			return false;
		}
		
		logger.info("Terminating background continuous command " + combinedCommand);
		proc.getInputStream().close(); // this should terminate threads still listing for the output
		proc.destroy();
		return true;
	}
	
	/** This helper checks if the command has correct permissions to be 
	 * executed, starts the command, and sends standard input if defined. It
	 * can be called either with combinedCommand or splitCommand.
	 */
	private static Process checkAndExecute(String combinedCommand, String[] splitCommand, 
    		boolean editsSystem, boolean requiresSU, String sendToStdin) throws IOException {
       	Runtime r = Runtime.getRuntime();
       	Process proc;

       	if (combinedCommand != null) {
       		if (requiresSU)
       			combinedCommand = "su " + combinedCommand;
       		proc = r.exec(combinedCommand);
       	}
       	else {
       		if (requiresSU) {
       			String[] tmp = new String[splitCommand.length+1];
       			for (int i=0; i<splitCommand.length; i++)
       				tmp[i+1] = splitCommand[i];
       			splitCommand[0] = "su";
       		}
       		
       		proc = r.exec(splitCommand);
       	}

		// if outputString is not null -> write!
		if (sendToStdin != null) {			
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()), IOBufferSize);
			out.write(sendToStdin);
			out.close();
		}
        	
       	return proc;
	}
    


	
    /** This helper 
     */
    private static String executeCommand(String combinedCommand, String[] splitCommand, 
    		boolean editsSystem, boolean requiresSU, String sendToStdin) 
			throws ExitCodeException, IOException {
    	Process proc = checkAndExecute(combinedCommand, splitCommand, editsSystem, requiresSU, sendToStdin);

		// start reading the command output
		BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()), IOBufferSize);
		StringBuffer output = new StringBuffer();
		String temp;
		while ((temp = in.readLine()) != null) {
			output.append(temp);
			output.append("\n");
		}

		int result;
       	try {
        	result = proc.waitFor();
		} catch (InterruptedException ex) {
			result = 0;				
		}
			
		if (combinedCommand != null)
			logger.finer("Command.executeCommand: (STATE2) " + combinedCommand + " Exit Value: " + result);
		else
			logger.finer("Command.executeCommand: (STATE2) " + splitCommand[0] + " Exit Value: " + result);
			
		// finish reading command output, there might be something left
		while ((temp = in.readLine()) != null) {
			output.append(temp);
			output.append("\n");
			}
		in.close();

       	if (result!=0) {
       		throw new ExitCodeException(convertToHTML(output.toString()), 
       				combinedCommand != null ? combinedCommand : splitCommand[0], result);
       	} else {
        	return output.toString();
		}
    }
    
    /** Executes a system command and returns its output. Optionally, some
     * string input can be sent to the process standard input.
     *
     * @param systemCommand The command to execute with parameters delimited with blanks.
     * @param editsSystem If true, then this command modifies the running system and therefore needs WRITE permissions to execute.
     * @param sendToStdin If set (not null), then this string will be sent to 
     *                    the system command as standard input. It can be used 
     *                    to provide input that the command would ask on the
     *                    terminal.
     * 
     * @return The (standard) output of the command after terminating.
     */
    public static String executeCommand(String systemCommand, boolean editsSystem, boolean requiresSU, String sendToStdin) 
    		throws ExitCodeException, IOException {
    	return executeCommand(systemCommand, null, editsSystem, requiresSU, sendToStdin);
    }

    /** Executes a system command and returns its output. Optionally, some
     * string input can be sent to the process standard input.
     *
     * @param systemCommand The command to execute; the first array element is
     *                      the command itself, while all further elements are
     *                      taken es command parameters.
     * @param editsSystem If true, then this command modifies the running system and therefore needs WRITE permissions to execute.
     * @param sendToStdin If set (not null), then this string will be sent to 
     *                    the system command as standard input. It can be used 
     *                    to provide input that the command would ask on the
     *                    terminal.
     * @param output If set (not null) output of the command will be appended here.
     * Use this if the command is likely to fail.
     *
     * @return The (standard) output of the command after terminating.
     */
	public static String executeCommand(String[] systemCommand, boolean editsSystem, boolean requiresSU, String sendToStdin) 
			throws ExitCodeException, IOException {
		return executeCommand(null, systemCommand, editsSystem, requiresSU, sendToStdin);
	}
	
	/** Converts a string to HTML (cuts the \n and replaces with <br>). */
	private static String convertToHTML(String line)  {
		if (StringHelper.isBlank(line)) {
			return "";
			// TODO: now the CALLER of Command needs to create that output!
			/*try {
				return ci.getLangSession().getText(LangConst.NO_SYSTEM_OUTPUT);
			}catch (IOException ioEx) {
			  return "Unexpected Error. Please contact Administrator (Error: Necessary language file not found )";
			}catch (JDOMException jEx) {
			  return "Unexpected Error. Please contact Administrator (Error: Language file not valid)";
			}catch (LanguageElemNotFoundException lEx) {
			  return "Unexpected Error. Please contact Administrator ";
			}*/
		}
		StringTokenizer tok = new StringTokenizer(line,"\n");
		String str = "";
		while (tok.hasMoreTokens()) {
			str += tok.nextToken() + "<br>";
		}
		return str;
	}
	
	/** Simply execute a command and return exit code */
	public static int executeCommandEC(String systemCommand) throws IOException, InterruptedException {
		Process p = checkAndExecute(systemCommand, null, false, false, null);
		p.waitFor();
		return p.exitValue();
	}


	/**
	 * Helper for executing a command and reading stdout as well as stderr. Code
	 * is partially borrowed from Gibraltar code from within this class. However,
	 * it tries to omit all custom exceptions and only report bad errors.
	 *
	 * @note This method splits your command by spaces.
	 *
	 * @todo Honor whitespaces (\s+), honor quotes!
	 *
	 * @param cmd The full command to execute as simple String.
	 * @param stdin Data which should be sent to stdin. May be null.
	 * @param stdout Buffer for writing the stdout output to. May be null.
	 * @param stderr Buffer for writing the stderr otupt to. May be null.
	 * 
	 * @return Exit code of the called command.
	 *
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static int executeCommand(String cmd, boolean requiresSU, String stdin, StringBuffer stdout, StringBuffer stderr)
			throws IOException, InterruptedException {
		String[] parts = cmd.split(" ");
		return executeCommand(parts, requiresSU, stdin, stdout, stderr);
	}


	/**
	 * Helper for executing a command and reading stdout as well as stderr. Code
	 * is partially borrowed from Gibraltar code from within this class. However,
	 * it tries to omit all custom exceptions and only report bad errors.
	 *
	 * @param cmd The full command to execute, as String[].
	 * @param stdin Data which should be sent to stdin. May be null.
	 * @param stdout Buffer for writing the stdout output to. May be null.
	 * @param stderr Buffer for writing the stderr otupt to. May be null.
	 * 
	 * @return Exit code of the called command.
	 *
	 * @throws IOException 
	 * @throws InterruptedException 
	 */	
	 public static int executeCommand(String[] cmd, boolean requiresSU, String stdin, StringBuffer stdout, StringBuffer stderr)
 			throws IOException, InterruptedException {
		 Process proc = checkAndExecute(null, cmd, false, requiresSU, stdin);

		 BufferedReader stdoutReader = null;
		 BufferedReader stderrReader = null;

		// read stdout if required
		if (stdout != null) {
			stdoutReader = new BufferedReader(new InputStreamReader(proc.getInputStream()), IOBufferSize);
			
			String out;
			while ((out = stdoutReader.readLine()) != null) {
				stdout.append(out);
			}
		}
		
		// read stderr if required
		if (stderr != null) {
			stderrReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()), IOBufferSize);
			
			String err;
			while ((err = stderrReader.readLine()) != null) {
				stderr.append(err);
			}
		}
		
		// wait for a clean exit, throws InterruptedException
		int ret;
		ret = proc.waitFor();
		
		//debug output
		StringBuffer cmdString = new StringBuffer();
		for(String s: cmd){
			cmdString.append(s + " ");
		}
		logger.log(Level.WARNING, "'" + cmdString + "' returned " + ret);
		
		// read possible stdout leftovers
		if (stdout != null) {
			String out;
			while ((out = stdoutReader.readLine()) != null) {
				stdout.append(out);
			}
			stdoutReader.close();
		}
		
		// same for stderr
		if (stderr != null) {
			String err;
			while ((err = stderrReader.readLine()) != null) {
				stderr.append(err);
			}
			stderrReader.close();
		}
		
		return ret;
	}
}
