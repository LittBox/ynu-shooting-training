package com.ynu.shoting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties(prefix="wechat-reminders") @Getter @Setter
public class WechatReminderProperties {
    private boolean enabled=false;
    private int leadMinutes=15;
    private String miniprogramState="formal";
    private String location="云大射击训练中心";
    private Template training=new Template();
    private Template duty=new Template();
    @Getter @Setter
    public static class Template {
        private String templateId="";
        private String fieldsJson="{}";
    }
}
