package com.courier.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "regions")
public class Region {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(unique = true)
    private String code;
    private String area;
    private String contact;
    private String district;
    private String state;
    private String country;
    private String accountType;
    private String parentDistrict;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(name = "password", nullable = false)
    private String passwordHash;
    @Transient
    private String password;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getArea() { return area; }
    public void setArea(String value) { area = value; }
    public String getContact() { return contact; }
    public void setContact(String value) { contact = value; }
    public String getDistrict() { return district; }
    public void setDistrict(String value) { district = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getCountry() { return country; }
    public void setCountry(String value) { country = value; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String value) { accountType = value; }
    public String getParentDistrict() { return parentDistrict; }
    public void setParentDistrict(String value) { parentDistrict = value; }
}
