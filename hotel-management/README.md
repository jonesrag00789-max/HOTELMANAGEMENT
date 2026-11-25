# Hotel Room Reservation & Management System

Java 17+ backend (JDK built-in `com.sun.net.httpserver`, zero dependencies) + HTML/CSS/JS front end.

## Run
    ./run.sh            # or: javac -d out $(find src -name "*.java") && java -cp out com.hotel.Main
    open http://localhost:8080

## Concurrency check
    java -cp out com.hotel.demo.ConcurrencyDemo

## Packages
- com.hotel.model     – Room, RoomType, Booking, Bill
- com.hotel.service   – HotelService (reserve / cancel / availability / bill, per-room locking)
- com.hotel.server    – ApiHandler (REST), StaticHandler (web files)
- com.hotel.exception – HotelException
- com.hotel.util      – Json helpers
- com.hotel.demo      – ConcurrencyDemo

## REST API
GET  /api/rooms · GET /api/availability?checkIn&checkOut&type · GET/POST /api/bookings
POST /api/bookings/{id}/cancel · GET /api/bookings/{id}/bill

Rates (INR/night): Standard 2500, Deluxe 4500, Suite 9000. 10% off for 5+ nights, 12% GST.

## v2: login and extra features
- Sign in / create account (PBKDF2-hashed passwords, HttpOnly session cookie). Demo admin: `admin` / `admin123`.
- Roles: guests see and manage only their own bookings; admins see all bookings, the dashboard and can put rooms into maintenance.
- Guest count (checked against room capacity), breakfast add-on, promo codes (`WELCOME10`, `FESTIVE15`), live price quote.
- Cancellation policy: free until 2 days before check-in, then one night's rate is charged.
- Booking search and status filter.
