package com.quanwei.gogo.agent.service;

import com.quanwei.gogo.agent.entity.AgentscopeSession;

import java.util.List;

/**
 * AgentScope 会话状态存取
 */
public interface AgentscopeSessionService {

    /**
     * 读取某个 state_key 下的全部状态项，按 item_index 正序。
     * 没有数据时返回空集合，不返回 null。
     */
    List<String> listState(String sessionId, String stateKey);

    /** 读整个会话的全部状态 */
    List<AgentscopeSession> listBySessionId(String sessionId);
}
