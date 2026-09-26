package com.ynu.shoting.service;

import com.ynu.shoting.config.*;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.entity.WechatReminder.*;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class WechatReminderService {
    private final WechatReminderRepository reminders;
    private final BookingRepository bookings;
    private final AdminScheduleRepository schedules;
    private final VenueAccess venue;
    private final AuthContext auth;
    private final WechatReminderProperties config;
    private final WechatReminderMessage messages;
    private final WechatSubscriptionClient client;
    private final Clock clock;
    public record View(boolean enabled,String templateId,int leadMinutes,String status,boolean canSubscribe,LocalDateTime scheduledAt,String message) {}
    public record Delivery(Long id,Map<String,Object> payload) {}
    private LocalDateTime now() {return LocalDateTime.now(clock);}
    private String name(User user) {return user.getProfile()==null?"队员":user.getProfile().getRealName();}
    private WechatReminderMessage.Target target(Kind kind,Long id) {
        if(kind==Kind.TRAINING) {
            var b=bookings.findById(id).orElse(null);if(b==null)return null;
            var slot=Slot.fromId(b.getSlotId());var day=LocalDate.parse(b.getSlotDate());
            return new WechatReminderMessage.Target(b.getUser().getId(),b.getUser().getOpenid(),name(b.getUser()),day.atTime(slot.getStart()),day.atTime(slot.getEnd()),
                (b.getDevice().getType()==Device.DeviceType.pistol?"手枪训练":"步枪训练"),id.toString(),b.getStatus()==Booking.BookingStatus.BOOKED);
        }
        var s=schedules.findById(id).orElse(null);if(s==null)return null;
        var slot=Slot.fromId(s.getSlotId());var day=LocalDate.parse(s.getSlotDate());
        return new WechatReminderMessage.Target(s.getAdmin().getId(),s.getAdmin().getOpenid(),name(s.getAdmin()),day.atTime(slot.getStart()),day.atTime(slot.getEnd()),
            "射击场值班",id.toString(),s.getArrivedAt()==null && s.getAdmin().getRole()!=User.Role.student);
    }
    private WechatReminderMessage.Target owned(HttpServletRequest request,Kind kind,Long id) {
        User user=auth.currentUser(request);var target=target(kind,id);
        if(target==null)throw new BusinessException(404,"预约或排班不存在");
        if(!user.getId().equals(target.userId()) || kind==Kind.DUTY && user.getRole()==User.Role.student)
            throw new BusinessException(403,"只能设置本人的提醒");
        return target;
    }
    private boolean enabled(Kind kind,WechatReminderMessage.Target target) {
        return messages.ready(kind)&&client.configured()&&!target.openid().startsWith("mock:");
    }
    @Transactional(readOnly=true)
    public View status(HttpServletRequest request,Kind kind,Long id) {
        var target=owned(request,kind,id);
        return view(kind,target,reminders.findByKindAndTargetId(kind,id).orElse(null));
    }
    private View view(Kind kind,WechatReminderMessage.Target target,WechatReminder r) {
        boolean enabled=enabled(kind,target),eligible=target.eligible()&&now().isBefore(target.startsAt());
        String status=r==null?"NONE":r.getStatus().name();
        boolean templateChanged=r!=null&&!r.getTemplateId().equals(messages.template(kind).getTemplateId());
        boolean pending=r!=null && (Set.of(Status.SENDING,Status.SENT,Status.UNKNOWN).contains(r.getStatus()) || r.getStatus()==Status.PENDING&&!templateChanged);
        boolean canSubscribe=enabled&&eligible&&!pending;
        String message;
        if(!eligible)message="已到场、已取消或时段已开始，无需到场提醒";
        else if(!enabled)message="微信提醒暂未开放";
        else if(templateChanged && status.equals("PENDING"))message="提醒模板已更新，请重新订阅本次提醒";
        else message=switch(status) {
            case "PENDING" -> "已安排提醒，请留意微信服务通知";
            case "SENDING" -> "提醒正在发送";
            case "SENT" -> "提醒已提交微信，请查看服务通知";
            case "UNKNOWN" -> "发送结果待确认，请以预约或值班时间为准";
            case "FAILED" -> r!=null&&"43101".equals(r.getLastCode())?"微信订阅权限不足，请重新订阅本次提醒":"提醒未发送成功，可重新订阅";
            default -> "订阅后将在开始前 "+config.getLeadMinutes()+" 分钟提醒一次";
        };
        return new View(enabled,enabled?messages.template(kind).getTemplateId():null,config.getLeadMinutes(),status,canSubscribe,
            r==null?target.startsAt().minusMinutes(config.getLeadMinutes()):r.getScheduledAt(),message);
    }
    @Transactional
    public View subscribe(HttpServletRequest request,Kind kind,Long id,String templateId,boolean accepted) {
        venue.lock();var target=owned(request,kind,id);
        if(!enabled(kind,target))throw new BusinessException(409,"微信提醒尚未配置完成");
        if(!accepted || !messages.template(kind).getTemplateId().equals(templateId))throw new BusinessException(400,"请先同意本次微信订阅提醒");
        if(!target.eligible()||!now().isBefore(target.startsAt()))throw new BusinessException(409,"当前预约或值班不再需要提醒");
        var existing=reminders.findByKindAndTargetId(kind,id).orElse(null);
        if(existing!=null) {
            if(existing.getStatus()==Status.PENDING && existing.getTemplateId().equals(templateId))return view(kind,target,existing); // Callback/network retry does not duplicate a delivery.
            if(Set.of(Status.SENDING,Status.SENT,Status.UNKNOWN).contains(existing.getStatus()))
                throw new BusinessException(409,"本次提醒已进入发送流程，请勿重复订阅");
        }
        var r=existing==null?WechatReminder.builder().kind(kind).targetId(id).userId(target.userId()).build():existing;
        r.setTemplateId(templateId);r.setStatus(Status.PENDING);r.setStartsAt(target.startsAt());
        r.setScheduledAt(target.startsAt().minusMinutes(config.getLeadMinutes()));r.setNextAttemptAt(r.getScheduledAt());
        r.setSubscribedAt(now());r.setAttempts(0);r.setClaimedAt(null);r.setSentAt(null);r.setLastCode(null);
        reminders.save(r);return view(kind,target,r);
    }
    @Transactional
    public View cancel(HttpServletRequest request,Kind kind,Long id) {
        venue.lock();var target=owned(request,kind,id);var r=reminders.findByKindAndTargetId(kind,id).orElse(null);
        if(r!=null) {
            if(Set.of(Status.SENDING,Status.SENT,Status.UNKNOWN).contains(r.getStatus()))throw new BusinessException(409,"提醒已进入发送流程，无法取消");
            r.setStatus(Status.CANCELLED);r.setLastCode("USER_CANCELLED");
        }
        return view(kind,target,r);
    }
    @Transactional(readOnly=true)
    public List<Long> due() {return config.isEnabled()?reminders.findDue(now(),now().minusMinutes(2),PageRequest.of(0,50)):List.of();}
    @Transactional
    public Delivery claim(Long id) {
        // Same lock order as booking cancellation and duty deletion; release before HTTP.
        venue.lock();var r=reminders.findLockedById(id).orElse(null);
        if(r==null)return null;
        if(r.getStatus()==Status.SENDING) {
            if(r.getClaimedAt().isBefore(now().minusMinutes(2))) {r.setStatus(Status.UNKNOWN);r.setLastCode("INTERRUPTED_DELIVERY");}
            return null; // A crashed request could have reached WeChat; never blindly send twice.
        }
        if(!config.isEnabled()||r.getStatus()!=Status.PENDING||r.getNextAttemptAt().isAfter(now()))return null;
        var target=target(r.getKind(),r.getTargetId());
        if(target==null || !target.userId().equals(r.getUserId()) || !target.eligible() || !now().isBefore(target.startsAt()) || !target.startsAt().equals(r.getStartsAt())) {
            r.setStatus(Status.SKIPPED);r.setLastCode("NO_LONGER_NEEDED");return null;
        }
        if(!enabled(r.getKind(),target) || !r.getTemplateId().equals(messages.template(r.getKind()).getTemplateId())) {
            r.setStatus(Status.FAILED);r.setLastCode("TEMPLATE_CHANGED");return null;
        }
        Map<String,Object> payload;
        try {payload=messages.payload(r.getKind(),r.getTargetId(),target);}
        catch(RuntimeException ex){r.setStatus(Status.FAILED);r.setLastCode("INVALID_TEMPLATE_DATA");return null;}
        r.setStatus(Status.SENDING);r.setClaimedAt(now());r.setAttempts(r.getAttempts()+1);
        return new Delivery(id,payload);
    }
    @Transactional
    public void complete(Long id,WechatSubscriptionClient.Result result) {
        var r=reminders.findLockedById(id).orElseThrow();if(r.getStatus()!=Status.SENDING)return;
        r.setLastCode(result.code());
        switch(result.outcome()) {
            case SENT -> {r.setStatus(Status.SENT);r.setSentAt(now());}
            case UNKNOWN -> r.setStatus(Status.UNKNOWN);
            case FAILED -> r.setStatus(Status.FAILED);
            case RETRY -> {
                if(r.getAttempts()<3 && now().plusSeconds(30).isBefore(r.getStartsAt())) {
                    r.setStatus(Status.PENDING);r.setNextAttemptAt(now().plusSeconds(30));
                } else r.setStatus(Status.FAILED);
            }
        }
        if(r.getStatus()!=Status.SENT)log.warn("Wechat reminder result id={} status={} code={}",id,r.getStatus(),result.code());
    }
}
