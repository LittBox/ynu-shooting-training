package com.ynu.shoting.controller;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.config.Slot;
import com.ynu.shoting.security.AuthContext;
import com.ynu.shoting.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/users/me/availability") @RequiredArgsConstructor
public class AvailabilityController {
    private final AuthContext auth;
    private final UserRepository users;
    private final UserAvailabilityRepository available;
    private final BookingRepository bookings;
    public record AvailabilityRequest(@NotBlank String date,@NotNull @Size(max=6) List<String> slotIds) {}
    @GetMapping @Transactional(readOnly=true) public ApiResponse<?> mine(HttpServletRequest req) {
        return ApiResponse.ok(available.findByUserId(auth.currentUser(req).getId()).stream().map(a -> Map.of("date",a.getSlotDate(),"slotId",a.getSlotId())).toList());
    }
    @PutMapping @Transactional public ApiResponse<?> update(HttpServletRequest req,@Valid @RequestBody AvailabilityRequest body) {
        User user=users.findLockedById(auth.currentUser(req).getId()).orElseThrow();
        LocalDate.parse(body.date()); body.slotIds().forEach(Slot::fromId);
        if(bookings.findActiveByUserAndDate(user.getId(),body.date()).stream().anyMatch(b -> !body.slotIds().contains(b.getSlotId())))
            throw new BusinessException(409,"请先取消该时段预约，再修改可训练时间");
        available.deleteByUserIdAndSlotDate(user.getId(),body.date());
        for(String slot:new HashSet<>(body.slotIds())) available.save(UserAvailability.builder().user(user).slotDate(body.date()).slotId(slot).build());
        return ApiResponse.ok();
    }
}
