package com.comforthub.backoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDto {
    private String id;
    private String name;
    private String description;
    private String type; // "question" or "dashboard"
    private String embedUrl;
}
