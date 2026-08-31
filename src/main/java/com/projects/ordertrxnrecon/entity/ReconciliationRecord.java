package com.projects.ordertrxnrecon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 50)
    private String reconId;

    @Column(length = 100)
    private String orderId;

    @Column(length = 255)
    private String transactionRef;

    @Column(length = 255)
    private String customerEmail;

    @Column(length = 50, nullable = false)
    private String discrepancyType;

    @Column(length = 20, nullable = false)
    private String severity;

    @Column(precision = 12, scale = 2)
    private BigDecimal orderAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal paymentAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal difference;

    @Column(length = 50)
    private String orderStatus;

    @Column(length = 50)
    private String paymentStatus;

    @Column(length = 20)
    private String currency;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(precision = 12, scale = 2)
    private BigDecimal moneyAtRisk;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
