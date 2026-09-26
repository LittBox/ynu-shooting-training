package com.ynu.shoting.security;

import com.ynu.shoting.entity.User;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthContext {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public User currentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(401, "未登录");
        }
        String token = header.substring(7);
        try {
            Claims claims = tokenProvider.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            return userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        } catch (Exception e) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }
    }
}
