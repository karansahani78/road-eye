package com.roadeye.trafficmap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficMapResponse {
    private SummaryDTO summary;
    private List<MapCameraDTO> cameras;
    private List<MapViolationDTO> violations;
    private List<MapAlertDTO> alerts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryDTO {
        private int totalCameras;
        private int mappedCameras;
        private int unmappedCameras;
        private int onlineCameras;
        private int offlineCameras;
        private int degradedCameras;
        private int activeStreams;
        private int currentActiveVehicles;
        private int activeViolations;
        private int unacknowledgedAlerts;
    }
}
