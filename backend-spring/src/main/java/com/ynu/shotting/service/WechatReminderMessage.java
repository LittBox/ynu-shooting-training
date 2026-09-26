package com.ynu.shoting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynu.shoting.config.WechatReminderProperties;
import com.ynu.shoting.entity.WechatReminder.Kind;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component @RequiredArgsConstructor
public class WechatReminderMessage {
    private final WechatReminderProperties config;
    private final ObjectMapper json;
    public record Target(Long userId,String openid,String name,LocalDateTime startsAt,LocalDateTime endsAt,String title,String reference,boolean eligible) {}
    public WechatReminderProperties.Template template(Kind kind) {return kind==Kind.TRAINING?config.getTraining():config.getDuty();}
    @PostConstruct void validateConfig() {
        if(config.getLeadMinutes()<1 || config.getLeadMinutes()>1440 || !Set.of("formal","trial","developer").contains(config.getMiniprogramState()))
            throw new IllegalStateException("Invalid reminder lead time or mini program state");
        if(config.isEnabled()) for(Kind kind:Kind.values()) {
            var t=template(kind);
            if(!t.getTemplateId().isBlank())fields(kind); // Allows enabling one kind while the other template is awaiting approval.
        }
    }
    public boolean ready(Kind kind) {return config.isEnabled() && !template(kind).getTemplateId().isBlank() && !fields(kind).isEmpty();}
    private Map<String,String> fields(Kind kind) {
        Map<String,String> fields;
        try {fields=json.readValue(template(kind).getFieldsJson(),new TypeReference<LinkedHashMap<String,String>>(){});}
        catch(Exception ex){throw new IllegalStateException("Reminder fields must be a JSON object of template keys and source names");}
        if(fields==null || fields.isEmpty() || fields.size()>10)throw new IllegalStateException("Reminder template fields are missing");
        fields.forEach((key,source)->{
            if(source==null || !Set.of("name","title","start","end","location","remark","reference","duration","status").contains(source)
                || !key.matches("(thing|name|time|date|character_string|number|phrase)[0-9]+"))
                throw new IllegalStateException("Unsupported reminder template field mapping");
            if((key.startsWith("time")||key.startsWith("date")) && !Set.of("start","end").contains(source)
                || key.startsWith("number") && !Set.of("duration","reference").contains(source)
                || key.startsWith("character_string") && !"reference".equals(source)
                || key.startsWith("phrase") && !"status".equals(source)
                || key.startsWith("name") && !"name".equals(source))
                throw new IllegalStateException("Reminder field type does not match source");
        });
        return fields;
    }
    public Map<String,Object> payload(Kind kind,Long targetId,Target target) {
        String page=kind==Kind.TRAINING?"pages/booking/booking?view=mine":"pages/my/my?dutyDate="+target.startsAt().toLocalDate();
        var values=Map.of("name",target.name(),"title",target.title(),"start",format(target.startsAt()),"end",format(target.endsAt()),
            "location",config.getLocation(),"remark",kind==Kind.TRAINING?"请按时到场参加训练":"请按时到场并确认到岗", "reference",target.reference(),
            "duration",String.valueOf(java.time.Duration.between(target.startsAt(),target.endsAt()).toMinutes()),"status","待开始");
        Map<String,Object> data=new LinkedHashMap<>();
        fields(kind).forEach((key,source)->{
            String value=values.get(source).replaceAll("[\\p{Cntrl}]"," ").trim();
            int limit=key.startsWith("thing")?20:key.startsWith("name")?10:32;
            if(!key.startsWith("time")&&!key.startsWith("date"))value=shorten(value,limit);
            if(value.isBlank())throw new IllegalStateException("Reminder field is empty");
            data.put(key,Map.of("value",value));
        });
        return Map.of("touser",target.openid(),"template_id",template(kind).getTemplateId(),"page",page,"data",data,
            "miniprogram_state",config.getMiniprogramState(),"lang","zh_CN");
    }
    private String format(LocalDateTime value) {return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}
    static String shorten(String value,int max) {return value.codePointCount(0,value.length())<=max?value:value.substring(0,value.offsetByCodePoints(0,max));}
}
