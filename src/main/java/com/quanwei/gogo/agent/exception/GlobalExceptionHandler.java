package com.quanwei.gogo.agent.exception;

import com.quanwei.gogo.agent.common.BaseResponse;
import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理，避免业务异常直接以 500 堆栈页的形式抛给前端。
 * 提示信息一律来自 ErrorCodeEnum，这里不写裸字符串。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 可预期的业务异常，code / msg 来自异常携带的错误码 */
    @ExceptionHandler(BizException.class)
    public BaseResponse handleBiz(BizException e) {
        log.warn("业务异常：code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        return BaseResponse.fail(e.getErrorCode().getCode(), e.getMessage());
    }

    /** 参数不合法，比如 Spring 反序列化阶段抛的 */
    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常：{}", e.getMessage());
        return BaseResponse.fail(ErrorCodeEnum.PARAM_INVALID);
    }

    /** 兜底，不把内部异常信息抛给前端 */
    @ExceptionHandler(Exception.class)
    public BaseResponse handleOther(Exception e) {
        log.error("未预期的异常", e);
        return BaseResponse.fail(ErrorCodeEnum.SYSTEM_ERROR);
    }
}
