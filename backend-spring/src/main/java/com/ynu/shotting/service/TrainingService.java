package com.ynu.shoting.service;

import com.ynu.shoting.dto.ScoreRequest;
import com.ynu.shoting.dto.TrainingVO;
import com.ynu.shoting.dto.ScoreVO;
import com.ynu.shoting.entity.Booking;
import com.ynu.shoting.entity.Score;
import com.ynu.shoting.entity.TrainingSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public interface TrainingService {

    /** 开始训练（签到后 → 训练中） */
    TrainingVO start(HttpServletRequest request, Long bookingId, String mode);

    TrainingVO resume(HttpServletRequest request, Long sessionId);

    /** 结束训练 */
    TrainingVO finish(HttpServletRequest request, Long sessionId);

    /** 提交成绩（管理员/教练） */
    ScoreVO submitScore(HttpServletRequest request, ScoreRequest req);

    /** 我的训练记录 */
    List<TrainingVO> mySessions(HttpServletRequest request);
}
