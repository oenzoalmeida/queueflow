package com.queueflow.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrioritySettingsRepository extends JpaRepository<PrioritySettings, Long> {
    default PrioritySettings get() { return findById(1L).orElseThrow(); }
}
