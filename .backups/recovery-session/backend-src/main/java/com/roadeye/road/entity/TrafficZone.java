package com.roadeye.road.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "traffic_zones")
public class TrafficZone extends BaseEntity {
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "zone_type")
    private String zoneType;

    @Column(name = "speed_limit")
    private Integer speedLimit;

    @Column(name = "restricted")
    private boolean restricted;
}
