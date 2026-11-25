package com.hotel.server;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.nio.file.*;

/** Serves the front-end files from the ./web directory. */
public class StaticHandler implements HttpHandler {
    private final Path root = Paths.get("web").toAbsolutePath().normalize();

    @Override public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        Path file = root.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            byte[] msg = "Not found".getBytes();
            ex.sendResponseHeaders(404, msg.length);
            ex.getResponseBody().write(msg);
            ex.close();
            return;
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8" : name.endsWith(".css") ? "text/css"
            : name.endsWith(".js") ? "text/javascript" : "application/octet-stream";
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }
}
