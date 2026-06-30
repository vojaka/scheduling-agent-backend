package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final BubbleClient bubbleClient;

    public CompanyController(BubbleClient bubbleClient) {
        this.bubbleClient = bubbleClient;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCompanies() {
        return ResponseEntity.ok(bubbleClient.fetchCompanies());
    }
}
