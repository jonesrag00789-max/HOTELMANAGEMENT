package com.hotel.model;

import com.hotel.exception.HotelException;

public enum RoomType {
    STANDARD("Standard", 2500, 2), DELUXE("Deluxe", 4500, 3), SUITE("Suite", 9000, 4);

    private final String label;
    private final double rate; // INR per night
    private final int capacity;

    RoomType(String label, double rate, int capacity) { this.label = label; this.rate = rate; this.capacity = capacity; }
    public String label() { return label; }
    public double rate() { return rate; }
    public int capacity() { return capacity; }

    public static RoomType parse(String s) {
        try { return valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { throw new HotelException(400, "Unknown room type: " + s); }
    }
}
