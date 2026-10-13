package dev.jpzconception.allodocteur.medicalrecord.infrastructure;

import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalAccessLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MedicalAccessLogRepository
        extends JpaRepository<MedicalAccessLog, UUID>, JpaSpecificationExecutor<MedicalAccessLog> {}
