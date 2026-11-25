package com.hotel.exception;

/** Business/validation error carrying the HTTP status to return. */
public class HotelException extends RuntimeException {
    private final int status;
    public HotelException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
