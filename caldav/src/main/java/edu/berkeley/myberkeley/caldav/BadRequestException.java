package edu.berkeley.myberkeley.caldav;

public class BadRequestException extends CalDavException {
    public BadRequestException(String message, int httpStatus) {
        super(message, null);
    }
}
