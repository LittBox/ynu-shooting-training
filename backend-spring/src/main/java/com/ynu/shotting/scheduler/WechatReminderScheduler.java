package com.ynu.shoting.scheduler;

import com.ynu.shoting.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="wechat-reminders.enabled",havingValue="true")
public class WechatReminderScheduler {
    private final WechatReminderService reminders;
    private final WechatSubscriptionClient client;
    @Scheduled(fixedDelay=15000,initialDelay=15000)
    public void sendDue() {
        for(Long id:reminders.due()) {
            try {
                var delivery=reminders.claim(id);
                if(delivery!=null)reminders.complete(id,client.send(delivery.payload()));
            }catch(Exception ex){log.warn("Wechat reminder processing failed id={} type={}",id,ex.getClass().getSimpleName());}
        }
    }
}
