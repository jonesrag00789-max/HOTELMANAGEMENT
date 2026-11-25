package com.hotel.service;

import com.hotel.exception.HotelException;
import com.hotel.model.*;
import com.hotel.util.Json;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core business logic.
 *
 * Concurrency: the "is the room free?" check and "add the booking" step happen together while holding
 * that room's ReentrantLock, so two customers can never both get the same room for overlapping dates.
 * Different rooms use different locks, so unrelated bookings run in parallel.
 */
public class HotelService {
    public static final double GST_RATE = 0.12;
    public static final int LONG_STAY_NIGHTS = 5;
    public static final double LONG_STAY_DISCOUNT = 0.10;
    public static final double BREAKFAST_PER_PERSON_NIGHT = 500;
    public static final int FREE_CANCELLATION_DAYS = 2; // later than this: one night's rate is charged
    private static final Map<String, Integer> PROMOS = Map.of("WELCOME10", 10, "FESTIVE15", 15);

    private final Map<String, Room> rooms = new LinkedHashMap<>(); // fixed after construction
    private final Map<Integer, Booking> bookings = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1000);

    public HotelService() {
        for (int i = 1; i <= 5; i++) add("10" + i, RoomType.STANDARD, 1);
        for (int i = 1; i <= 4; i++) add("20" + i, RoomType.DELUXE, 2);
        for (int i = 1; i <= 3; i++) add("30" + i, RoomType.SUITE, 3);
    }
    private void add(String no, RoomType t, int floor) { rooms.put(no, new Room(no, t, floor)); }

    public Collection<Room> getRooms() { return rooms.values(); }

    public List<Room> findAvailable(LocalDate in, LocalDate out, RoomType type) {
        validateDates(in, out);
        List<Room> result = new ArrayList<>();
        for (Room r : rooms.values())
            if ((type == null || r.getType() == type) && r.isFree(in, out)) result.add(r);
        return result;
    }

    public Booking reserve(String owner, String name, String phone, RoomType type, LocalDate in, LocalDate out,
                           int guests, boolean breakfast, String promo) {
        if (name == null || name.isBlank()) throw new HotelException(400, "Guest name is required");
        if (phone == null || !phone.matches("[0-9+\\- ]{7,15}")) throw new HotelException(400, "Enter a valid phone number");
        validateDates(in, out);
        if (guests < 1 || guests > type.capacity())
            throw new HotelException(400, type.label() + " rooms sleep 1 to " + type.capacity() + " guests");
        promoPercent(promo); // validates the code
        for (Room r : rooms.values()) {
            if (r.getType() != type) continue;
            ReentrantLock lock = r.lock();
            lock.lock();
            try {
                if (r.isFree(in, out)) {               // check ...
                    Booking b = new Booking(nextId.incrementAndGet(), owner, name.trim(), phone.trim(), r, in, out,
                        guests, breakfast, promo == null ? "" : promo.trim().toUpperCase());
                    r.addBooking(b);                    // ... and act, atomically per room
                    bookings.put(b.getId(), b);
                    return b;
                }
            } finally { lock.unlock(); }
        }
        throw new HotelException(409, "No " + type.label() + " rooms available for the selected dates");
    }

    public Booking cancel(int id, User user) {
        Booking b = getFor(id, user);
        Room r = rooms.get(b.getRoomNumber());
        r.lock().lock();
        try {
            if (!b.isActive()) throw new HotelException(409, "Booking " + id + " is already cancelled");
            if (b.getCheckIn().isBefore(LocalDate.now())) throw new HotelException(409, "Stays that have started cannot be cancelled");
            long daysAhead = ChronoUnit.DAYS.between(LocalDate.now(), b.getCheckIn());
            b.cancel(daysAhead >= FREE_CANCELLATION_DAYS ? 0 : b.getType().rate());
            return b;
        } finally { r.lock().unlock(); }
    }

    public Bill bill(int id, User user) {
        Booking b = getFor(id, user);
        if (!b.isActive()) throw new HotelException(409, "Booking " + id + " is cancelled; no bill to generate");
        return price(id, b.getCustomerName(), b.getRoomNumber(), b.getType(), b.nights(), b.getGuests(), b.hasBreakfast(), b.getPromoCode());
    }

    /** Price estimate for the booking form. */
    public Bill quote(RoomType type, LocalDate in, LocalDate out, int guests, boolean breakfast, String promo) {
        validateDates(in, out);
        if (guests < 1 || guests > type.capacity()) throw new HotelException(400, type.label() + " rooms sleep 1 to " + type.capacity() + " guests");
        return price(0, "", "", type, (int) ChronoUnit.DAYS.between(in, out), guests, breakfast, promo);
    }

    private Bill price(int id, String name, String room, RoomType type, int nights, int guests, boolean breakfast, String promo) {
        double charge = type.rate() * nights;
        double longStay = nights >= LONG_STAY_NIGHTS ? round2(charge * LONG_STAY_DISCOUNT) : 0;
        double promoDisc = round2(charge * promoPercent(promo) / 100.0);
        double bf = breakfast ? BREAKFAST_PER_PERSON_NIGHT * guests * nights : 0;
        double taxable = charge - longStay - promoDisc + bf;
        double gst = round2(taxable * GST_RATE);
        return new Bill(id, name, room, type.label(), nights, guests, type.rate(), charge, bf, longStay, promoDisc,
            promo == null ? "" : promo.trim().toUpperCase(), gst, round2(taxable + gst));
    }

    private static int promoPercent(String code) {
        if (code == null || code.isBlank()) return 0;
        Integer p = PROMOS.get(code.trim().toUpperCase());
        if (p == null) throw new HotelException(400, "Invalid promo code");
        return p;
    }

    public void setMaintenance(String roomNo, boolean on) {
        Room r = rooms.get(roomNo);
        if (r == null) throw new HotelException(404, "Room " + roomNo + " not found");
        r.lock().lock();
        try { r.setMaintenance(on); } finally { r.lock().unlock(); }
    }

    /** Admins see every booking; guests see only their own. */
    public List<Booking> bookingsFor(User user) {
        List<Booking> l = new ArrayList<>();
        for (Booking b : bookings.values()) if (user.isAdmin() || b.getOwner().equals(user.getUsername())) l.add(b);
        l.sort(Comparator.comparingInt(Booking::getId).reversed());
        return l;
    }

    public Booking getFor(int id, User user) {
        Booking b = bookings.get(id);
        if (b == null || (!user.isAdmin() && !b.getOwner().equals(user.getUsername())))
            throw new HotelException(404, "Booking " + id + " not found");
        return b;
    }

    /** Dashboard numbers for admins. */
    public String stats() {
        LocalDate today = LocalDate.now();
        int occupied = 0, maint = 0;
        Map<RoomType, int[]> by = new EnumMap<>(RoomType.class);
        for (RoomType t : RoomType.values()) by.put(t, new int[2]);
        for (Room r : rooms.values()) {
            by.get(r.getType())[0]++;
            if (r.isMaintenance()) maint++;
            else if (r.isOccupied(today)) { occupied++; by.get(r.getType())[1]++; }
        }
        int active = 0, cancelled = 0;
        double revenue = 0;
        for (Booking b : bookings.values()) {
            if (b.isActive()) { active++; revenue += price(b.getId(), "", "", b.getType(), b.nights(), b.getGuests(), b.hasBreakfast(), b.getPromoCode()).total(); }
            else { cancelled++; revenue += b.getCancellationFee(); }
        }
        int usable = rooms.size() - maint;
        List<String> types = new ArrayList<>();
        by.forEach((t, c) -> types.add(Json.obj("label", t.label(), "rooms", c[0], "occupied", c[1])));
        return Json.obj("totalRooms", rooms.size(), "maintenanceRooms", maint, "occupiedTonight", occupied,
            "occupancyPct", usable == 0 ? 0 : Math.round(occupied * 100.0 / usable), "activeBookings", active,
            "cancelledBookings", cancelled, "revenue", round2(revenue), "byType", Json.raw(Json.array(types)));
    }

    private void validateDates(LocalDate in, LocalDate out) {
        if (in == null || out == null) throw new HotelException(400, "Check-in and check-out dates are required");
        if (in.isBefore(LocalDate.now())) throw new HotelException(400, "Check-in cannot be in the past");
        if (!out.isAfter(in)) throw new HotelException(400, "Check-out must be after check-in");
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
