package com.projects.ordertrxnrecon.service;

import com.projects.ordertrxnrecon.dto.DiscrepancyItemDto;
import com.projects.ordertrxnrecon.dto.DiscrepancySummaryDto;
import com.projects.ordertrxnrecon.dto.PaginatedResponseDto;
import com.projects.ordertrxnrecon.dto.ReconciliationSummaryDto;
import com.projects.ordertrxnrecon.entity.Order;
import com.projects.ordertrxnrecon.entity.Payment;
import com.projects.ordertrxnrecon.entity.ReconciliationRecord;
import com.projects.ordertrxnrecon.repository.OrderRepository;
import com.projects.ordertrxnrecon.repository.PaymentRepository;
import com.projects.ordertrxnrecon.repository.ReconciliationRecordRepository;
import com.projects.ordertrxnrecon.repository.specification.ReconciliationSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;

    private static final BigDecimal ROUNDING_THRESHOLD = new BigDecimal("0.05");

    /**
     * PROCESS AND STORE RECONCILIATION DATA IN DATABASE.
     * Triggered by GET /api/reconciliation or POST /api/reconciliation.
     */
    @Transactional
    public ReconciliationSummaryDto processAndSaveReconciliation(Long userId) {
        List<Order> allOrders = orderRepository.findByUserId(userId);
        List<Payment> allPayments = paymentRepository.findByUserId(userId);

        long invalidOrdersCount = allOrders.stream().filter(o -> "INVALID".equalsIgnoreCase(o.getRowStatus())).count();
        long invalidPaymentsCount = allPayments.stream().filter(p -> "INVALID".equalsIgnoreCase(p.getRowStatus())).count();

        List<Order> validOrders = allOrders.stream()
                .filter(o -> "VALID".equalsIgnoreCase(o.getRowStatus()))
                .toList();

        List<Payment> validPayments = allPayments.stream()
                .filter(p -> "VALID".equalsIgnoreCase(p.getRowStatus()))
                .toList();

        // Map valid payments by clean order reference
        Map<String, List<Payment>> paymentsByRef = new HashMap<>();
        for (Payment p : validPayments) {
            if (p.getOrderReference() != null && !p.getOrderReference().isBlank()) {
                String cleanRef = p.getOrderReference().trim().toUpperCase();
                paymentsByRef.computeIfAbsent(cleanRef, k -> new ArrayList<>()).add(p);
            }
        }

        List<DiscrepancyItemDto> items = new ArrayList<>();
        Set<String> matchedCleanOrderIds = new HashSet<>();
        int idCounter = 1;

        // 1. Match valid orders against payments
        for (Order order : validOrders) {
            String cleanId = order.getOrderId() != null ? order.getOrderId().trim().toUpperCase() : "";
            matchedCleanOrderIds.add(cleanId);

            List<Payment> matchingPayments = paymentsByRef.getOrDefault(cleanId, Collections.emptyList());
            items.add(buildOrderItem(idCounter++, order, matchingPayments));
        }

        // 2. Process orphan payments
        for (Map.Entry<String, List<Payment>> entry : paymentsByRef.entrySet()) {
            String cleanRef = entry.getKey();
            if (!matchedCleanOrderIds.contains(cleanRef)) {
                items.add(buildOrphanItem(idCounter++, cleanRef, entry.getValue()));
            }
        }

        // Clear existing DB recon records for this user
        reconciliationRecordRepository.deleteByUserId(userId);

        // Convert items to entities and save in database
        List<ReconciliationRecord> entities = items.stream()
                .map(item -> toEntity(userId, item))
                .collect(Collectors.toList());

        reconciliationRecordRepository.saveAll(entities);

        // Build and return summary response
        return buildSummaryFromItems(validOrders.size(), validPayments.size(), invalidOrdersCount, invalidPaymentsCount, validOrders, validPayments, items);
    }

    /**
     * READ ONLY FROM DATABASE: Fetch summary metrics & breakdown.
     */
    @Transactional(readOnly = true)
    public ReconciliationSummaryDto getSummary(Long userId) {
        List<ReconciliationRecord> dbRecords = reconciliationRecordRepository.findByUserId(userId);

        long totalOrders = orderRepository.countByUserIdAndRowStatus(userId, "VALID");
        long totalPayments = paymentRepository.countByUserIdAndRowStatus(userId, "VALID");
        long invalidOrdersCount = orderRepository.countByUserIdAndRowStatus(userId, "INVALID");
        long invalidPaymentsCount = paymentRepository.countByUserIdAndRowStatus(userId, "INVALID");

        List<DiscrepancyItemDto> items = dbRecords.stream().map(this::toDto).toList();

        List<Order> validOrders = orderRepository.findByUserId(userId).stream()
                .filter(o -> "VALID".equalsIgnoreCase(o.getRowStatus())).toList();
        List<Payment> validPayments = paymentRepository.findByUserId(userId).stream()
                .filter(p -> "VALID".equalsIgnoreCase(p.getRowStatus())).toList();

        ReconciliationSummaryDto summary = buildSummaryFromItems(
                (int) totalOrders, (int) totalPayments, invalidOrdersCount, invalidPaymentsCount, validOrders, validPayments, items);

        // Omit individual items from summary DTO for performance
        summary.setDiscrepancies(null);
        return summary;
    }

    /**
     * READ ONLY FROM DATABASE: Paginated discrepancies.
     */
    @Transactional(readOnly = true)
    public PaginatedResponseDto<DiscrepancyItemDto> getPaginatedDiscrepancies(
            Long userId, int page, int size, String search, String filterType, String filterSeverity, String sortBy, String sortDir) {

        Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, resolveSortField(sortBy));
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sort);

        Page<ReconciliationRecord> recordPage = reconciliationRecordRepository.findAll(
                ReconciliationSpecification.filterBy(userId, search, filterType, filterSeverity), pageable);

        List<DiscrepancyItemDto> content = recordPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PaginatedResponseDto.<DiscrepancyItemDto>builder()
                .content(content)
                .page(recordPage.getNumber())
                .size(recordPage.getSize())
                .totalElements(recordPage.getTotalElements())
                .totalPages(recordPage.getTotalPages())
                .first(recordPage.isFirst())
                .last(recordPage.isLast())
                .build();
    }

    /**
     * READ ONLY FROM DATABASE: Export CSV report of stored records.
     */
    @Transactional(readOnly = true)
    public byte[] exportDiscrepanciesCsv(Long userId, String search, String filterType, String filterSeverity) {
        List<ReconciliationRecord> dbRecords = reconciliationRecordRepository.findAll(
                ReconciliationSpecification.filterBy(userId, search, filterType, filterSeverity));

        StringBuilder sb = new StringBuilder();
        sb.append("\"Reconciliation ID\",\"Order ID\",\"Transaction Ref\",\"Customer Email\",\"Discrepancy Type\",\"Severity\",\"Order Amount\",\"Payment Amount\",\"Difference\",\"Order Status\",\"Payment Status\",\"Currency\",\"Money At Risk\",\"Details\"\n");

        for (ReconciliationRecord item : dbRecords) {
            sb.append(escapeCsv(item.getReconId())).append(",")
                    .append(escapeCsv(item.getOrderId())).append(",")
                    .append(escapeCsv(item.getTransactionRef())).append(",")
                    .append(escapeCsv(item.getCustomerEmail())).append(",")
                    .append(escapeCsv(item.getDiscrepancyType())).append(",")
                    .append(escapeCsv(item.getSeverity())).append(",")
                    .append(item.getOrderAmount() != null ? item.getOrderAmount() : "0.00").append(",")
                    .append(item.getPaymentAmount() != null ? item.getPaymentAmount() : "0.00").append(",")
                    .append(item.getFee() != null ? item.getFee() : "0.00").append(",")
                    .append(item.getNetSettled() != null ? item.getNetSettled() : "0.00").append(",")
                    .append(item.getDifference() != null ? item.getDifference() : "0.00").append(",")
                    .append(escapeCsv(item.getOrderStatus())).append(",")
                    .append(escapeCsv(item.getPaymentStatus())).append(",")
                    .append(escapeCsv(item.getCurrency())).append(",")
                    .append(item.getMoneyAtRisk() != null ? item.getMoneyAtRisk() : "0.00").append(",")
                    .append(escapeCsv(item.getDetails())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ReconciliationSummaryDto buildSummaryFromItems(
            int totalOrders, int totalPayments, long invalidOrdersCount, long invalidPaymentsCount,
            List<Order> validOrders, List<Payment> validPayments, List<DiscrepancyItemDto> items) {

        BigDecimal totalOrderValue = validOrders.stream()
                .map(o -> o.getNetAmount() != null ? o.getNetAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaymentValue = validPayments.stream()
                .map(p -> {
                    BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                    return "refund".equalsIgnoreCase(p.getType()) ? amt.negate() : amt;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGatewayFees = validPayments.stream()
                .map(p -> p.getFee() != null ? p.getFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNetSettledValue = validPayments.stream()
                .map(p -> p.getNetSettled() != null ? p.getNetSettled() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValueReconciled = items.stream()
                .filter(i -> "MATCHED".equals(i.getDiscrepancyType()) || "ROUNDING_DIFFERENCE".equals(i.getDiscrepancyType()))
                .map(DiscrepancyItemDto::getOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValueInDispute = items.stream()
                .filter(i -> !"MATCHED".equals(i.getDiscrepancyType()))
                .map(i -> "ORPHAN_PAYMENT".equals(i.getDiscrepancyType()) ? i.getPaymentAmount() : i.getOrderAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMoneyAtRisk = items.stream()
                .map(DiscrepancyItemDto::getMoneyAtRisk)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DiscrepancySummaryDto> breakdown = buildBreakdown(items);

        return ReconciliationSummaryDto.builder()
                .totalOrders(totalOrders)
                .totalPayments(totalPayments)
                .invalidOrdersCount(invalidOrdersCount)
                .invalidPaymentsCount(invalidPaymentsCount)
                .totalOrderValue(totalOrderValue.setScale(2, RoundingMode.HALF_UP))
                .totalPaymentValue(totalPaymentValue.setScale(2, RoundingMode.HALF_UP))
                .totalGatewayFees(totalGatewayFees.setScale(2, RoundingMode.HALF_UP))
                .totalNetSettledValue(totalNetSettledValue.setScale(2, RoundingMode.HALF_UP))
                .totalValueReconciled(totalValueReconciled.setScale(2, RoundingMode.HALF_UP))
                .totalValueInDispute(totalValueInDispute.setScale(2, RoundingMode.HALF_UP))
                .totalMoneyAtRisk(totalMoneyAtRisk.setScale(2, RoundingMode.HALF_UP))
                .discrepancyBreakdown(breakdown)
                .discrepancies(items)
                .build();
    }

    private ReconciliationRecord toEntity(Long userId, DiscrepancyItemDto dto) {
        return ReconciliationRecord.builder()
                .userId(userId)
                .reconId(dto.getId())
                .orderId(dto.getOrderId())
                .transactionRef(dto.getTransactionRef())
                .customerEmail(dto.getCustomerEmail())
                .discrepancyType(dto.getDiscrepancyType())
                .severity(dto.getSeverity())
                .orderAmount(dto.getOrderAmount())
                .paymentAmount(dto.getPaymentAmount())
                .fee(dto.getFee())
                .netSettled(dto.getNetSettled())
                .difference(dto.getDifference())
                .orderStatus(dto.getOrderStatus())
                .paymentStatus(dto.getPaymentStatus())
                .currency(dto.getCurrency())
                .details(dto.getDetails())
                .moneyAtRisk(dto.getMoneyAtRisk())
                .build();
    }

    private DiscrepancyItemDto toDto(ReconciliationRecord rec) {
        return DiscrepancyItemDto.builder()
                .id(rec.getReconId())
                .orderId(rec.getOrderId())
                .transactionRef(rec.getTransactionRef())
                .customerEmail(rec.getCustomerEmail())
                .discrepancyType(rec.getDiscrepancyType())
                .severity(rec.getSeverity())
                .orderAmount(rec.getOrderAmount())
                .paymentAmount(rec.getPaymentAmount())
                .fee(rec.getFee())
                .netSettled(rec.getNetSettled())
                .difference(rec.getDifference())
                .orderStatus(rec.getOrderStatus())
                .paymentStatus(rec.getPaymentStatus())
                .currency(rec.getCurrency())
                .details(rec.getDetails())
                .moneyAtRisk(rec.getMoneyAtRisk())
                .build();
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) return "id";
        switch (sortBy.toLowerCase()) {
            case "reconid": return "reconId";
            case "orderid": return "orderId";
            case "transactionref": return "transactionRef";
            case "discrepancytype": return "discrepancyType";
            case "severity": return "severity";
            case "orderamount": return "orderAmount";
            case "paymentamount": return "paymentAmount";
            case "fee": return "fee";
            case "netsettled": return "netSettled";
            case "difference": return "difference";
            case "moneyatrisk": return "moneyAtRisk";
            default: return "id";
        }
    }

    private DiscrepancyItemDto buildOrderItem(int id, Order order, List<Payment> payments) {
        BigDecimal orderAmt = order.getNetAmount() != null ? order.getNetAmount() : BigDecimal.ZERO;
        String orderStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "";
        String orderCurr = order.getCurrency() != null ? order.getCurrency().toUpperCase() : "USD";

        if (payments.isEmpty()) {
            boolean isCompleted = "completed".equalsIgnoreCase(orderStatus);
            BigDecimal risk = isCompleted ? orderAmt : BigDecimal.ZERO;
            return DiscrepancyItemDto.builder()
                    .id("REC-" + id)
                    .orderId(order.getOrderId())
                    .transactionRef("N/A")
                    .customerEmail(order.getCustomerEmail())
                    .discrepancyType("UNPAID_ORDER")
                    .severity("CRITICAL")
                    .orderAmount(orderAmt)
                    .paymentAmount(BigDecimal.ZERO)
                    .fee(BigDecimal.ZERO)
                    .netSettled(BigDecimal.ZERO)
                    .difference(orderAmt)
                    .orderStatus(order.getStatus())
                    .paymentStatus("N/A")
                    .currency(orderCurr)
                    .details("Order marked " + order.getStatus() + " in system but no payment transaction was found.")
                    .moneyAtRisk(risk)
                    .build();
        }

        String txRefs = payments.stream().map(Payment::getTransactionRef).collect(Collectors.joining(", "));
        List<Payment> charges = payments.stream().filter(p -> "charge".equalsIgnoreCase(p.getType())).collect(Collectors.toList());
        List<Payment> refunds = payments.stream().filter(p -> "refund".equalsIgnoreCase(p.getType())).collect(Collectors.toList());

        BigDecimal totalCharged = charges.stream().map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRefunded = refunds.stream().map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPaid = totalCharged.subtract(totalRefunded);

        BigDecimal totalFee = payments.stream().map(p -> p.getFee() != null ? p.getFee() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netSettled = netPaid.subtract(totalFee);

        BigDecimal diff = orderAmt.subtract(netPaid);
        BigDecimal absDiff = diff.abs();

        String firstPmtStatus = payments.get(0).getStatus();
        String pmtCurr = payments.get(0).getCurrency() != null ? payments.get(0).getCurrency().toUpperCase() : "USD";

        boolean currencyMismatch = payments.stream().anyMatch(p -> p.getCurrency() != null && !p.getCurrency().equalsIgnoreCase(orderCurr));
        if (currencyMismatch) {
            return DiscrepancyItemDto.builder()
                    .id("REC-" + id)
                    .orderId(order.getOrderId())
                    .transactionRef(txRefs)
                    .customerEmail(order.getCustomerEmail())
                    .discrepancyType("CURRENCY_MISMATCH")
                    .severity("HIGH")
                    .orderAmount(orderAmt)
                    .paymentAmount(netPaid)
                    .fee(totalFee)
                    .netSettled(netSettled)
                    .difference(diff)
                    .orderStatus(order.getStatus())
                    .paymentStatus(firstPmtStatus)
                    .currency(orderCurr + " / " + pmtCurr)
                    .details("Order currency (" + orderCurr + ") differs from payment processor currency (" + pmtCurr + "). Gateway Fee: $" + totalFee + ".")
                    .moneyAtRisk(orderAmt)
                    .build();
        }

        boolean pmtUnsettled = "completed".equalsIgnoreCase(orderStatus) && payments.stream().anyMatch(p -> "failed".equalsIgnoreCase(p.getStatus()) || "pending".equalsIgnoreCase(p.getStatus()));
        if (pmtUnsettled) {
            return DiscrepancyItemDto.builder()
                    .id("REC-" + id)
                    .orderId(order.getOrderId())
                    .transactionRef(txRefs)
                    .customerEmail(order.getCustomerEmail())
                    .discrepancyType("FULFILLED_UNSETTLED")
                    .severity("HIGH")
                    .orderAmount(orderAmt)
                    .paymentAmount(netPaid)
                    .fee(totalFee)
                    .netSettled(netSettled)
                    .difference(diff)
                    .orderStatus(order.getStatus())
                    .paymentStatus(firstPmtStatus)
                    .currency(orderCurr)
                    .details("Order marked completed but payment transaction status is " + firstPmtStatus + ".")
                    .moneyAtRisk(orderAmt)
                    .build();
        }

        if (charges.size() > 1) {
            BigDecimal overcharge = totalCharged.subtract(orderAmt);
            return DiscrepancyItemDto.builder()
                    .id("REC-" + id)
                    .orderId(order.getOrderId())
                    .transactionRef(txRefs)
                    .customerEmail(order.getCustomerEmail())
                    .discrepancyType("DOUBLE_CHARGED")
                    .severity("HIGH")
                    .orderAmount(orderAmt)
                    .paymentAmount(netPaid)
                    .fee(totalFee)
                    .netSettled(netSettled)
                    .difference(diff)
                    .orderStatus(order.getStatus())
                    .paymentStatus(firstPmtStatus)
                    .currency(orderCurr)
                    .details("Order was charged " + charges.size() + " times. Total charged: $" + totalCharged + " (Gateway Fee: $" + totalFee + ").")
                    .moneyAtRisk(overcharge)
                    .build();
        }

        if ("cancelled".equalsIgnoreCase(orderStatus) && totalCharged.compareTo(BigDecimal.ZERO) > 0 && totalRefunded.compareTo(BigDecimal.ZERO) == 0) {
            return DiscrepancyItemDto.builder()
                    .id("REC-" + id)
                    .orderId(order.getOrderId())
                    .transactionRef(txRefs)
                    .customerEmail(order.getCustomerEmail())
                    .discrepancyType("CANCELLED_CHARGED")
                    .severity("HIGH")
                    .orderAmount(orderAmt)
                    .paymentAmount(netPaid)
                    .fee(totalFee)
                    .netSettled(netSettled)
                    .difference(diff)
                    .orderStatus(order.getStatus())
                    .paymentStatus(firstPmtStatus)
                    .currency(orderCurr)
                    .details("Order is marked cancelled in store system but payment was charged and not refunded.")
                    .moneyAtRisk(totalCharged)
                    .build();
        }

        if ("completed".equalsIgnoreCase(orderStatus) && totalRefunded.compareTo(BigDecimal.ZERO) > 0) {
            return DiscrepancyItemDto.builder()
                    .id("REC-" + id)
                    .orderId(order.getOrderId())
                    .transactionRef(txRefs)
                    .customerEmail(order.getCustomerEmail())
                    .discrepancyType("REFUND_MISMATCH")
                    .severity("HIGH")
                    .orderAmount(orderAmt)
                    .paymentAmount(netPaid)
                    .fee(totalFee)
                    .netSettled(netSettled)
                    .difference(diff)
                    .orderStatus(order.getStatus())
                    .paymentStatus(firstPmtStatus)
                    .currency(orderCurr)
                    .details("Order marked completed in store system but payment processor issued refund of $" + totalRefunded + ".")
                    .moneyAtRisk(totalRefunded)
                    .build();
        }

        if ("refunded".equalsIgnoreCase(orderStatus) && netPaid.compareTo(BigDecimal.ZERO) > 0) {
            return DiscrepancyItemDto.builder()
                    .id("REC-" + id)
                    .orderId(order.getOrderId())
                    .transactionRef(txRefs)
                    .customerEmail(order.getCustomerEmail())
                    .discrepancyType("REFUND_MISMATCH")
                    .severity("HIGH")
                    .orderAmount(orderAmt)
                    .paymentAmount(netPaid)
                    .fee(totalFee)
                    .netSettled(netSettled)
                    .difference(diff)
                    .orderStatus(order.getStatus())
                    .paymentStatus(firstPmtStatus)
                    .currency(orderCurr)
                    .details("Order marked refunded in store system but payment processor refund was incomplete (remaining net paid: $" + netPaid + ").")
                    .moneyAtRisk(netPaid)
                    .build();
        }

        if (absDiff.compareTo(BigDecimal.ZERO) > 0) {
            if (absDiff.compareTo(ROUNDING_THRESHOLD) <= 0) {
                return DiscrepancyItemDto.builder()
                        .id("REC-" + id)
                        .orderId(order.getOrderId())
                        .transactionRef(txRefs)
                        .customerEmail(order.getCustomerEmail())
                        .discrepancyType("ROUNDING_DIFFERENCE")
                        .severity("LOW")
                        .orderAmount(orderAmt)
                        .paymentAmount(netPaid)
                        .fee(totalFee)
                        .netSettled(netSettled)
                        .difference(diff)
                        .orderStatus(order.getStatus())
                        .paymentStatus(firstPmtStatus)
                        .currency(orderCurr)
                        .details("Minor rounding difference of $" + diff + " between order net amount and payment amount (Gateway Fee: $" + totalFee + ").")
                        .moneyAtRisk(absDiff)
                        .build();
            } else {
                return DiscrepancyItemDto.builder()
                        .id("REC-" + id)
                        .orderId(order.getOrderId())
                        .transactionRef(txRefs)
                        .customerEmail(order.getCustomerEmail())
                        .discrepancyType("AMOUNT_MISMATCH")
                        .severity("HIGH")
                        .orderAmount(orderAmt)
                        .paymentAmount(netPaid)
                        .fee(totalFee)
                        .netSettled(netSettled)
                        .difference(diff)
                        .orderStatus(order.getStatus())
                        .paymentStatus(firstPmtStatus)
                        .currency(orderCurr)
                        .details("Amount mismatch: order net amount is $" + orderAmt + " but payment amount is $" + netPaid + " (Gateway Fee: $" + totalFee + ").")
                        .moneyAtRisk(absDiff)
                        .build();
            }
        }

        return DiscrepancyItemDto.builder()
                .id("REC-" + id)
                .orderId(order.getOrderId())
                .transactionRef(txRefs)
                .customerEmail(order.getCustomerEmail())
                .discrepancyType("MATCHED")
                .severity("NONE")
                .orderAmount(orderAmt)
                .paymentAmount(netPaid)
                .fee(totalFee)
                .netSettled(netSettled)
                .difference(BigDecimal.ZERO)
                .orderStatus(order.getStatus())
                .paymentStatus(firstPmtStatus)
                .currency(orderCurr)
                .details("Order and payment match cleanly (Gateway Fee: $" + totalFee + ", Net Settled: $" + netSettled + ").")
                .moneyAtRisk(BigDecimal.ZERO)
                .build();
    }

    private DiscrepancyItemDto buildOrphanItem(int id, String cleanRef, List<Payment> payments) {
        String txRefs = payments.stream().map(Payment::getTransactionRef).collect(Collectors.joining(", "));
        BigDecimal totalPaid = payments.stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFee = payments.stream()
                .map(p -> p.getFee() != null ? p.getFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netSettled = totalPaid.subtract(totalFee);

        Payment firstPmt = payments.get(0);
        String pmtCurr = firstPmt.getCurrency() != null ? firstPmt.getCurrency().toUpperCase() : "USD";

        return DiscrepancyItemDto.builder()
                .id("REC-" + id)
                .orderId("N/A")
                .transactionRef(txRefs)
                .customerEmail("N/A")
                .discrepancyType("ORPHAN_PAYMENT")
                .severity("HIGH")
                .orderAmount(BigDecimal.ZERO)
                .paymentAmount(totalPaid)
                .fee(totalFee)
                .netSettled(netSettled)
                .difference(totalPaid.negate())
                .orderStatus("N/A")
                .paymentStatus(firstPmt.getStatus())
                .currency(pmtCurr)
                .details("Payment settled in processor for order reference '" + firstPmt.getOrderReference() + "' but no order exists in store system (Gateway Fee: $" + totalFee + ").")
                .moneyAtRisk(totalPaid)
                .build();
    }

    private List<DiscrepancySummaryDto> buildBreakdown(List<DiscrepancyItemDto> items) {
        Map<String, List<DiscrepancyItemDto>> grouped = items.stream()
                .collect(Collectors.groupingBy(DiscrepancyItemDto::getDiscrepancyType));

        List<DiscrepancySummaryDto> result = new ArrayList<>();

        String[] typesInOrder = {
                "MATCHED", "UNPAID_ORDER", "ORPHAN_PAYMENT", "DOUBLE_CHARGED",
                "AMOUNT_MISMATCH", "ROUNDING_DIFFERENCE", "CURRENCY_MISMATCH",
                "FULFILLED_UNSETTLED", "CANCELLED_CHARGED", "REFUND_MISMATCH"
        };

        for (String type : typesInOrder) {
            List<DiscrepancyItemDto> typeItems = grouped.getOrDefault(type, Collections.emptyList());
            if (!typeItems.isEmpty()) {
                int count = typeItems.size();
                BigDecimal totalVal = typeItems.stream()
                        .map(i -> "ORPHAN_PAYMENT".equals(type) ? i.getPaymentAmount() : i.getOrderAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal risk = typeItems.stream()
                        .map(DiscrepancyItemDto::getMoneyAtRisk)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                String severity = typeItems.get(0).getSeverity();

                result.add(DiscrepancySummaryDto.builder()
                        .type(type)
                        .title(formatTitle(type))
                        .count(count)
                        .totalValue(totalVal.setScale(2, RoundingMode.HALF_UP))
                        .moneyAtRisk(risk.setScale(2, RoundingMode.HALF_UP))
                        .severity(severity)
                        .build());
            }
        }

        return result;
    }

    private String formatTitle(String type) {
        switch (type) {
            case "MATCHED": return "Reconciled & Matched";
            case "UNPAID_ORDER": return "Unpaid Orders";
            case "ORPHAN_PAYMENT": return "Orphan Payments";
            case "DOUBLE_CHARGED": return "Double Charged Orders";
            case "AMOUNT_MISMATCH": return "Amount Mismatch (> $0.05)";
            case "ROUNDING_DIFFERENCE": return "Rounding Difference (<= $0.05)";
            case "CURRENCY_MISMATCH": return "Currency Mismatch";
            case "FULFILLED_UNSETTLED": return "Fulfilled on Unsettled Payment";
            case "CANCELLED_CHARGED": return "Cancelled Order Charged";
            case "REFUND_MISMATCH": return "Refund State Mismatch";
            default: return type;
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        String escaped = val.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
