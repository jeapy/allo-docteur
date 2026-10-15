package dev.jpzconception.allodocteur.professional.domain;

import dev.jpzconception.allodocteur.reference.domain.City;
import dev.jpzconception.allodocteur.reference.domain.District;
import dev.jpzconception.allodocteur.reference.domain.ProfessionalType;
import dev.jpzconception.allodocteur.reference.domain.Specialty;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import dev.jpzconception.allodocteur.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** Profil professionnel : l'identifiant est celui du compte utilisateur. */
@Entity
@Table(name = "professionals")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
public class Professional extends AuditableEntity {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_type_id")
    private ProfessionalType type;

    @Column(nullable = false)
    private String qualification;

    @Column(name = "license_number", nullable = false)
    private String licenseNumber;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(columnDefinition = "text")
    private String bio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @Column(name = "consultation_fee")
    private BigDecimal consultationFee;

    @Column(name = "home_visit_fee")
    private BigDecimal homeVisitFee;

    @Column(nullable = false)
    private String currency = "XOF";

    @Column(name = "offers_teleconsultation", nullable = false)
    private boolean offersTeleconsultation;

    @Column(name = "offers_home_visit", nullable = false)
    private boolean offersHomeVisit;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_check_status", nullable = false)
    private LicenseCheckStatus licenseCheckStatus = LicenseCheckStatus.UNCHECKED;

    @Column(name = "license_checked_at")
    private Instant licenseCheckedAt;

    @Column(name = "license_checked_by")
    private UUID licenseCheckedBy;

    @Column(name = "license_check_note")
    private String licenseCheckNote;

    @Column(name = "rating_avg", nullable = false)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @ManyToMany
    @JoinTable(name = "professional_specialties",
            joinColumns = @JoinColumn(name = "professional_id"),
            inverseJoinColumns = @JoinColumn(name = "specialty_id"))
    private Set<Specialty> specialties = new HashSet<>();

    @OneToMany(mappedBy = "professional", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CoverageZone> coverageZones = new HashSet<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Enregistre le résultat d'une vérification du numéro d'ordre (automatique : {@code by} nul). */
    public void recordLicenseCheck(LicenseCheckStatus status, UUID by, Instant now, String note) {
        this.licenseCheckStatus = status;
        this.licenseCheckedAt = now;
        this.licenseCheckedBy = by;
        this.licenseCheckNote = note;
    }

    // ---- transitions du workflow de validation ----

    public void approve(UUID adminId, Instant now) {
        if (verificationStatus != VerificationStatus.PENDING && verificationStatus != VerificationStatus.SUSPENDED) {
            throw invalid("approuver");
        }
        verificationStatus = VerificationStatus.APPROVED;
        markReviewed(adminId, now, null);
    }

    public void reject(UUID adminId, Instant now, String reason) {
        if (verificationStatus != VerificationStatus.PENDING) {
            throw invalid("rejeter");
        }
        verificationStatus = VerificationStatus.REJECTED;
        markReviewed(adminId, now, reason);
    }

    public void suspend(UUID adminId, Instant now, String reason) {
        if (verificationStatus != VerificationStatus.APPROVED) {
            throw invalid("suspendre");
        }
        verificationStatus = VerificationStatus.SUSPENDED;
        markReviewed(adminId, now, reason);
    }

    /** Un profil rejeté ou modifié sur un champ sensible repasse en attente de validation. */
    public void resubmit() {
        verificationStatus = VerificationStatus.PENDING;
        rejectionReason = null;
    }

    public boolean isVisibleInDirectory() {
        return verificationStatus == VerificationStatus.APPROVED;
    }

    private void markReviewed(UUID adminId, Instant now, String reason) {
        this.reviewedBy = adminId;
        this.reviewedAt = now;
        this.rejectionReason = reason;
    }

    private ApiException invalid(String action) {
        return ApiException.conflict("INVALID_STATE",
                "Impossible de " + action + " un professionnel au statut " + verificationStatus + ".");
    }
}
