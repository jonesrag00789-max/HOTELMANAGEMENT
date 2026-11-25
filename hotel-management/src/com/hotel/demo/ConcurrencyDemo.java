package com.hotel.demo;

import com.hotel.exception.HotelException;
import com.hotel.model.*;
import com.hotel.service.HotelService;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** 60 customers race for the 4 Deluxe rooms on the same dates: exactly 4 must succeed, each with a different room. */
public class ConcurrencyDemo {
    public static void main(String[] args) throws Exception {
        HotelService s = new HotelService();
        LocalDate in = LocalDate.now().plusDays(3), out = in.plusDays(2);
        int customers = 60;
        ExecutorService pool = Executors.newFixedThreadPool(customers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            final int n = i;
            results.add(pool.submit(() -> {
                start.await();
                try { return s.reserve("demo", "Guest" + n, "9876543210", RoomType.DELUXE, in, out, 1, false, null).getRoomNumber(); }
                catch (HotelException e) { return null; }
            }));
        }
        start.countDown();
        Set<String> rooms = new HashSet<>();
        int success = 0;
        for (Future<String> f : results) { String r = f.get(); if (r != null) { success++; rooms.add(r); } }
        pool.shutdown();
        System.out.println("Successful bookings: " + success + " (expected 4)");
        System.out.println("Distinct rooms used: " + rooms.size() + " " + rooms);
        System.out.println(success == 4 && rooms.size() == 4 ? "PASS: no double-booking" : "FAIL");
    }
}
