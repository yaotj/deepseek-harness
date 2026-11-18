package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.risk.GraylistRisk;
import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.chinasofti.huateng.para.service.GraylistRiskService;
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


@RestController
@RequestMapping("/page/risk")
public class RiskManagementController {
    private final RiskManagementService riskManagementService;
    private final GraylistRiskService graylistRiskService;

    public RiskManagementController(RiskManagementService riskManagementService, 
                                     GraylistRiskService graylistRiskService) {
        this.riskManagementService = riskManagementService;
        this.graylistRiskService = graylistRiskService;
    }

    @GetMapping("/groups")
    public ResultVO<PageInfo<RiskGroup>> pageGroups(@RequestParam(required = false) String groupName,
                                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        return riskManagementService.pageGroups(groupName, pageNum, pageSize);
    }

    @GetMapping("/groups/options")
    public ResultVO<List<RiskGroup>> groupOptions() { return riskManagementService.listGroupOptions(); }

    @PostMapping("/groups")
    public ResultVO<Void> createGroup(@RequestBody RiskGroup request) { return riskManagementService.createGroup(request); }

    @PutMapping("/groups/{groupId}")
    public ResultVO<Void> updateGroup(@PathVariable Long groupId, @RequestBody RiskGroup request) {
        return riskManagementService.updateGroup(groupId, request);
    }

    @DeleteMapping("/groups/{groupId}")
    public ResultVO<Void> deleteGroup(@PathVariable Long groupId) { return riskManagementService.deleteGroup(groupId); }

    @GetMapping("/rules")
    public ResultVO<PageInfo<RiskRule>> pageRules(@RequestParam(required = false) String ruleId,
                                                   @RequestParam(required = false) String ruleName,
                                                   @RequestParam(required = false) Long groupId,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return riskManagementService.pageRules(ruleId, ruleName, groupId, pageNum, pageSize);
    }

    @PostMapping("/rules")
    public ResultVO<Void> createRule(@RequestBody RiskRule request) { return riskManagementService.createRule(request); }

    @PutMapping("/rules/{ruleId}")
    public ResultVO<Void> updateRule(@PathVariable String ruleId, @RequestBody RiskRule request) {
        return riskManagementService.updateRule(ruleId, request);
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResultVO<Void> deleteRule(@PathVariable String ruleId) { return riskManagementService.deleteRule(ruleId); }

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

    @GetMapping("/graylist")
    public ResultVO<PageInfo<GraylistRisk>> pageGraylist(@RequestParam(required = false) String cardId,
                                                          @RequestParam(required = false) String ruleName,
                                                          @RequestParam(required = false) String updateTimeBegin,
                                                          @RequestParam(required = false) String updateTimeEnd,
                                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return graylistRiskService.page(cardId, ruleName, updateTimeBegin, updateTimeEnd, pageNum, pageSize);
    }
}
