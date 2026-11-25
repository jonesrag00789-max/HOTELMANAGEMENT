package com.hotel.model;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/** A physical room. Each room owns a lock so that bookings for different rooms never block each other. */
public class Room {
    private final String number;
    private final RoomType type;
    private final int floor;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Booking> bookings = new CopyOnWriteArrayList<>();
    private volatile boolean maintenance;

    public Room(String number, RoomType type, int floor) { this.number = number; this.type = type; this.floor = floor; }

    public String getNumber() { return number; }
    public RoomType getType() { return type; }
    public int getFloor() { return floor; }
    public ReentrantLock lock() { return lock; }
    public void addBooking(Booking b) { bookings.add(b); }
    public boolean isMaintenance() { return maintenance; }
    public void setMaintenance(boolean m) { maintenance = m; }

    /** True if bookable for [in, out): not under maintenance and no active booking overlaps. Hold lock() for certainty. */
    public boolean isFree(LocalDate in, LocalDate out) {
        if (maintenance) return false;
        for (Booking b : bookings) if (b.isActive() && b.overlaps(in, out)) return false;
        return true;
    }

    /** True if a guest is booked in on the given night (ignores maintenance). */
    public boolean isOccupied(LocalDate night) {
        for (Booking b : bookings) if (b.isActive() && b.overlaps(night, night.plusDays(1))) return true;
        return false;
    }
}
