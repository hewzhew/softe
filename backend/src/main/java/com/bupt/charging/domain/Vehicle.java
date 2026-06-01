package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String carId;

    @Column(nullable = false)
    private double carCapacity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserAccount owner;

    protected Vehicle() {
    }

    public Vehicle(String carId, double carCapacity, UserAccount owner) {
        this.carId = carId;
        this.carCapacity = carCapacity;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public String getCarId() {
        return carId;
    }

    public double getCarCapacity() {
        return carCapacity;
    }

    public UserAccount getOwner() {
        return owner;
    }
}
