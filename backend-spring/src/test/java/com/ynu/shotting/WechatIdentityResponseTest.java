package com.ynu.shoting;

import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.service.WechatIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WechatIdentityResponseTest {
    private WechatIdentityService service;
    private MockRestServiceServer server;
    private static final String URL = "https://api.weixin.qq.com/sns/jscode2session"
            + "?appid=test-app&secret=test-secret&js_code=test-code&grant_type=authorization_code";

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WechatIdentityService(new MockEnvironment(), "wechat", "test-app", "test-secret");
        ReflectionTestUtils.setField(service, "client", builder.build());
    }

    @Test void acceptsWechatJsonWithTextPlainContentType() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"openid\":\"real-test-openid\",\"session_key\":\"not-returned\"}", MediaType.TEXT_PLAIN));
        assertEquals("real-test-openid", service.resolve("test-code", "wechat"));
        server.verify();
    }

    @Test void stillAcceptsApplicationJson() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"openid\":\"real-test-openid\"}", MediaType.APPLICATION_JSON));
        assertEquals("real-test-openid", service.resolve("test-code", "wechat"));
        server.verify();
    }

    @Test void textPlainApiRejectionIsNotAConversionFailure() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.TEXT_PLAIN));
        assertFailure(401);
    }

    @Test void missingIdentityCannotAuthenticate() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));
        assertFailure(401);
    }

    @Test void invalidJsonFailsClosed() {
        server.expect(requestTo(URL)).andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));
        assertFailure(502);
    }

    @Test void upstreamHttpFailureDoesNotExposeRequestCredentials() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertFailure(502);
    }

    private void assertFailure(int status) {
        var failure = assertThrows(BusinessException.class, () -> service.resolve("test-code", "wechat"));
        assertEquals(status, failure.getCode());
        assertFalse(failure.getMessage().contains("test-secret"));
        assertFalse(failure.getMessage().contains("test-code"));
        server.verify();
    }
}
