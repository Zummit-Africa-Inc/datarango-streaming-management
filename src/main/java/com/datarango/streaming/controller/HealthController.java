package com.datarango.streaming.controller;

import com.datarango.streaming.service.AzureBlobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    private final AzureBlobService azureBlobService;

    public HealthController(AzureBlobService azureBlobService) {
        this.azureBlobService = azureBlobService;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        boolean storageHealthy = checkAzureStorage();
        String status = storageHealthy ? "UP" : "DOWN";
        return ResponseEntity.ok(Map.of(
                "service", "streaming-service",
                "status", status
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthDetails = new HashMap<>();
        boolean storageHealthy = checkAzureStorage();

        healthDetails.put("status", storageHealthy ? "UP" : "DOWN");
        healthDetails.put("components", Map.of(
                "azureStorage", Map.of("status", storageHealthy ? "UP" : "DOWN")
        ));

        if (storageHealthy) {
            return ResponseEntity.ok(healthDetails);
        } else {
            return ResponseEntity.status(503).body(healthDetails);
        }
    }

    private boolean checkAzureStorage() {
        try {
            azureBlobService.getContainerClient().exists();
            return true;
        } catch (Exception e) {
            logger.error("Azure Storage health check failed", e);
            return false;
        }
    }
}
