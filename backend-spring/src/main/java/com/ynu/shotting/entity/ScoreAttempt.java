package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="score_attempts", uniqueConstraints=@UniqueConstraint(name="uk_score_attempt_request", columnNames={"training_session_id","request_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScoreAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="training_session_id",nullable=false,
        foreignKey=@ForeignKey(name="fk_score_attempts__training_session_id__training_sessions"))
    private TrainingSession session;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Score.ScoreMode mode;
    @Column(name="request_key",nullable=false,length=64) private String requestKey;
    @Column(name="total_score",nullable=false) private Double totalScore;
    @Column(name="group_totals",nullable=false,columnDefinition="TEXT") private String groupTotals;
    @Column(name="shot_scores",nullable=false,columnDefinition="TEXT") private String shotScores;
    @Column(name="group_scores",nullable=false,columnDefinition="TEXT") private String groupScores;
    @Column(name="recorded_at",nullable=false) private LocalDateTime recordedAt;
}
