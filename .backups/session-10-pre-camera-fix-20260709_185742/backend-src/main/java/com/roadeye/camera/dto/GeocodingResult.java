package com.roadeye.camera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodingResult {
    private String displayName;
    private Double latitude;
    private Double longitude;
    private String placeId;
    private String category;
    private String type;
    private String country;
    private String state;
    private String district;
    private String municipality;
    private String road;
    private String houseNumber;
    private String postcode;
    private Double importance;
    private List<String> boundingBox;
}
