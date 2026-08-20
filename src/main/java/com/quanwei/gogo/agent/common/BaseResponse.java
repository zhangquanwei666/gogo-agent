package com.quanwei.gogo.agent.common;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 响应基类，所有出参 DTO 继承它。
 * 业务字段和 code / msg 平铺在 JSON 同一层。
 * 本类也可直接实例化，用于没有业务数据的失败响应。
 */
@Getter
@Setter
@ToString
public class BaseResponse implements Serializable {

    /** 业务状态码，200 表示成功 */
    private int code = ErrorCodeEnum.SUCCESS.getCode();

    /** 提示信息 */
    private String msg = ErrorCodeEnum.SUCCESS.getMsg();

    /** 按错误码构造失败响应 */
    public static BaseResponse fail(ErrorCodeEnum errorCode) {
        return fail(errorCode.getCode(), errorCode.getMsg());
    }

    /** 按错误码构造失败响应，msg 用调用方补充过细节的版本 */
    public static BaseResponse fail(int code, String msg) {
        BaseResponse response = new BaseResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }
}
