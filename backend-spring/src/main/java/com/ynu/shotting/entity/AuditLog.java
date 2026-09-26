package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_audit_log__admin_user_id__users"))
    private User admin;

    @Column(nullable = false)
    private String action;

    /** Logical historical reference to users.id; intentionally not a database foreign key. */
    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
