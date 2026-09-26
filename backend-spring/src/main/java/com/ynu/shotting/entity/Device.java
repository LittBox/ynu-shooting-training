package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** rifle | pistol */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeviceType type;

    /** IDLE | BOOKED | IN_USE | MAINTENANCE | DISABLED */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.IDLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum DeviceType {
        rifle, pistol
    }

    public enum DeviceStatus {
        IDLE, BOOKED, IN_USE, MAINTENANCE, DISABLED
    }
}
