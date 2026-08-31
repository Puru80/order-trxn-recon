package com.projects.ordertrxnrecon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 100)
    private String transactionRef;

    @Column(length = 100)
    private String processedAt;

    @Column(length = 100)
    private String orderReference;

    @Column(length = 10)
    private String currency;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 12, scale = 2)
    private BigDecimal fee;

    @Column(precision = 12, scale = 2)
    private BigDecimal netSettled;

    @Column(length = 50)
    private String type;

    @Column(length = 50)
    private String status;

    @Column(nullable = false, length = 20)
    private String rowStatus;

    @Column(columnDefinition = "TEXT")
    private String errors;

    @Column(columnDefinition = "TEXT")
    private String rawRow;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
