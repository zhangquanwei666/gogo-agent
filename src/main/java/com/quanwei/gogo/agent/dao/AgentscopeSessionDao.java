package com.quanwei.gogo.agent.dao;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quanwei.gogo.agent.entity.AgentscopeSession;
import com.quanwei.gogo.agent.mapper.AgentscopeSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * agentscope_session 数据访问层。
 */
@Repository
public class AgentscopeSessionDao {

    @Autowired
    private AgentscopeSessionMapper agentscopeSessionMapper;

    /** 新增一条状态 */
    public int insert(AgentscopeSession session) {
        return agentscopeSessionMapper.insert(session);
    }

    /** 查某个 state_key 下的全部状态*/
    public List<AgentscopeSession> selectBySessionIdAndStateKey(String sessionId, String stateKey) {
        return agentscopeSessionMapper.selectList(Wrappers.<AgentscopeSession>lambdaQuery()
                .eq(AgentscopeSession::getSessionId, sessionId)
                .eq(AgentscopeSession::getStateKey, stateKey)
                .orderByAsc(AgentscopeSession::getItemIndex));
    }

    /** 查整个会话的全部状态，先按 state_key 再按 item_index 排 */
    public List<AgentscopeSession> selectBySessionId(String sessionId) {
        return agentscopeSessionMapper.selectList(Wrappers.<AgentscopeSession>lambdaQuery()
                .eq(AgentscopeSession::getSessionId, sessionId)
                .orderByAsc(AgentscopeSession::getStateKey)
                .orderByAsc(AgentscopeSession::getItemIndex));
    }
}
