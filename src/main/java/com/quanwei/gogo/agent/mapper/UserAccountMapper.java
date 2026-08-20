package com.quanwei.gogo.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quanwei.gogo.agent.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * user_account 表 mapper。
 *
 * <p>本包（mapper）的约定：
 * <ul>
 *   <li>单表操作直接用 BaseMapper 提供的能力，不写 SQL；</li>
 *   <li>涉及多表 join / 聚合的查询，在本包下定义 {@code XxxMapping} 接口（标 {@code @Mapper}），
 *       SQL 写在 {@code resources/mapper/XxxMapping.xml} 里，不用注解拼 SQL；</li>
 *   <li>多表查询的结果集用独立的 VO/DTO 承载，不要硬塞进单表实体；</li>
 *   <li>上层只允许 dao 包调用本包，service 不直接碰 mapper。</li>
 * </ul>
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
