package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_profiles__user_id__users"))
    private User user;

    @Column(nullable = false)
    private String realName;

    @Column(name = "student_no", unique = true, nullable = false)
    private String studentNo;

    @Column(nullable = false)
    private String phone;

    @Column(length=1)
    @Builder.Default
    private String gender = "U";

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isComplete() {
        return realName != null && !realName.isBlank()
                && studentNo != null && !studentNo.isBlank()
                && phone != null && !phone.isBlank()
                && ("M".equals(gender) || "F".equals(gender));
    }

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
}
