package com.roadeye.geography.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "municipalities")
public class Municipality extends BaseEntity {
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id", nullable = false)
    private District district;
}
