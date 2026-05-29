package com.example.mediscanauth.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "ai_detected_regions")
public class AiDetectedRegion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_result_id", nullable = false)
    private AiAnalysisResult aiResult;

    @NotNull
    @Column(name = "x_coordinate", nullable = false)
    private Integer xCoordinate;

    @NotNull
    @Column(name = "y_coordinate", nullable = false)
    private Integer yCoordinate;

    @NotNull
    @Column(name = "width", nullable = false)
    private Integer width;

    @NotNull
    @Column(name = "height", nullable = false)
    private Integer height;

    @Size(max = 100)
    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Lob
    @Column(name = "description")
    private String description;


}