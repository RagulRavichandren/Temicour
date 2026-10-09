package com.courier.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "admin_accounts")
public class AdminAccount {
    @Id
    private Long id = 1L;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
