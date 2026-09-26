package com.ynu.shoting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Explicit role-aware projection; never return User/Profile entities. */
public record SlotDetailVO(String date, String slotId, String start, String end,
                           String viewerScope, Instant updatedAt, List<DeviceRow> devices) {
    public record DeviceRow(Long deviceId, String deviceName, String deviceType,
                            String deviceStatus, boolean staffed, boolean available, boolean coachPresent, boolean walkInAvailable,
                            List<Reservation> reservations) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Reservation(Long bookingId, String status, String displayName,
                              String studentNo, boolean mine) {}
}
