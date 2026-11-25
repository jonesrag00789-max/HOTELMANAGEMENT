package com.hotel;

import com.hotel.server.*;
import com.hotel.service.AuthService;
import com.hotel.service.HotelService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HotelService service = new HotelService();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(service, new AuthService()));
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(32)); // many customers served in parallel
        server.start();
        System.out.println("Hotel system running at http://localhost:" + port + "  (admin login: admin / admin123)");
    }
}
