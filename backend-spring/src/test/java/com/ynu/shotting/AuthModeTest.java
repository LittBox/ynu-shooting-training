package com.ynu.shoting;
import com.ynu.shoting.service.WechatIdentityService;
import com.ynu.shoting.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class AuthModeTest {
    @Test void arbitraryCodesCannotLogIntoProduction() {
        var service=new WechatIdentityService(new MockEnvironment(),"mock","","");
        assertEquals(403,assertThrows(BusinessException.class,()->service.resolve("admin-openid-001","mock")).getCode());
    }
    @Test void integrationIdentitiesAreNamespaced() {
        var env=new MockEnvironment();env.setActiveProfiles("integration");
        assertEquals("mock:admin-openid-001",new WechatIdentityService(env,"mock","","").resolve("admin-openid-001","mock"));
    }
    @Test void wechatNeverFallsBackToMockWhenCredentialsAreMissing() {
        var env=new MockEnvironment();env.setActiveProfiles("integration");
        assertEquals(503,assertThrows(BusinessException.class,()->new WechatIdentityService(env,"mock","","").resolve("code","wechat")).getCode());
    }
}
