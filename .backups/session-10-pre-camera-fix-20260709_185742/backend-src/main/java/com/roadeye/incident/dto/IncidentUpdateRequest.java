package com.roadeye.incident.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncidentUpdateRequest {
    private String status;
    private String assignedTo;
    private String resolutionNotes;
    private String description;
}
