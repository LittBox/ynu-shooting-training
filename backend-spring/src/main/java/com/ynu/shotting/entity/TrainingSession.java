package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "training_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", unique = true, nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_training_sessions__booking_id__bookings"))
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_training_sessions__user_id__users"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_training_sessions__device_id__devices"))
    private Device device;

    /** final | qualifying */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionMode mode;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "resume_started_at")
    private LocalDateTime resumeStartedAt;

    @Column(name = "actual_duration_min")
    private Integer actualDurationMin;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Score> scores = new ArrayList<>();

    public enum SessionMode {
        final_, qualifying
    }
}
