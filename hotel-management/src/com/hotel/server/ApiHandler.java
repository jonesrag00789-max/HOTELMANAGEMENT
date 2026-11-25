package com.hotel.server;

import com.hotel.exception.HotelException;
import com.hotel.model.*;
import com.hotel.service.AuthService;
import com.hotel.service.HotelService;
import com.hotel.util.Json;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/** REST endpoints under /api. Everything except login/register requires a session cookie. */
public class ApiHandler implements HttpHandler {
    private record Response(int status, String body, String cookie) {
        Response(int status, String body) { this(status, body, null); }
    }
    private final HotelService service;
    private final AuthService auth;

    public ApiHandler(HotelService service, AuthService auth) { this.service = service; this.auth = auth; }

    @Override public void handle(HttpExchange ex) throws IOException {
        Response res;
        try {
            String token = sessionToken(ex);
            res = route(ex.getRequestMethod(), ex.getRequestURI().getPath(), query(ex),
                new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), token, auth.userFor(token));
        } catch (HotelException e) {
            res = new Response(e.getStatus(), Json.obj("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            res = new Response(500, Json.obj("error", "Internal server error"));
        }
        byte[] out = res.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (res.cookie() != null) ex.getResponseHeaders().add("Set-Cookie", res.cookie());
        ex.sendResponseHeaders(res.status(), out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private Response route(String method, String path, Map<String, String> q, String body, String token, User user) {
        String[] p = path.split("/"); // ["", "api", "bookings", "1001", "bill"]
        String r = p.length > 2 ? p[2] : "";
        boolean get = method.equals("GET"), post = method.equals("POST");
        Map<String, String> f = post ? Json.parse(body) : Map.of();

        if (post && r.equals("login")) return session(auth.login(f.get("username"), f.get("password")), 200);
        if (post && r.equals("register")) {
            auth.register(f.get("username"), f.get("password"), f.get("fullName"), f.get("phone"));
            return session(auth.login(f.get("username"), f.get("password")), 201);
        }
        if (post && r.equals("logout")) {
            auth.logout(token);
            return new Response(200, Json.obj("ok", true), "session=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
        }
        if (user == null) throw new HotelException(401, "Please sign in");

        if (get && r.equals("me")) return ok(user.toJson());
        if (get && r.equals("rooms")) return ok(rooms());
        if (post && r.equals("rooms") && p.length == 5 && p[4].equals("maintenance")) {
            requireAdmin(user);
            service.setMaintenance(p[3], "true".equalsIgnoreCase(f.get("on")));
            return ok(rooms());
        }
        if (get && r.equals("availability")) return ok(availability(q));
        if (get && r.equals("quote")) {
            return ok(service.quote(RoomType.parse(String.valueOf(q.get("type"))), date(q.get("checkIn")), date(q.get("checkOut")),
                num(q.get("guests")), "true".equalsIgnoreCase(q.get("breakfast")), q.get("promo")).toJson());
        }
        if (get && r.equals("stats")) { requireAdmin(user); return ok(service.stats()); }
        if (r.equals("bookings")) {
            if (p.length == 3 && get)
                return ok(Json.array(service.bookingsFor(user).stream().map(Booking::toJson).collect(Collectors.toList())));
            if (p.length == 3 && post) return new Response(201, create(user, f).toJson());
            if (p.length == 5 && post && p[4].equals("cancel")) return ok(service.cancel(id(p[3]), user).toJson());
            if (p.length == 5 && get && p[4].equals("bill")) return ok(service.bill(id(p[3]), user).toJson());
        }
        throw new HotelException(404, "Not found");
    }

    private Booking create(User u, Map<String, String> f) {
        String name = f.get("customerName") == null || f.get("customerName").isBlank() ? u.getFullName() : f.get("customerName");
        String phone = f.get("phone") == null || f.get("phone").isBlank() ? u.getPhone() : f.get("phone");
        return service.reserve(u.getUsername(), name, phone, RoomType.parse(String.valueOf(f.get("type"))),
            date(f.get("checkIn")), date(f.get("checkOut")), num(f.get("guests")),
            "true".equalsIgnoreCase(f.get("breakfast")), f.get("promo"));
    }

    private String availability(Map<String, String> q) {
        RoomType t = q.get("type") == null || q.get("type").isBlank() ? null : RoomType.parse(q.get("type"));
        List<Room> list = service.findAvailable(date(q.get("checkIn")), date(q.get("checkOut")), t);
        return Json.array(list.stream().map(this::roomJson).collect(Collectors.toList()));
    }

    private String rooms() { return Json.array(service.getRooms().stream().map(this::roomJson).collect(Collectors.toList())); }

    private String roomJson(Room r) {
        LocalDate today = LocalDate.now();
        String status = r.isMaintenance() ? "MAINTENANCE" : r.isOccupied(today) ? "OCCUPIED" : "VACANT";
        return Json.obj("number", r.getNumber(), "type", r.getType().name(), "typeLabel", r.getType().label(),
            "floor", r.getFloor(), "rate", r.getType().rate(), "capacity", r.getType().capacity(), "status", status);
    }

    private Response session(String token, int status) {
        return new Response(status, auth.userFor(token).toJson(), "session=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800");
    }

    private static void requireAdmin(User u) { if (!u.isAdmin()) throw new HotelException(403, "Admin access required"); }
    private static Response ok(String json) { return new Response(200, json); }
    private static int num(String s) { try { return s == null ? 1 : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { throw new HotelException(400, "Invalid number: " + s); } }
    private static int id(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { throw new HotelException(400, "Invalid booking id"); } }
    private static LocalDate date(String s) {
        if (s == null) return null;
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { throw new HotelException(400, "Invalid date: " + s); }
    }
    private static String sessionToken(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Cookie");
        if (h == null) return null;
        for (String c : h.split(";")) { c = c.trim(); if (c.startsWith("session=")) return c.substring(8); }
        return null;
    }
    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            String[] a = kv.split("=", 2);
            m.put(URLDecoder.decode(a[0], StandardCharsets.UTF_8), a.length > 1 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
}
