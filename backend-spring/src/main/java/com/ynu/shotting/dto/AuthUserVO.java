package com.ynu.shoting.dto;

import com.ynu.shoting.entity.User;

/** Authenticated user's own account and editable profile; no entity graph or openid. */
public record AuthUserVO(Long id, String nickname, String avatarUrl,
                         String role, String profileStatus, String realName,
                         String studentNo, String phone, String gender) {
    public static AuthUserVO from(User user) {
        var profile = user.getProfile();
        boolean complete = user.getProfileStatus() == User.ProfileStatus.completed
                && profile != null && profile.isComplete();
        return new AuthUserVO(user.getId(), user.getNickname(), user.getAvatarUrl(),
                user.getRole().name(), complete ? "completed" : "pending",
                profile == null ? null : profile.getRealName(),
                profile == null ? null : profile.getStudentNo(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getGender());
    }
}
