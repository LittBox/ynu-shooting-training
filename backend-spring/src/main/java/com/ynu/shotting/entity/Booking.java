package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_bookings__user_id__users"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_bookings__device_id__devices"))
    private Device device;

    @Column(name = "slot_date", nullable = false)
    private String slotDate;  // YYYY-MM-DD

    @Column(name = "slot_id", nullable = false)
    private String slotId;  // S1~S6

    /** BOOKED | CHECKED_IN | IN_USE | COMPLETED | CANCELLED | NO_SHOW */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingStatus status = BookingStatus.BOOKED;

    @Column(name = "booked_at", nullable = false)
    @Builder.Default
    private LocalDateTime bookedAt = LocalDateTime.now();

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TrainingSession trainingSession;

    @Column(name = "occupied_device_slot", unique = true)
    private String occupiedDeviceSlot;

    @Column(name = "occupied_user_slot", unique = true)
    private String occupiedUserSlot;

    @PrePersist @PreUpdate
    void syncOccupancy() {
        boolean occupied = status != BookingStatus.CANCELLED && status != BookingStatus.NO_SHOW;
        occupiedDeviceSlot = occupied && status != BookingStatus.COMPLETED ? device.getId() + ":" + slotDate + ":" + slotId : null;
        occupiedUserSlot = occupied ? user.getId() + ":" + slotDate + ":" + slotId : null;
    }

    public enum BookingStatus {
        BOOKED, CHECKED_IN, IN_USE, COMPLETED, CANCELLED, NO_SHOW
    }
}
