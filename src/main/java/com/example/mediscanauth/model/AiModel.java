package com.example.mediscanauth.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "ai_models")
public class AiModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_id", nullable = false)
    private Long id;

    @Size(max = 100)
    @NotNull
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Size(max = 50)
    @NotNull
    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Size(max = 500)
    @Column(name = "api_endpoint", length = 500)
    private String apiEndpoint;

    @Column(name = "accuracy", precision = 5, scale = 2)
    private BigDecimal accuracy;

    @Lob
    @Column(name = "description")
    private String description;

    @ColumnDefault("'ACTIVE'")
    @Lob
    @Column(name = "status")
    private String status;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;


}