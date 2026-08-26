package com.watermonitor.alert.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AlertJpaRepository extends JpaRepository<AlertEntity, UUID> {

    @Query("SELECT a FROM AlertEntity a WHERE a.deviceId = :deviceId AND a.state NOT IN ('RESOLVED', 'EXPIRED')")
    AlertEntity findActiveByDeviceId(String deviceId);

    @Query("SELECT a FROM AlertEntity a WHERE a.state NOT IN ('RESOLVED', 'EXPIRED') ORDER BY a.openedAt DESC")
    List<AlertEntity> findAllActive();

    List<AlertEntity> findByDeviceIdOrderByOpenedAtDesc(String deviceId);
}
