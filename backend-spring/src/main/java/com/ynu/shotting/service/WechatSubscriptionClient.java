package com.ynu.shoting.service;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.*;

/** External delivery never runs in a database transaction. Do not log URLs, tokens, openids or response bodies. */
@Component
public class WechatSubscriptionClient {
    public enum Outcome { SENT, RETRY, FAILED, UNKNOWN }
    public record Result(Outcome outcome,String code) {}
    private final String appId,secret;
    private final Clock clock;
    private final ObjectMapper json;
    private final RestClient client;
    private String cachedToken;
    private Instant expiresAt=Instant.MIN;
    public WechatSubscriptionClient(@Value("${auth.wechat-app-id:}") String appId,@Value("${auth.wechat-app-secret:}") String secret,Clock clock,ObjectMapper json) {
        this.appId=appId;this.secret=secret;this.clock=clock;this.json=json;
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(5000);
        client=RestClient.builder().baseUrl("https://api.weixin.qq.com").requestFactory(factory).build();
    }
    public boolean configured() {return !appId.isBlank()&&!secret.isBlank();}
    private synchronized String token() throws Exception {
        if(cachedToken!=null && clock.instant().isBefore(expiresAt))return cachedToken;
        if(!configured())throw new IllegalStateException("Wechat credentials unavailable");
        JsonNode body=json.readTree(client.post().uri("/cgi-bin/stable_token").contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("grant_type","client_credential","appid",appId,"secret",secret,"force_refresh",false)).retrieve().body(String.class));
        if(body==null || body.path("errcode").asInt(0)!=0 || body.path("access_token").asText().isBlank() || body.path("expires_in").asLong()<=0)
            throw new IllegalStateException("Wechat token unavailable");
        cachedToken=body.path("access_token").asText();
        expiresAt=clock.instant().plusSeconds(Math.max(1,body.path("expires_in").asLong()-60));
        return cachedToken;
    }
    private synchronized void invalidate(String token) {if(Objects.equals(cachedToken,token)){cachedToken=null;expiresAt=Instant.MIN;}}
    public Result send(Map<String,Object> payload) {
        String token;
        try {token=token();}catch(Exception ex){return new Result(Outcome.RETRY,"TOKEN_UNAVAILABLE");}
        for(int i=0;i<2;i++) {
            JsonNode body;
            try {
                String current=token;
                body=json.readTree(client.post().uri(b->b.path("/cgi-bin/message/subscribe/send").queryParam("access_token",current).build())
                    .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(String.class));
            }catch(Exception ex){return new Result(Outcome.UNKNOWN,"DELIVERY_UNCERTAIN");}
            if(body==null || !body.path("errcode").isIntegralNumber())return new Result(Outcome.UNKNOWN,"INVALID_RESPONSE");
            int code=body.path("errcode").asInt();
            if(code==0)return new Result(Outcome.SENT,"0");
            if(Set.of(40001,40014,42001).contains(code)) {
                invalidate(token);
                if(i==0) {try{token=token();continue;}catch(Exception ex){return new Result(Outcome.RETRY,"TOKEN_UNAVAILABLE");}}
                return new Result(Outcome.RETRY,String.valueOf(code));
            }
            return new Result(code==-1||code==43108?Outcome.RETRY:Outcome.FAILED,String.valueOf(code));
        }
        return new Result(Outcome.RETRY,"TOKEN_UNAVAILABLE");
    }
}
