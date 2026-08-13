package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.chinasofti.huateng.para.service.RiskManagementService;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营端风险参数及风险命中记录接口。
 * <p>风险组和风险规则可维护；命中记录属于审计数据，仅允许分页查询。</p>
 */
@RestController
@RequestMapping("/page/risk")
public class RiskManagementController {
    private final RiskManagementService riskManagementService;

    public RiskManagementController(RiskManagementService riskManagementService) {
        this.riskManagementService = riskManagementService;
    }

    /** 分页查询风险组，供风险组管理页使用。 */
    @GetMapping("/groups")
    public ResultVO<PageInfo<RiskGroup>> pageGroups(@RequestParam(required = false) String groupName,
                                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        return riskManagementService.pageGroups(groupName, pageNum, pageSize);
    }

    /** 返回全部风险组，供风险规则新增和修改时选择归属分组。 */
    @GetMapping("/groups/options")
    public ResultVO<List<RiskGroup>> groupOptions() { return riskManagementService.listGroupOptions(); }

    /** 新增风险组。 */
    @PostMapping("/groups")
    public ResultVO<Void> createGroup(@RequestBody RiskGroup request) { return riskManagementService.createGroup(request); }

    /** 修改风险组名称或描述。 */
    @PutMapping("/groups/{groupId}")
    public ResultVO<Void> updateGroup(@PathVariable Long groupId, @RequestBody RiskGroup request) {
        return riskManagementService.updateGroup(groupId, request);
    }

    /** 删除无关联规则的风险组。 */
    @DeleteMapping("/groups/{groupId}")
    public ResultVO<Void> deleteGroup(@PathVariable Long groupId) { return riskManagementService.deleteGroup(groupId); }

    /** 按规则编号、名称或风险组分页查询风险规则。 */
    @GetMapping("/rules")
    public ResultVO<PageInfo<RiskRule>> pageRules(@RequestParam(required = false) String ruleId,
                                                   @RequestParam(required = false) String ruleName,
                                                   @RequestParam(required = false) Long groupId,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return riskManagementService.pageRules(ruleId, ruleName, groupId, pageNum, pageSize);
    }

    /** 新增风险规则，规则编号在创建后不可变更。 */
    @PostMapping("/rules")
    public ResultVO<Void> createRule(@RequestBody RiskRule request) { return riskManagementService.createRule(request); }

    /** 修改指定规则的阈值、等级及关联风险组。 */
    @PutMapping("/rules/{ruleId}")
    public ResultVO<Void> updateRule(@PathVariable String ruleId, @RequestBody RiskRule request) {
        return riskManagementService.updateRule(ruleId, request);
    }

    /** 删除风险规则；历史命中日志仍保留规则编号用于审计。 */
    @DeleteMapping("/rules/{ruleId}")
    public ResultVO<Void> deleteRule(@PathVariable String ruleId) { return riskManagementService.deleteRule(ruleId); }

    /** 分页查询风险规则命中记录，不提供页面侧写入或删除能力。 */
    @GetMapping("/control-logs")
    public ResultVO<PageInfo<RiskControlLog>> pageControlLogs(@RequestParam(required = false) String cardId,
                                                               @RequestParam(required = false) String userOrderNo,
                                                               @RequestParam(required = false) String ruleId,
                                                               @RequestParam(required = false) String riskHitTimeBegin,
                                                               @RequestParam(required = false) String riskHitTimeEnd,
                                                               @RequestParam(defaultValue = "1") Integer pageNum,
                                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        return riskManagementService.pageControlLogs(cardId, userOrderNo, ruleId, riskHitTimeBegin, riskHitTimeEnd, pageNum, pageSize);
    }
}
