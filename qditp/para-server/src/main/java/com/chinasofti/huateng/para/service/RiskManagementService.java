package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface RiskManagementService {
    ResultVO<PageInfo<RiskGroup>> pageGroups(String groupName, Integer pageNum, Integer pageSize);

    ResultVO<List<RiskGroup>> listGroupOptions();

    ResultVO<Void> createGroup(RiskGroup request);

    ResultVO<Void> updateGroup(Long groupId, RiskGroup request);

    ResultVO<Void> deleteGroup(Long groupId);

    ResultVO<List<String>> listRuleIdsByGroup(Long groupId);

    ResultVO<Void> bindRulesToGroup(Long groupId, List<String> ruleIds);

    ResultVO<PageInfo<RiskRule>> pageRules(String ruleId, String ruleName, Long groupId, Integer pageNum, Integer pageSize);

    ResultVO<Void> createRule(RiskRule request);

    ResultVO<Void> updateRule(String ruleId, RiskRule request);

    ResultVO<Void> deleteRule(String ruleId);

    ResultVO<PageInfo<RiskControlLog>> pageControlLogs(String cardId, String userOrderNo, String ruleId,
                                                       String riskStatus, String riskHitTimeBegin, String riskHitTimeEnd,
                                                       Integer pageNum, Integer pageSize);

    ResultVO<List<RiskControlLog>> exportControlLogs(String cardId, String userOrderNo, String ruleId,
                                                     String riskStatus, String riskHitTimeBegin, String riskHitTimeEnd);

    ResultVO<Void> createControlLog(RiskControlLog request);
}
