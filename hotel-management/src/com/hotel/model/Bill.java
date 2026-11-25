package com.hotel.model;

import com.hotel.util.Json;

public record Bill(int bookingId, String customerName, String room, String typeLabel, int nights, int guests,
                   double rate, double roomCharge, double breakfast, double longStayDiscount,
                   double promoDiscount, String promoCode, double gst, double total) {
    public String toJson() {
        return Json.obj("bookingId", bookingId, "customerName", customerName, "room", room, "typeLabel", typeLabel,
            "nights", nights, "guests", guests, "rate", rate, "roomCharge", roomCharge, "breakfast", breakfast,
            "longStayDiscount", longStayDiscount, "promoDiscount", promoDiscount, "promoCode", promoCode,
            "gst", gst, "total", total);
    }
}
