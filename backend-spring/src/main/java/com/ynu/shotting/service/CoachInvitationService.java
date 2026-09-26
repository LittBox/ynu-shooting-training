package com.ynu.shoting.service;

import com.ynu.shoting.dto.AuthUserVO;
import com.ynu.shoting.entity.AuditLog;
import com.ynu.shoting.entity.User;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.AuditLogRepository;
import com.ynu.shoting.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class CoachInvitationService {
    private final UserRepository users;
    private final AuditLogRepository audits;
    private final Clock clock;
    private final byte[] invitation;
    private final Map<Long, Attempts> attempts = new HashMap<>();
    private record Attempts(Instant expires, int count) {}

    public CoachInvitationService(UserRepository users, AuditLogRepository audits, Clock clock,
            @Value("${auth.coach-invite-code:${COACH_INVITE_CODE:}}") String invitation) {
        this.users = users;
        this.audits = audits;
        this.clock = clock;
        this.invitation = invitation.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public AuthUserVO activate(Long userId, String inviteCode) {
        User user = users.findLockedById(userId).orElseThrow(() -> new BusinessException(401, "请重新登录"));
        if (user.getRole() != User.Role.student) return AuthUserVO.from(user);
        if (user.getProfile() == null || !user.getProfile().isComplete())
            throw new BusinessException(400, "请先完善个人资料，再开通教练身份");
        if (invitation.length < 16)
            throw new BusinessException(409, "教练邀请码尚未启用，请联系负责人");
        registerAttempt(userId);
        if (!MessageDigest.isEqual(invitation, inviteCode.strip().getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(403, "教练邀请码不正确，请向负责人核实");
        user.setRole(User.Role.admin);
        users.save(user);
        audits.save(AuditLog.builder().admin(user).action("ACTIVATE_COACH")
                .targetUserId(userId).detail("Coach invitation verified")
                .createdAt(LocalDateTime.now(clock)).build());
        clearAttempts(userId);
        return AuthUserVO.from(user);
    }

    private synchronized void registerAttempt(Long userId) {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(entry -> !entry.getValue().expires().isAfter(now));
        Attempts previous = attempts.get(userId);
        if (previous != null && previous.count() >= 5)
            throw new BusinessException(429, "邀请码尝试次数过多，请15分钟后重试");
        attempts.put(userId, previous == null ? new Attempts(now.plus(Duration.ofMinutes(15)), 1)
                : new Attempts(previous.expires(), previous.count() + 1));
    }

    private synchronized void clearAttempts(Long userId) { attempts.remove(userId); }
}
