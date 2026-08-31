package com.projects.ordertrxnrecon.repository;

import com.projects.ordertrxnrecon.entity.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long>, JpaSpecificationExecutor<ReconciliationRecord> {

    List<ReconciliationRecord> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
