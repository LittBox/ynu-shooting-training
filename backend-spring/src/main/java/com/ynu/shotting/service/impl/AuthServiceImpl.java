package com.ynu.shoting.service.impl;

import com.ynu.shoting.dto.LoginRequest;
import com.ynu.shoting.dto.LoginResponse;
import com.ynu.shoting.dto.ProfileRequest;
import com.ynu.shoting.entity.Profile;
import com.ynu.shoting.entity.User;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.ProfileRepository;
import com.ynu.shoting.repository.UserRepository;
import com.ynu.shoting.security.AuthContext;
import com.ynu.shoting.security.JwtTokenProvider;
import com.ynu.shoting.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final com.ynu.shoting.service.WechatIdentityService identities;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthContext authContext;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String openid = identities.resolve(request.getCode(), request.getMode());

        User user = userRepository.findByOpenid(openid).orElseGet(() -> {
            User u = User.builder()
                    .openid(openid)
                    .nickname(request.getNickname() == null ? "" : request.getNickname())
                    .avatarUrl(request.getAvatarUrl() == null ? "" : request.getAvatarUrl())
                    .role(User.Role.student)
                    .profileStatus(User.ProfileStatus.pending)
                    .build();
            return userRepository.save(u);
        });

        String token = tokenProvider.generateToken(user);

        boolean profileCompleted = user.getProfileStatus() == User.ProfileStatus.completed
                && profileRepository.findByUserId(user.getId()).map(Profile::isComplete).orElse(false);

        return new LoginResponse(
                token,
                user.getId(),
                user.getRole().name(),
                profileCompleted ? "completed" : "pending",
                user.getNickname(),
                user.getAvatarUrl()
        );
    }

    @Override
    @Transactional
    public User submitProfile(HttpServletRequest request, ProfileRequest req) {
        req.setStudentNo(req.getStudentNo().trim());
        req.setRealName(req.getRealName().trim());
        User user = authContext.currentUser(request);
        user = userRepository.findLockedById(user.getId()).orElseThrow(() -> new BusinessException(401, "用户不存在"));
        Profile existing = profileRepository.findByUserId(user.getId()).orElse(null);
        if (existing != null && !existing.getStudentNo().equals(req.getStudentNo()))
            throw new BusinessException(400, "登记后学号不可修改");
        Profile duplicate = profileRepository.findByStudentNo(req.getStudentNo()).orElse(null);
        if (duplicate != null && !duplicate.getUserId().equals(user.getId()))
            throw new BusinessException(409, "该学号已注册");
        // @MapsId derives the identifier on persist. Assigning it here makes
        // Spring Data merge a new profile as a detached entity.
        Profile profile = profileRepository.findByUserId(user.getId()).orElseGet(() ->
                Profile.builder().build());
        profile.setUser(user);
        profile.setRealName(req.getRealName());
        profile.setStudentNo(req.getStudentNo());
        profile.setPhone(req.getPhone());
        profile.setGender(req.getGender());
        profile = profileRepository.save(profile);
        user.setProfile(profile);

        user.setProfileStatus(User.ProfileStatus.completed);
        return userRepository.save(user);
    }

    @Override
    public User me(HttpServletRequest request) {
        return authContext.currentUser(request);
    }

}
