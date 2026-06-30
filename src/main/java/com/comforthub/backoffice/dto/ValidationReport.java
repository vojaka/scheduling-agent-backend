package com.comforthub.backoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationReport {
    private boolean valid = true;
    private List<ValidationIssue> issues = new ArrayList<>();

    public void addIssue(ValidationIssue issue) {
        this.issues.add(issue);
        if (issue.getSeverity() == ValidationIssue.Severity.ERROR) {
            this.valid = false;
        }
    }
}
