package com.quanwei.gogo.agent.exception;

import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import lombok.Getter;

/**
 * 业务异常。
 * 所有可预期的业务失败都抛这个，配套的提示信息统一来自 ErrorCodeEnum。
 */
@Getter
public class BizException extends RuntimeException {

    /** 对应的错误码 */
    private final ErrorCodeEnum errorCode;

    public BizException(ErrorCodeEnum errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    /**
     * 在枚举定义的提示后面补一段细节，比如具体是哪个账号重复了
     *
     * @param detail 补充信息，会以「提示：细节」的形式拼接
     */
    public BizException(ErrorCodeEnum errorCode, String detail) {
        super(errorCode.getMsg() + "：" + detail);
        this.errorCode = errorCode;
    }
}
