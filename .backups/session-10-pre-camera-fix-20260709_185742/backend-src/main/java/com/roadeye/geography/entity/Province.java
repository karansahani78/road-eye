package com.roadeye.geography.entity;

import com.roadeye.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "provinces")
public class Province extends BaseEntity {
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;
}
