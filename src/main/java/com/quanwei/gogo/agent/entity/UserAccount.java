package com.quanwei.gogo.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户登录账号表 user_account
 */
@Getter
@Setter
@ToString(exclude = "password")
@TableName("user_account")
public class UserAccount implements Serializable {

    /** 主键，用户唯一标识。由 service 生成 32 位 UUID，所以用 IdType.INPUT */
    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;

    /** 登录账号 */
    @TableField("username")
    private String username;

    /** 登录密码，存 BCrypt 密文，禁止存明文 */
    @TableField("password")
    private String password;

    /** 用户真实姓名 */
    @TableField("real_name")
    private String realName;

    /** 角色 */
    @TableField("role")
    private String role;

    /** 创建时间，由 MetaObjectHandler 自动填充 */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 最后变更时间，由 MetaObjectHandler 自动填充 */
    @TableField(value = "modify_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime modifyTime;
}
