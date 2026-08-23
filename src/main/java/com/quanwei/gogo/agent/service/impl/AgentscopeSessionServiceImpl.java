package com.quanwei.gogo.agent.service.impl;

import com.quanwei.gogo.agent.common.ErrorCodeEnum;
import com.quanwei.gogo.agent.dao.AgentscopeSessionDao;
import com.quanwei.gogo.agent.entity.AgentscopeSession;
import com.quanwei.gogo.agent.exception.BizException;
import com.quanwei.gogo.agent.service.AgentscopeSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AgentscopeSessionServiceImpl implements AgentscopeSessionService {

    @Autowired
    private AgentscopeSessionDao agentscopeSessionDao;

    @Override
    public List<String> listState(String sessionId, String stateKey) {
        // Dao 已经按 item_index 正序，这里直接抽 state_data，顺序就是原来的顺序
        return agentscopeSessionDao.selectBySessionIdAndStateKey(sessionId, stateKey).stream()
                .map(AgentscopeSession::getStateData)
                .toList();
    }

    @Override
    public List<AgentscopeSession> listBySessionId(String sessionId) {
        return agentscopeSessionDao.selectBySessionId(sessionId);
    }

}
