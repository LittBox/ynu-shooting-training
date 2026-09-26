package com.ynu.shoting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "scores", uniqueConstraints = @UniqueConstraint(name="uk_score_session_mode", columnNames={"training_session_id","mode"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_session_id", nullable = false, referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_scores__training_session_id__training_sessions"))
    private TrainingSession session;

    /** final | qualifying —— 决赛 24发 (10+10+4)，资格赛 60发 (6×10) */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ScoreMode mode;

    @Column(name = "total_score", nullable = false)
    private Double totalScore;

    /**
     * 决赛分组：每发成绩按 [10, 10, 4] 三段切片 → 计算每段均值
     * JSON: [g1_avg, g2_avg, g3_avg]
     */
    @Column(name = "group_scores", nullable = false, columnDefinition = "TEXT")
    private String groupScores;

    /** JSON: [s1, s2, ..., sN] —— N 由 event_type 决定（FINAL=24, QUAL=60） */
    @Column(name = "shot_scores", nullable = false, columnDefinition = "TEXT")
    private String shotScores;

    /** 每组发数（变长）。决赛 JSON: [10,10,4]；资格赛 JSON: [10,10,10,10,10,10] */
    @Column(name = "group_spec", nullable = false, columnDefinition = "TEXT")
    private String groupSpec;

    @Column(name = "shot_count", nullable = false)
    private Integer shotCount;

    @Column(name = "x_count")
    @Builder.Default
    private Integer xCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id", referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_scores__recorded_by_user_id__users"))
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();

    public enum ScoreMode {
        final_, qualifying
    }

    /**
     * 工具：从 groupSpec JSON 还原成 List<Integer>
     * 例：「[10,10,4]」 → [10, 10, 4]
     */
    public static List<Integer> parseGroupSpec(String json) {
        if (json == null || json.isEmpty()) return List.of();
        String inner = json.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        if (inner.isEmpty()) return List.of();
        String[] parts = inner.split(",");
        java.util.List<Integer> out = new java.util.ArrayList<>(parts.length);
        for (String p : parts) {
            out.add(Integer.parseInt(p.trim()));
        }
        return out;
    }
}
