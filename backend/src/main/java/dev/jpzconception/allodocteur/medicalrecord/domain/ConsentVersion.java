package dev.jpzconception.allodocteur.medicalrecord.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "consent_versions")
@Getter
@NoArgsConstructor
public class ConsentVersion {

    @Id
    private String version;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}
