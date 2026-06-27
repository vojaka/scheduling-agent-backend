package com.example.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue {
    public enum Severity {
        ERROR, WARNING
    }
    
    private Severity severity;
    private String rule;
    private String assignedUser;
    private String message;
}
