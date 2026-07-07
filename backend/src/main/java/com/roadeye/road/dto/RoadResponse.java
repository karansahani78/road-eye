package com.roadeye.road.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadResponse {
    private UUID id;
    private String name;
    private String code;
    private String roadType;
    private String province;
    private String district;
    private Double length;
    private Integer speedLimit;
}
