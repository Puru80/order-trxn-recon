package com.projects.ordertrxnrecon.repository.specification;

import com.projects.ordertrxnrecon.entity.ReconciliationRecord;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ReconciliationSpecification {

    public static Specification<ReconciliationRecord> filterBy(Long userId, String search, String type, String severity) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("userId"), userId));

            if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
                predicates.add(cb.equal(cb.upper(root.get("discrepancyType")), type.toUpperCase()));
            }

            if (severity != null && !severity.isBlank() && !"ALL".equalsIgnoreCase(severity)) {
                predicates.add(cb.equal(cb.upper(root.get("severity")), severity.toUpperCase()));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate searchPredicate = cb.or(
                        cb.like(cb.lower(root.get("orderId")), pattern),
                        cb.like(cb.lower(root.get("transactionRef")), pattern),
                        cb.like(cb.lower(root.get("customerEmail")), pattern),
                        cb.like(cb.lower(root.get("details")), pattern)
                );
                predicates.add(searchPredicate);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
