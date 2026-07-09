package com.roadeye.road.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "roads")
public class Road extends BaseEntity {
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "road_type")
    private String roadType;

    @Column(name = "province")
    private String province;

    @Column(name = "district")
    private String district;

    @Column(name = "length_km")
    private Double length;

    @Column(name = "default_speed_limit")
    private Integer defaultSpeedLimit;
}
