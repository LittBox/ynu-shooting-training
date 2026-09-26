package com.ynu.shoting.exception;
/** A committed business transition, not a failed write. */
public class CheckInExpiredException extends BusinessException {
    public CheckInExpiredException() { super(410, "签到已超时，预约已标记为爽约"); }
}
