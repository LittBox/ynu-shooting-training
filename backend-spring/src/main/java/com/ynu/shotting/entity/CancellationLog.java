package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cancellation_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_cancellation_log__booking_id__bookings"))
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_cancellation_log__user_id__users"))
    private User user;

    /** user_cancel | admin_cancel */
    @Column(nullable = false)
    @Builder.Default
    private String reason = "user_cancel";

    /** free | late | near_no_show */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CancelLevel level;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum CancelLevel {
        free, late, near_no_show
    }
}
