package com.projects.ordertrxnrecon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationSummaryDto {
    private long totalOrders;
    private long totalPayments;
    private long invalidOrdersCount;
    private long invalidPaymentsCount;
    private BigDecimal totalOrderValue;
    private BigDecimal totalPaymentValue;
    private BigDecimal totalGatewayFees;
    private BigDecimal totalNetSettledValue;
    private BigDecimal totalValueReconciled;
    private BigDecimal totalValueInDispute;
    private BigDecimal totalMoneyAtRisk;
    private List<DiscrepancySummaryDto> discrepancyBreakdown;
    private List<DiscrepancyItemDto> discrepancies;
}
