package edu.berkeley.myberkeley.caldav;

public class BadRequestException extends CalDavException {

  private static final long serialVersionUID = -4361376969555851017L;

  public BadRequestException(String message) {
    super(message, null);
  }
}
