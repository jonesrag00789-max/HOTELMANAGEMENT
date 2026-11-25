package com.hotel.model;

import com.hotel.util.Json;

public class User {
    public enum Role { ADMIN, GUEST }

    private final String username, fullName, phone;
    private final byte[] salt, hash;
    private final Role role;

    public User(String username, String fullName, String phone, byte[] salt, byte[] hash, Role role) {
        this.username = username; this.fullName = fullName; this.phone = phone;
        this.salt = salt; this.hash = hash; this.role = role;
    }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public byte[] getSalt() { return salt; }
    public byte[] getHash() { return hash; }
    public Role getRole() { return role; }
    public boolean isAdmin() { return role == Role.ADMIN; }

    public String toJson() {
        return Json.obj("username", username, "fullName", fullName, "phone", phone, "role", role.name());
    }
}
