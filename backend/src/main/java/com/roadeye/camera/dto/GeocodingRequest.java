package com.roadeye.camera.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodingRequest {
    @NotBlank(message = "Query is required")
    private String query;

    private Integer limit;
}
