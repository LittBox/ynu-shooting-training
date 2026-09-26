package com.ynu.shoting.service;

import com.ynu.shoting.dto.BookingRequest;
import com.ynu.shoting.dto.BookingVO;
import com.ynu.shoting.dto.SlotVO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

public interface BookingService {

    /** 创建预约（学生） */
    BookingVO create(HttpServletRequest request, BookingRequest req);

    BookingVO walkIn(HttpServletRequest request, com.ynu.shoting.dto.WalkInRequest req);

    /** 取消预约 */
    void cancel(HttpServletRequest request, Long bookingId);

    /** 签到 */
    BookingVO checkIn(HttpServletRequest request, Long bookingId);

    /** 我的预约列表（学生） */
    List<BookingVO> myBookings(HttpServletRequest request);

    /** 查询某日某时间片的占用情况（所有用户） */
    List<SlotVO> dailySlots(LocalDate date, String deviceType);

    /** 单条预约详情 */
    BookingVO detail(HttpServletRequest request, Long bookingId);
}
