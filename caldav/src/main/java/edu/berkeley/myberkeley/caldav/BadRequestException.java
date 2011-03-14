package edu.berkeley.myberkeley.caldav;

public class BadRequestException extends CalDavException {
    public BadRequestException(String message) {
        super(message, null);
    }
}
