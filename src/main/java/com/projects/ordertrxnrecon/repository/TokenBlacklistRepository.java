package com.projects.ordertrxnrecon.repository;

import com.projects.ordertrxnrecon.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

    Optional<TokenBlacklist> findByTokenHash(String tokenHash);

    boolean existsByTokenHash(String tokenHash);
}
