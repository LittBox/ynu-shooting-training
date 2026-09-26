package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_availability")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_user_availability__user_id__users"))
    private User user;

    @Column(name = "slot_date", nullable = false)
    private String slotDate;  // YYYY-MM-DD

    @Column(name = "slot_id", nullable = false)
    private String slotId;  // S1~S6
}
