package com.hotel.service;

import com.hotel.exception.HotelException;
import com.hotel.model.User;
import com.hotel.model.User.Role;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Registration, PBKDF2 password hashing and in-memory session tokens. */
public class AuthService {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, User> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public AuthService() { users.put("admin", build("admin", "admin123", "Hotel Manager", "", Role.ADMIN)); }

    public User register(String username, String password, String fullName, String phone) {
        username = username == null ? "" : username.trim().toLowerCase();
        if (!username.matches("[a-z0-9_]{3,20}")) throw new HotelException(400, "Username: 3-20 letters, digits or underscore");
        if (password == null || password.length() < 6) throw new HotelException(400, "Password must be at least 6 characters");
        if (fullName == null || fullName.isBlank()) throw new HotelException(400, "Full name is required");
        if (phone == null || !phone.matches("[0-9+\\- ]{7,15}")) throw new HotelException(400, "Enter a valid phone number");
        User u = build(username, password, fullName.trim(), phone.trim(), Role.GUEST);
        if (users.putIfAbsent(username, u) != null) throw new HotelException(409, "Username already taken");
        return u;
    }

    public String login(String username, String password) {
        User u = username == null ? null : users.get(username.trim().toLowerCase());
        if (u == null || password == null || !MessageDigest.isEqual(u.getHash(), pbkdf2(password, u.getSalt())))
            throw new HotelException(401, "Invalid username or password");
        byte[] t = new byte[32];
        random.nextBytes(t);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(t);
        sessions.put(token, u);
        return token;
    }

    public User userFor(String token) { return token == null ? null : sessions.get(token); }
    public void logout(String token) { if (token != null) sessions.remove(token); }

    private User build(String un, String pw, String name, String phone, Role role) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return new User(un, name, phone, salt, pbkdf2(pw, salt), role);
    }

    private static byte[] pbkdf2(String pw, byte[] salt) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(pw.toCharArray(), salt, 60000, 256)).getEncoded();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
