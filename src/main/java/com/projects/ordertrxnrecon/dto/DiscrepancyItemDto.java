package com.projects.ordertrxnrecon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyItemDto {
    private String id;
    private String orderId;
    private String transactionRef;
    private String customerEmail;
    private String discrepancyType;
    private String severity;
    private BigDecimal orderAmount;
    private BigDecimal paymentAmount;
    private BigDecimal difference;
    private String orderStatus;
    private String paymentStatus;
    private String currency;
    private String details;
    private BigDecimal moneyAtRisk;
}
