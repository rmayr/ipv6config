package to.doc.android.ipv6config;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2001
 * Company:
 * @author
 * @version 1.0
 */

 /**
  * this exception can handle two messages
  * one message will be translated (message is key in languagefile)
  * the second message will NOT be translated and will be appended to the
  *   translated message as it is (this is useful for module names or user names)
  */
public class GibraltarBaseException extends Exception {

	private static final long serialVersionUID = 1L;
	
  /**
   * untranslated part of exception message
   */
  private String untranslatedMsg = "";

  /**
   * constructor
   */
  public GibraltarBaseException() {
    super();
  }

  /**
   * constructor
   *
   * @param msg  msg that should be translated
   */
  public GibraltarBaseException(String msg) {
    super(msg);
  }

  /**
   * constructor
   *
   * @param msg  msg that should be translated
   * @param untranslatedMsg  msg that should NOT be translated
   */
  public GibraltarBaseException(String msg, String untranslatedMsg) {
    super(msg);
    this.setUntranslatedMsg(untranslatedMsg);
  }
  

  /**
   * sets untranslated message
   *
   * @param msg  untranslated msg
   */
  public void setUntranslatedMsg(String msg){
    this.untranslatedMsg = msg;
  }

  /**
   * returns untranslated part of message
   *
   * @return  untranslated part of message
   */
  public String getUntranslatedMsg(){
    return this.untranslatedMsg;
  }
  
  @Override
  public String toString() {
	  return super.toString() + untranslatedMsg;
  }
}
