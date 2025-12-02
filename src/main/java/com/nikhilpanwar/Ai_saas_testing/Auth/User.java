package com.nikhilpanwar.Ai_saas_testing.Auth;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String firebaseUid;

    private String email;
    private String name;

    // ✅ Binary Data (Photo) Store karne ke liye
    // Postgres ke liye 'bytea' column definition best hai
    @Column(length = 5000000)
    private byte[] profilePicture;

    public User() {}

    public User(String firebaseUid, String email, String name) {
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.name = name;
    }

    // Getters and Setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public byte[] getProfilePicture() { return profilePicture; }
    public void setProfilePicture(byte[] profilePicture) { this.profilePicture = profilePicture; }
}