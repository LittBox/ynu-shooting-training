package com.ynu.shoting.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynu.shoting.dto.SlotVO;
import com.ynu.shoting.service.BookingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主动广播：预约/取消/签到/爽约 时调用。
 * 使用 ObjectProvider 延迟注入 BookingService，避免与 BookingServiceImpl 循环依赖。
 */
@Slf4j
@Service
public class SlotPushService {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<BookingService> bookingServiceProvider;

    public SlotPushService(ObjectMapper objectMapper,
                           ObjectProvider<BookingService> bookingServiceProvider) {
        this.objectMapper = objectMapper;
        this.bookingServiceProvider = bookingServiceProvider;
    }

    /** 推送某日全类型 + rifle + pistol 三个 topic */
    public void pushDate(String date) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { broadcast(date); }
                });
        } else broadcast(date);
    }

    private void broadcast(String date) {
        pushDate(date, null);
        pushDate(date, "rifle");
        pushDate(date, "pistol");
    }

    public void pushDate(String date, String deviceType) {
        String topic = SlotAvailabilityHandler.buildTopic(date, deviceType);
        Set<WebSocketSession> sessions = SlotAvailabilityHandler.sessionsOf(topic);
        if (sessions.isEmpty()) return;
        BookingService bookingService = bookingServiceProvider.getIfAvailable();
        if (bookingService == null) {
            log.warn("BookingService not ready, skip push date={} type={}", date, deviceType);
            return;
        }
        try {
            LocalDate d = LocalDate.parse(date);
            List<SlotVO> slots = bookingService.dailySlots(d, deviceType);

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "SLOTS_UPDATE");
            payload.put("date", date);
            payload.put("slots", slots);
            String text = objectMapper.writeValueAsString(payload);

            TextMessage message = new TextMessage(text);
            int sent = 0;
            for (WebSocketSession s : sessions) {
                if (!s.isOpen()) continue;
                try {
                    synchronized (s) {
                        s.sendMessage(message);
                    }
                    sent++;
                } catch (Exception e) {
                    log.warn("WS send failed sessionId={} err={}", s.getId(), e.getMessage());
                }
            }
            log.debug("WS pushed date={} type={} recipients={}", date, deviceType, sent);
        } catch (JsonProcessingException e) {
            log.error("WS payload serialize failed", e);
        } catch (Exception e) {
            log.error("WS push failed date={} type={}", date, deviceType, e);
        }
    }
}
