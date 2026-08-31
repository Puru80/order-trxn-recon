package com.projects.ordertrxnrecon.repository;

import com.projects.ordertrxnrecon.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    long countByUserIdAndRowStatus(Long userId, String rowStatus);
}
