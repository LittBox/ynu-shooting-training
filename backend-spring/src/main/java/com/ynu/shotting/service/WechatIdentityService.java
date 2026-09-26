package com.ynu.shoting.service;
import com.ynu.shoting.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.Arrays;
@Service
@Slf4j
public class WechatIdentityService {
    private final Environment environment;
    private final String mode, appId, appSecret;
    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper();
    public WechatIdentityService(Environment environment, @Value("${auth.mode:wechat}") String mode,
            @Value("${auth.wechat-app-id:}") String appId, @Value("${auth.wechat-app-secret:}") String appSecret) {
        this.environment=environment; this.mode=mode; this.appId=appId; this.appSecret=appSecret;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(5000);
        client = RestClient.builder().requestFactory(factory).baseUrl("https://api.weixin.qq.com").build();
    }
    public String resolve(String code, String requestedMode) {
        if ("mock".equals(requestedMode)) {
            boolean local = Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equals("integration") || p.equals("local"));
            if (!local || !"mock".equals(mode)) throw new BusinessException(403, "开发登录未开放");
            // A development identity can never impersonate a real WeChat openid.
            return "mock:" + code;
        }
        if (!"wechat".equals(requestedMode)) throw new BusinessException(400, "无效登录方式");
        if (appId.isBlank() || appSecret.isBlank()) throw new BusinessException(503, "微信登录尚未配置，请联系管理员");
        JsonNode result;
        try {
            // WeChat returns JSON with Content-Type: text/plain. Read the body
            // as text before parsing so response media type does not reject it.
            String body = client.get().uri(uri -> uri.path("/sns/jscode2session")
                .queryParam("appid", appId).queryParam("secret", appSecret).queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code").build()).retrieve().body(String.class);
            result = json.readTree(body);
        } catch (Exception ex) {
            // Exception messages/stack traces may include the URL's secret and code.
            log.warn("WeChat login exchange failed: type={}, cause={}", ex.getClass().getSimpleName(),
                    ex.getCause() == null ? "none" : ex.getCause().getClass().getSimpleName());
            throw new BusinessException(502, "微信登录服务暂时不可用，请重试");
        }
        if (result == null || result.path("errcode").asInt(0) != 0 || result.path("openid").asText().isBlank()) {
            log.warn("WeChat login rejected: errcode={}", result == null ? "empty-response" : result.path("errcode").asInt(0));
            throw new BusinessException(401, "微信登录凭证已失效，请重新登录");
        }
        return result.path("openid").asText();
    }
}
