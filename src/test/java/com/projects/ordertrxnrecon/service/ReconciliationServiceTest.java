package com.projects.ordertrxnrecon.service;

import com.projects.ordertrxnrecon.dto.DiscrepancyItemDto;
import com.projects.ordertrxnrecon.dto.PaginatedResponseDto;
import com.projects.ordertrxnrecon.dto.ReconciliationSummaryDto;
import com.projects.ordertrxnrecon.entity.Order;
import com.projects.ordertrxnrecon.entity.Payment;
import com.projects.ordertrxnrecon.entity.ReconciliationRecord;
import com.projects.ordertrxnrecon.repository.OrderRepository;
import com.projects.ordertrxnrecon.repository.PaymentRepository;
import com.projects.ordertrxnrecon.repository.ReconciliationRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReconciliationRecordRepository reconciliationRecordRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    private Long userId = 1L;

    @Test
    void testProcessAndSaveReconciliation() {
        Order o1 = Order.builder()
                .userId(userId)
                .orderId("ORD-101")
                .currency("USD")
                .netAmount(new BigDecimal("100.00"))
                .status("completed")
                .rowStatus("VALID")
                .build();

        Payment p1 = Payment.builder()
                .userId(userId)
                .transactionRef("TXN-101")
                .orderReference("ORD-101")
                .currency("USD")
                .amount(new BigDecimal("100.00"))
                .type("charge")
                .status("settled")
                .rowStatus("VALID")
                .build();

        Order o2 = Order.builder()
                .userId(userId)
                .orderId("ORD-102")
                .currency("USD")
                .netAmount(new BigDecimal("68.65"))
                .status("completed")
                .rowStatus("VALID")
                .build();

        Payment p2 = Payment.builder()
                .userId(userId)
                .transactionRef("TXN-102")
                .orderReference("ORD-102")
                .currency("USD")
                .amount(new BigDecimal("68.63"))
                .type("charge")
                .status("settled")
                .rowStatus("VALID")
                .build();

        when(orderRepository.findByUserId(userId)).thenReturn(Arrays.asList(o1, o2));
        when(paymentRepository.findByUserId(userId)).thenReturn(Arrays.asList(p1, p2));

        ReconciliationSummaryDto result = reconciliationService.processAndSaveReconciliation(userId);

        assertNotNull(result);
        assertEquals(2, result.getTotalOrders());
        assertEquals(2, result.getTotalPayments());
        assertEquals(2, result.getDiscrepancies().size());
    }

    @Test
    void testGetPaginatedDiscrepanciesAndExportFromDb() {
        ReconciliationRecord r1 = ReconciliationRecord.builder()
                .userId(userId)
                .reconId("REC-1")
                .orderId("ORD-101")
                .transactionRef("TXN-101")
                .discrepancyType("MATCHED")
                .severity("NONE")
                .orderAmount(new BigDecimal("100.00"))
                .paymentAmount(new BigDecimal("100.00"))
                .difference(BigDecimal.ZERO)
                .moneyAtRisk(BigDecimal.ZERO)
                .build();

        Page<ReconciliationRecord> pageMock = new PageImpl<>(Collections.singletonList(r1));

        when(reconciliationRecordRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageMock);
        when(reconciliationRecordRepository.findAll(any(Specification.class))).thenReturn(Collections.singletonList(r1));

        PaginatedResponseDto<DiscrepancyItemDto> paged = reconciliationService.getPaginatedDiscrepancies(
                userId, 0, 10, null, null, null, "id", "asc");

        assertNotNull(paged);
        assertEquals(1, paged.getTotalElements());
        assertEquals(1, paged.getContent().size());
        assertEquals("ORD-101", paged.getContent().get(0).getOrderId());

        byte[] csv = reconciliationService.exportDiscrepanciesCsv(userId, null, null, null);
        assertNotNull(csv);
        String csvStr = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csvStr.contains("ORD-101"));
    }
}
