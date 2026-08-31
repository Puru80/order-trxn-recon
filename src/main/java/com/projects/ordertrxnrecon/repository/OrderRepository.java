package com.projects.ordertrxnrecon.repository;

import com.projects.ordertrxnrecon.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    long countByUserIdAndRowStatus(Long userId, String rowStatus);
}
