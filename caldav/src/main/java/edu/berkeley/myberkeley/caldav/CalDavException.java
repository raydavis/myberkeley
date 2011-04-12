package edu.berkeley.myberkeley.caldav;

public class CalDavException extends Exception {

  private static final long serialVersionUID = 2546182064294742075L;

  public CalDavException(String message, Throwable t) {
    super(message, t);
  }

}
