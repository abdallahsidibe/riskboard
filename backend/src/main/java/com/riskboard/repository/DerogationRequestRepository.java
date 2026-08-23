package com.riskboard.repository;

import com.riskboard.entity.DerogationRequest;
import com.riskboard.enums.DerogationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DerogationRequestRepository extends JpaRepository<DerogationRequest, Long> {

    List<DerogationRequest> findByStatus(DerogationStatus status);
}
