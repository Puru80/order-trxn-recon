package com.projects.ordertrxnrecon.controller;

import com.projects.ordertrxnrecon.dto.DiscrepancyItemDto;
import com.projects.ordertrxnrecon.dto.PaginatedResponseDto;
import com.projects.ordertrxnrecon.dto.ReconciliationSummaryDto;
import com.projects.ordertrxnrecon.entity.User;
import com.projects.ordertrxnrecon.repository.UserRepository;
import com.projects.ordertrxnrecon.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ReconciliationSummaryDto> getReconciliation(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        ReconciliationSummaryDto summary = reconciliationService.processAndSaveReconciliation(userId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping
    public ResponseEntity<ReconciliationSummaryDto> processReconciliation(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        ReconciliationSummaryDto summary = reconciliationService.processAndSaveReconciliation(userId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/summary")
    public ResponseEntity<ReconciliationSummaryDto> getSummary(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        ReconciliationSummaryDto summary = reconciliationService.getSummary(userId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/discrepancies")
    public ResponseEntity<PaginatedResponseDto<DiscrepancyItemDto>> getPaginatedDiscrepancies(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc") String sortDir,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        PaginatedResponseDto<DiscrepancyItemDto> response = reconciliationService.getPaginatedDiscrepancies(
                userId, page, size, search, type, severity, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "severity", required = false) String severity,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        byte[] csvBytes = reconciliationService.exportDiscrepanciesCsv(userId, search, type, severity);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reconciliation_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }

    private Long resolveUserId(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
