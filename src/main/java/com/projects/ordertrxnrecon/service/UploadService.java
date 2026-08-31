package com.projects.ordertrxnrecon.service;

import com.projects.ordertrxnrecon.dto.UploadResponse;
import com.projects.ordertrxnrecon.entity.Order;
import com.projects.ordertrxnrecon.entity.Payment;
import com.projects.ordertrxnrecon.repository.OrderRepository;
import com.projects.ordertrxnrecon.repository.PaymentRepository;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CsvParserService csvParserService;

    @Transactional
    public UploadResponse uploadOrders(MultipartFile file, Long userId) throws IOException, CsvValidationException {
        validateFile(file);

        String csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<Order> orders = csvParserService.parseOrders(csvContent, userId);

        orderRepository.deleteByUserId(userId);
        orderRepository.saveAll(orders);

        long validCount = orders.stream().filter(o -> "VALID".equals(o.getRowStatus())).count();
        int invalidCount = orders.size() - (int) validCount;

        return UploadResponse.builder()
                .filename(file.getOriginalFilename())
                .type("ORDER")
                .totalRows(orders.size())
                .validRows((int) validCount)
                .invalidRows(invalidCount)
                .message(invalidCount == 0
                        ? "File uploaded and processed successfully"
                        : "File uploaded with " + invalidCount + " invalid row(s)")
                .build();
    }

    @Transactional
    public UploadResponse uploadPayments(MultipartFile file, Long userId) throws IOException, CsvValidationException {
        validateFile(file);

        String csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<Payment> payments = csvParserService.parsePayments(csvContent, userId);

        paymentRepository.deleteByUserId(userId);
        paymentRepository.saveAll(payments);

        long validCount = payments.stream().filter(p -> "VALID".equals(p.getRowStatus())).count();
        int invalidCount = payments.size() - (int) validCount;

        return UploadResponse.builder()
                .filename(file.getOriginalFilename())
                .type("PAYMENT")
                .totalRows(payments.size())
                .validRows((int) validCount)
                .invalidRows(invalidCount)
                .message(invalidCount == 0
                        ? "File uploaded and processed successfully"
                        : "File uploaded with " + invalidCount + " invalid row(s)")
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new RuntimeException("Only CSV files are supported");
        }
    }
}
