package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.github.pagehelper.PageInfo;

import java.util.List;

/** 风险组、风险规则和风险命中审计记录的运营管理服务。 */
public interface RiskManagementService {
    /** 分页查询风险组。 */
    ResultVO<PageInfo<RiskGroup>> pageGroups(String groupName, Integer pageNum, Integer pageSize);

    /** 读取规则编辑页所需的风险组下拉选项。 */
    ResultVO<List<RiskGroup>> listGroupOptions();

    /** 新增风险组。 */
    ResultVO<Void> createGroup(RiskGroup request);

    /** 修改风险组。 */
    ResultVO<Void> updateGroup(Long groupId, RiskGroup request);

    /** 删除未被风险规则引用的风险组。 */
    ResultVO<Void> deleteGroup(Long groupId);

    /** 分页查询风险规则及其所属风险组名称。 */
    ResultVO<PageInfo<RiskRule>> pageRules(String ruleId, String ruleName, Long groupId, Integer pageNum, Integer pageSize);

    /** 新增风险规则。 */
    ResultVO<Void> createRule(RiskRule request);

    /** 按规则编号修改风险规则。 */
    ResultVO<Void> updateRule(String ruleId, RiskRule request);

    /** 删除风险规则，不删除历史风险命中记录。 */
    ResultVO<Void> deleteRule(String ruleId);

    /** 分页查询只读风险控制命中审计记录。 */
    ResultVO<PageInfo<RiskControlLog>> pageControlLogs(String cardId, String userOrderNo, String ruleId,
                                                       String riskHitTimeBegin, String riskHitTimeEnd,
                                                       Integer pageNum, Integer pageSize);
}
