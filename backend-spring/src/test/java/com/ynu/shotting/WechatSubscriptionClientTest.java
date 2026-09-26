package com.ynu.shoting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynu.shoting.service.WechatSubscriptionClient;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WechatSubscriptionClientTest {
    WechatSubscriptionClient client;MockRestServiceServer server;
    static final String BASE="https://api.weixin.qq.com";
    final Map<String,Object> payload=Map.of("touser","member","template_id","template","data",Map.of("thing4",Map.of("value","手枪训练")));
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl(BASE);server=MockRestServiceServer.bindTo(builder).build();
        client=new WechatSubscriptionClient("test-app","test-secret",Clock.fixed(Instant.parse("2026-09-26T00:00:00Z"),ZoneId.of("Asia/Shanghai")),new ObjectMapper());
        ReflectionTestUtils.setField(client,"client",builder.build());
    }
    void token(String value) {
        server.expect(requestTo(BASE+"/cgi-bin/stable_token")).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"grant_type\":\"client_credential\",\"appid\":\"test-app\",\"secret\":\"test-secret\",\"force_refresh\":false}"))
            .andRespond(withSuccess("{\"access_token\":\""+value+"\",\"expires_in\":7200}",MediaType.TEXT_PLAIN));
    }
    void send(String token,int code) {server.expect(requestTo(BASE+"/cgi-bin/message/subscribe/send?access_token="+token))
        .andExpect(method(HttpMethod.POST)).andExpect(content().json(new ObjectMapper().valueToTree(payload).toString()))
        .andRespond(withSuccess("{\"errcode\":"+code+",\"errmsg\":\"test\"}",MediaType.TEXT_PLAIN));}
    @Test void usesStableTokenAndCachesItAcrossMessages() {
        token("safe-token");send("safe-token",0);send("safe-token",0);
        assertEquals(WechatSubscriptionClient.Outcome.SENT,client.send(payload).outcome());
        assertEquals(WechatSubscriptionClient.Outcome.SENT,client.send(payload).outcome());server.verify();
    }
    @Test void refreshesOnlyAfterExplicitInvalidTokenResponse() {
        token("old");send("old",42001);token("new");send("new",0);
        assertEquals(WechatSubscriptionClient.Outcome.SENT,client.send(payload).outcome());server.verify();
    }
    @Test void refusalIsTerminalAndDoesNotLoop() {
        token("t");send("t",43101);var result=client.send(payload);
        assertEquals(WechatSubscriptionClient.Outcome.FAILED,result.outcome());assertEquals("43101",result.code());server.verify();
    }
    @Test void invalidTemplateDataFailsInsteadOfClaimingDelivery() {
        token("t");send("t",47003);assertEquals(WechatSubscriptionClient.Outcome.FAILED,client.send(payload).outcome());server.verify();
    }
    @Test void explicitBusyIsRetryable() {
        token("t");send("t",-1);assertEquals(WechatSubscriptionClient.Outcome.RETRY,client.send(payload).outcome());server.verify();
    }
    @Test void sendingTransportFailureIsUncertainAndDoesNotExposeSecrets() {
        token("sensitive-token");server.expect(requestTo(BASE+"/cgi-bin/message/subscribe/send?access_token=sensitive-token"))
            .andRespond(withException(new java.net.SocketTimeoutException("URL contained sensitive-token test-secret")));
        var result=client.send(payload);assertEquals(WechatSubscriptionClient.Outcome.UNKNOWN,result.outcome());
        assertFalse(result.toString().contains("sensitive-token"));assertFalse(result.toString().contains("test-secret"));server.verify();
    }
    @Test void missingErrcodeDoesNotCountAsSuccess() {
        token("t");server.expect(requestTo(BASE+"/cgi-bin/message/subscribe/send?access_token=t")).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertEquals(WechatSubscriptionClient.Outcome.UNKNOWN,client.send(payload).outcome());server.verify();
    }
    @Test void tokenFetchFailureIsSafeToRetryBecauseMessageWasNotSent() {
        server.expect(requestTo(BASE+"/cgi-bin/stable_token")).andRespond(withSuccess("{\"errcode\":40164}",MediaType.TEXT_PLAIN));
        assertEquals(WechatSubscriptionClient.Outcome.RETRY,client.send(payload).outcome());server.verify();
    }
}
