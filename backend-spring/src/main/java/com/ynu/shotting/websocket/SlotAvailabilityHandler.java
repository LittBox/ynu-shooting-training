package com.ynu.shoting.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynu.shoting.dto.SlotVO;
import com.ynu.shoting.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时可用位推送处理器（constitution 第四条）
 *
 * 端点：
 *   ws://host:8080/ws/slots?date=2026-09-21
 *   ws://host:8080/ws/slots?date=2026-09-21&type=rifle
 *
 * 消息格式（服务端推送）：
 *   { "type":"SLOTS_UPDATE", "date":"...", "slots":[SlotVO, ...] }
 */
@Slf4j
@Component
public class SlotAvailabilityHandler extends TextWebSocketHandler {

    private static final Map<String, Set<WebSocketSession>> TOPIC_SESSIONS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final ObjectProvider<BookingService> bookingServiceProvider;

    public SlotAvailabilityHandler(ObjectMapper objectMapper,
                                   ObjectProvider<BookingService> bookingServiceProvider) {
        this.objectMapper = objectMapper;
        this.bookingServiceProvider = bookingServiceProvider;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String topic = resolveTopic(session);
        TOPIC_SESSIONS.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet())
                .add(session);
        log.info("WS connected id={} topic={} total={}",
                session.getId(), topic, TOPIC_SESSIONS.get(topic).size());

        BookingService bookingService = bookingServiceProvider.getIfAvailable();
        if (bookingService == null) return;
        try {
            String[] t = topic.split("\\|", -1);
            String date = t[0];
            String type = t.length > 1 ? t[1] : "";
            List<SlotVO> slots = bookingService.dailySlots(LocalDate.parse(date),
                    type.isEmpty() ? null : type);
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "SLOTS_UPDATE");
            msg.put("date", date);
            msg.put("slots", slots);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.warn("WS initial push failed sessionId={} err={}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TOPIC_SESSIONS.values().forEach(set -> set.remove(session));
        log.info("WS closed id={} reason={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("ping".equals(message.getPayload())) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (Exception ignored) { }
        }
    }

    public static Set<WebSocketSession> sessionsOf(String topic) {
        return TOPIC_SESSIONS.getOrDefault(topic, Set.of());
    }

    public static String buildTopic(String date, String deviceType) {
        return date + "|" + (deviceType == null ? "" : deviceType);
    }

    private String resolveTopic(WebSocketSession session) {
        Map<String, String> params = parseQuery(session.getUri() == null ? "" : session.getUri().getQuery());
        String date = params.getOrDefault("date", LocalDate.now().toString());
        String type = params.getOrDefault("type", "");
        return buildTopic(date, type);
    }

    private Map<String, String> parseQuery(String q) {
        Map<String, String> out = new HashMap<>();
        if (q == null || q.isBlank()) return out;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) {
                out.put(kv.substring(0, i), java.net.URLDecoder.decode(kv.substring(i + 1),
                        java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return out;
    }
}
