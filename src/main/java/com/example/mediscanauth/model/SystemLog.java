package com.example.mediscanauth.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "system_logs")
public class SystemLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Size(max = 100)
    @NotNull
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Size(max = 100)
    @Column(name = "entity_name", length = 100)
    private String entityName;

    @Size(max = 100)
    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Lob
    @Column(name = "description")
    private String description;

    @Size(max = 50)
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;


}
