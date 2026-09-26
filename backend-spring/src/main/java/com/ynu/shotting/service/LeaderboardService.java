package com.ynu.shoting.service;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.ScoreRepository;
import com.ynu.shoting.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
@Service @RequiredArgsConstructor
public class LeaderboardService {
    private final ScoreRepository scores;
    private final Clock clock;
    @Value("${jwt.secret}") private String secret;
    public record Row(int rank,String userIdHash,String displayName,String gender,String weapon,String event,
        double score,LocalDateTime recordedAt,int sampleCount,Double delta) {}
    public record Board(String weapon,String event,String metric,OffsetDateTime asOf,List<Row> rows) {}
    private record Candidate(User user,double value,LocalDateTime at,int count) {}
    @Transactional(readOnly=true)
    public Board rank(String weapon,String event,String metric,String gender,int limit) {
        if (!Set.of("PISTOL","RIFLE").contains(weapon) || !Set.of("FINAL","QUALIFICATION").contains(event)
                || !Set.of("BEST","AVG5","PB30D","IMPROVE").contains(metric) || !Set.of("ALL","M","F").contains(gender)
                || limit<1 || limit>100) throw new BusinessException(400,"无效的排行榜筛选条件");
        var mode=event.equals("FINAL") ? Score.ScoreMode.final_ : Score.ScoreMode.qualifying;
        var now=LocalDateTime.now(clock);
        var grouped=scores.findPublished(mode,Device.DeviceType.valueOf(weapon.toLowerCase(Locale.ROOT))).stream()
            .filter(s -> s.getRecordedAt()!=null && !s.getRecordedAt().isAfter(now) && s.getTotalScore()!=null
                && Double.isFinite(s.getTotalScore()) && s.getTotalScore()>=0 && s.getTotalScore()<=(event.equals("FINAL")?240:600)
                && Objects.equals(s.getShotCount(),event.equals("FINAL")?24:60))
            .filter(s -> s.getSession().getUser().getProfile() != null
                && s.getSession().getUser().getProfile().getRealName() != null
                && !s.getSession().getUser().getProfile().getRealName().isBlank())
            .filter(s -> gender.equals("ALL") || gender.equals(gender(s.getSession().getUser())))
            .collect(Collectors.groupingBy(s -> s.getSession().getUser().getId()));
        List<Candidate> candidates=new ArrayList<>();
        for (var list:grouped.values()) {
            list.sort(Comparator.comparing(Score::getRecordedAt).reversed().thenComparing(Score::getId,Comparator.reverseOrder()));
            User user=list.get(0).getSession().getUser();
            if (metric.equals("AVG5")) {
                var recent=list.subList(0,Math.min(5,list.size()));
                candidates.add(new Candidate(user,round(recent.stream().mapToDouble(Score::getTotalScore).average().orElseThrow()),recent.get(0).getRecordedAt(),recent.size()));
            } else if (metric.equals("IMPROVE")) {
                var current=list.stream().filter(s -> !s.getRecordedAt().isBefore(now.minusDays(30))).toList();
                var previous=list.stream().filter(s -> s.getRecordedAt().isBefore(now.minusDays(30)) && !s.getRecordedAt().isBefore(now.minusDays(60))).toList();
                if (!current.isEmpty() && !previous.isEmpty()) {
                    Score best=best(current);
                    candidates.add(new Candidate(user,round(best.getTotalScore()-best(previous).getTotalScore()),best.getRecordedAt(),current.size()));
                }
            } else {
                var eligible=metric.equals("PB30D") ? list.stream().filter(s -> !s.getRecordedAt().isBefore(now.minusDays(30))).toList() : list;
                if (!eligible.isEmpty()) { var best=best(eligible); candidates.add(new Candidate(user,best.getTotalScore(),best.getRecordedAt(),1)); }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::value).reversed().thenComparing(Candidate::at).thenComparing(c -> c.user().getId()));
        List<Row> rows=new ArrayList<>();
        for (var c:candidates.subList(0,Math.min(limit,candidates.size()))) {
            String hash=hash(c.user().getId());
            rows.add(new Row(rows.size()+1,hash,c.user().getProfile().getRealName(),
                gender(c.user()),weapon,event,c.value(),c.at(),c.count(),metric.equals("IMPROVE")?c.value():null));
        }
        return new Board(weapon,event,metric,OffsetDateTime.now(clock),rows);
    }
    private Score best(List<Score> list) {
        return list.stream().min(Comparator.comparing(Score::getTotalScore).reversed().thenComparing(Score::getRecordedAt).thenComparing(Score::getId)).orElseThrow();
    }
    private String gender(User u) { return u.getProfile()==null || u.getProfile().getGender()==null ? "U" : u.getProfile().getGender(); }
    private double round(double n) { return Math.round(n*10)/10.0; }
    private String hash(Long id) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return "u_"+HexFormat.of().formatHex(mac.doFinal(id.toString().getBytes(StandardCharsets.UTF_8))).substring(0,16);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
