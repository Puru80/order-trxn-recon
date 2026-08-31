package com.projects.ordertrxnrecon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 100)
    private String orderId;

    @Column(length = 100)
    private String orderDate;

    @Column(length = 255)
    private String customerEmail;

    @Column(length = 10)
    private String currency;

    @Column(precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(precision = 12, scale = 2)
    private BigDecimal netAmount;

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
