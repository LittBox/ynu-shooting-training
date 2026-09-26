package com.ynu.shoting;

import com.ynu.shoting.entity.Profile;
import com.ynu.shoting.entity.User;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.AuditLogRepository;
import com.ynu.shoting.repository.UserRepository;
import com.ynu.shoting.service.CoachInvitationService;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoachInvitationConfigTest {
    @Test void missingOrWeakServerCodeNeverGrantsPermission() {
        var users=mock(UserRepository.class);
        var audits=mock(AuditLogRepository.class);
        var user=User.builder().id(1L).role(User.Role.student).profileStatus(User.ProfileStatus.completed).build();
        user.setProfile(Profile.builder().realName("测试").studentNo("TEST").phone("13800000000").gender("F").build());
        when(users.findLockedById(1L)).thenReturn(Optional.of(user));
        for (String code : new String[]{"", "1234"}) {
            var service=new CoachInvitationService(users,audits,Clock.systemUTC(),code);
            assertEquals(409,assertThrows(BusinessException.class,()->service.activate(1L,code)).getCode());
            assertEquals(User.Role.student,user.getRole());
        }
        verifyNoInteractions(audits);
        verify(users,never()).save(any());
    }
}
