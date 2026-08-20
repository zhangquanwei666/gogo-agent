package com.quanwei.gogo.agent.common;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 请求基类，所有入参 DTO 继承它。
 * 公共字段平铺在 JSON 同一层，不额外包一层 data。
 */
@Getter
@Setter
@ToString
public class BaseRequest implements Serializable {

    /** 链路追踪 ID，前端不传时可由网关或拦截器补 */
    private String traceId;
}
