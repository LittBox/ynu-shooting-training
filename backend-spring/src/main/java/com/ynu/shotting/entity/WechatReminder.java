package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="wechat_reminders", uniqueConstraints=@UniqueConstraint(name="uk_reminder_target",columnNames={"kind","target_id"}),
    indexes=@Index(name="idx_reminder_due",columnList="status,next_attempt_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WechatReminder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Kind kind;
    // Logical references survive deleted shifts for delivery diagnostics; validated against live records before sending.
    @Column(name="target_id",nullable=false) private Long targetId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="template_id",nullable=false,length=128) private String templateId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Status status;
    @Column(name="scheduled_at",nullable=false) private LocalDateTime scheduledAt;
    @Column(name="starts_at",nullable=false) private LocalDateTime startsAt;
    @Column(name="next_attempt_at",nullable=false) private LocalDateTime nextAttemptAt;
    @Column(name="subscribed_at",nullable=false) private LocalDateTime subscribedAt;
    @Column(name="claimed_at") private LocalDateTime claimedAt;
    @Column(name="sent_at") private LocalDateTime sentAt;
    @Column(nullable=false) private int attempts;
    @Column(name="last_code",length=64) private String lastCode;
    public enum Kind { TRAINING, DUTY }
    public enum Status { PENDING, SENDING, SENT, FAILED, UNKNOWN, SKIPPED, CANCELLED }
}
