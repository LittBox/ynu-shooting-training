package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_schedules", uniqueConstraints = @UniqueConstraint(
        name = "uk_admin_schedule_slot", columnNames = {"admin_user_id", "slot_date", "slot_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_admin_schedules__admin_user_id__users"))
    private User admin;

    @Column(name = "slot_date", nullable = false)
    private String slotDate;  // YYYY-MM-DD

    @Column(name = "slot_id", nullable = false)
    private String slotId;  // S1~S6

    /** One classroom: a shift covers all its devices. Attendance is confirmed by the coach. */
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "departed_at")
    private LocalDateTime departedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
