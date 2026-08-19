package com.vplmqa.e2e.controllers;

import com.vplmqa.e2e.dto.GherkinE2ERunRequest;
import com.vplmqa.e2e.services.GherkinE2EAutomationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gherkin/e2e")
public class GherkinE2EController {

    private final GherkinE2EAutomationService automationService;

    public GherkinE2EController(GherkinE2EAutomationService automationService) {
        this.automationService = automationService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@Valid @RequestBody GherkinE2ERunRequest request) {
        return ResponseEntity.ok(automationService.run(request));
    }
}
