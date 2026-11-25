package com.hotel.model;

import com.hotel.util.Json;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class Booking {
    public enum Status { CONFIRMED, CANCELLED }

    private final int id, guests;
    private final String owner, customerName, phone, roomNumber, promoCode;
    private final boolean breakfast;
    private final RoomType type;
    private final LocalDate checkIn, checkOut;
    private final LocalDateTime createdAt = LocalDateTime.now().withNano(0);
    private volatile Status status = Status.CONFIRMED;
    private volatile double cancellationFee;

    public Booking(int id, String owner, String customerName, String phone, Room room, LocalDate checkIn,
                   LocalDate checkOut, int guests, boolean breakfast, String promoCode) {
        this.id = id; this.owner = owner; this.customerName = customerName; this.phone = phone;
        this.roomNumber = room.getNumber(); this.type = room.getType();
        this.checkIn = checkIn; this.checkOut = checkOut;
        this.guests = guests; this.breakfast = breakfast; this.promoCode = promoCode == null ? "" : promoCode;
    }

    public int getId() { return id; }
    public int getGuests() { return guests; }
    public String getOwner() { return owner; }
    public String getCustomerName() { return customerName; }
    public String getRoomNumber() { return roomNumber; }
    public String getPromoCode() { return promoCode; }
    public boolean hasBreakfast() { return breakfast; }
    public RoomType getType() { return type; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public Status getStatus() { return status; }
    public double getCancellationFee() { return cancellationFee; }
    public void cancel(double fee) { cancellationFee = fee; status = Status.CANCELLED; }
    public boolean isActive() { return status == Status.CONFIRMED; }
    public int nights() { return (int) ChronoUnit.DAYS.between(checkIn, checkOut); }
    public boolean overlaps(LocalDate in, LocalDate out) { return in.isBefore(checkOut) && out.isAfter(checkIn); }

    public String toJson() {
        return Json.obj("id", id, "owner", owner, "customerName", customerName, "phone", phone, "room", roomNumber,
            "type", type.name(), "typeLabel", type.label(), "checkIn", checkIn.toString(),
            "checkOut", checkOut.toString(), "nights", nights(), "guests", guests, "breakfast", breakfast,
            "promoCode", promoCode, "status", status.name(), "cancellationFee", cancellationFee,
            "createdAt", createdAt.toString());
    }
}
