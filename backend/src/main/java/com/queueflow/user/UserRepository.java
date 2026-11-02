package com.queueflow.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findAllByOrderByNameAsc();

    /** Serializa a verificação de primeiro usuário (evita dois ADMINs em registro concorrente). */
    @org.springframework.data.jpa.repository.Query(value = "SELECT pg_advisory_xact_lock(730001)", nativeQuery = true)
    void lockUserProvisioning();
}
