package com.projects.ordertrxnrecon.service;

import com.projects.ordertrxnrecon.entity.Order;
import com.projects.ordertrxnrecon.entity.Payment;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CsvParserService {

    private static final String VALID = "VALID";
    private static final String INVALID = "INVALID";

    public List<Order> parseOrders(String csvContent, Long userId) throws CsvValidationException, IOException {
        List<Order> orders = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();

        try (CSVReader reader = new CSVReader(new StringReader(csvContent))) {
            String[] header = reader.readNext();
            if (header == null) {
                return orders;
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                String rawRow = String.join(",", line);
                Order order = parseOrderRow(line, userId, rawRow, seenOrderIds);
                orders.add(order);
            }
        }

        return orders;
    }

    public List<Payment> parsePayments(String csvContent, Long userId) throws CsvValidationException, IOException {
        List<Payment> payments = new ArrayList<>();
        Set<String> seenTransactionRefs = new HashSet<>();

        try (CSVReader reader = new CSVReader(new StringReader(csvContent))) {
            String[] header = reader.readNext();
            if (header == null) {
                return payments;
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                String rawRow = String.join(",", line);
                Payment payment = parsePaymentRow(line, userId, rawRow, seenTransactionRefs);
                payments.add(payment);
            }
        }

        return payments;
    }

    private Order parseOrderRow(String[] line, Long userId, String rawRow, Set<String> seenOrderIds) {
        List<String> errors = new ArrayList<>();

        String orderId = getSafeValue(line, 0);
        String orderDate = getSafeValue(line, 1);
        String customerEmail = getSafeValue(line, 2);
        String currency = getSafeValue(line, 3);
        BigDecimal grossAmount = parseBigDecimal(getSafeValue(line, 4), "gross_amount", errors);
        BigDecimal discount = parseBigDecimal(getSafeValue(line, 5), "discount", errors);
        BigDecimal netAmount = parseBigDecimal(getSafeValue(line, 6), "net_amount", errors);
        String status = getSafeValue(line, 7);

        if (orderId == null || orderId.isBlank()) {
            errors.add("order_id is required");
        } else {
            String normalizedId = orderId.trim().toUpperCase();
            if (!seenOrderIds.add(normalizedId)) {
                errors.add("Duplicate order_id: " + orderId);
            }
        }

        String rowStatus = errors.isEmpty() ? VALID : INVALID;

        return Order.builder()
                .userId(userId)
                .orderId(orderId)
                .orderDate(orderDate)
                .customerEmail(customerEmail)
                .currency(currency)
                .grossAmount(grossAmount)
                .discount(discount)
                .netAmount(netAmount)
                .status(status)
                .rowStatus(rowStatus)
                .errors(errors.isEmpty() ? null : String.join("; ", errors))
                .rawRow(rawRow)
                .build();
    }

    private Payment parsePaymentRow(String[] line, Long userId, String rawRow, Set<String> seenTransactionRefs) {
        List<String> errors = new ArrayList<>();

        String transactionRef = getSafeValue(line, 0);
        String processedAt = getSafeValue(line, 1);
        String orderReference = getSafeValue(line, 2);
        String currency = getSafeValue(line, 3);
        BigDecimal amount = parseBigDecimal(getSafeValue(line, 4), "amount", errors);
        BigDecimal fee = parseBigDecimal(getSafeValue(line, 5), "fee", errors);
        BigDecimal netSettled = parseBigDecimal(getSafeValue(line, 6), "net_settled", errors);
        String type = getSafeValue(line, 7);
        String status = getSafeValue(line, 8);

        if (transactionRef == null || transactionRef.isBlank()) {
            errors.add("transaction_ref is required");
        } else {
            String normalizedRef = transactionRef.trim().toUpperCase();
            if (!seenTransactionRefs.add(normalizedRef)) {
                errors.add("Duplicate transaction_ref: " + transactionRef);
            }
        }

        String rowStatus = errors.isEmpty() ? VALID : INVALID;

        return Payment.builder()
                .userId(userId)
                .transactionRef(transactionRef)
                .processedAt(processedAt)
                .orderReference(orderReference)
                .currency(currency)
                .amount(amount)
                .fee(fee)
                .netSettled(netSettled)
                .type(type)
                .status(status)
                .rowStatus(rowStatus)
                .errors(errors.isEmpty() ? null : String.join("; ", errors))
                .rawRow(rawRow)
                .build();
    }

    private String getSafeValue(String[] line, int index) {
        if (index < line.length) {
            String val = line[index];
            return (val == null || val.isBlank()) ? null : val.trim();
        }
        return null;
    }

    private BigDecimal parseBigDecimal(String value, String fieldName, List<String> errors) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            errors.add(fieldName + " is not a valid number: " + value);
            return null;
        }
    }
}
