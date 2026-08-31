package com.projects.ordertrxnrecon.controller;

import com.projects.ordertrxnrecon.dto.UploadResponse;
import com.projects.ordertrxnrecon.entity.User;
import com.projects.ordertrxnrecon.repository.UserRepository;
import com.projects.ordertrxnrecon.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final UploadService uploadService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadPayments(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws Exception {

        Long userId = resolveUserId(authentication);
        UploadResponse response = uploadService.uploadPayments(file, userId);
        return ResponseEntity.ok(response);
    }

    private Long resolveUserId(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}
