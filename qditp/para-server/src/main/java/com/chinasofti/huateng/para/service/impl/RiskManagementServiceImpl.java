package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.chinasofti.huateng.para.mapper.risk.RiskControlLogMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskGroupMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskGroupRuleMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskRuleMapper;
import com.chinasofti.huateng.para.service.RiskManagementService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RiskManagementServiceImpl implements RiskManagementService {
    private static final int EXPORT_LIMIT = 20000;

    private final RiskGroupMapper riskGroupMapper;
    private final RiskRuleMapper riskRuleMapper;
    private final RiskGroupRuleMapper riskGroupRuleMapper;
    private final RiskControlLogMapper riskControlLogMapper;

    public RiskManagementServiceImpl(RiskGroupMapper riskGroupMapper, RiskRuleMapper riskRuleMapper,
                                     RiskGroupRuleMapper riskGroupRuleMapper,
                                     RiskControlLogMapper riskControlLogMapper) {
        this.riskGroupMapper = riskGroupMapper;
        this.riskRuleMapper = riskRuleMapper;
        this.riskGroupRuleMapper = riskGroupRuleMapper;
        this.riskControlLogMapper = riskControlLogMapper;
    }

    @Override
    public ResultVO<PageInfo<RiskGroup>> pageGroups(String groupName, Integer pageNum, Integer pageSize) {
        PageInfo<RiskGroup> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> riskGroupMapper.selectPage(trimToNull(groupName)));
        return ResultMapper.ok(page);
    }

    @Override
    public ResultVO<List<RiskGroup>> listGroupOptions() {
        return ResultMapper.ok(riskGroupMapper.selectOptions());
    }

    @Override
    public ResultVO<Void> createGroup(RiskGroup request) {
        String message = validateGroup(request);
        if (message != null) return ResultMapper.illegalParams(message);
        normalizeGroup(request);
        try {
            riskGroupMapper.insert(request);
            return ResultMapper.ok();
        } catch (Exception exception) {
            if (!isDuplicateKeyViolation(exception)) throw exception;
            return ResultMapper.error("风险组名称已存在");
        }
    }

    @Override
    public ResultVO<Void> updateGroup(Long groupId, RiskGroup request) {
        if (groupId == null) return ResultMapper.illegalParams("风险组ID不能为空");
        String message = validateGroup(request);
        if (message != null) return ResultMapper.illegalParams(message);
        normalizeGroup(request);
        request.setGroupId(groupId);
        try {
            return riskGroupMapper.update(request) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待修改的风险组");
        } catch (Exception exception) {
            if (!isDuplicateKeyViolation(exception)) throw exception;
            return ResultMapper.error("风险组名称已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> deleteGroup(Long groupId) {
        if (groupId == null) return ResultMapper.illegalParams("风险组ID不能为空");
        if (riskGroupRuleMapper.countByGroupId(groupId) > 0) {
            return ResultMapper.error("该风险组下存在风险规则，不能删除");
        }
        return riskGroupMapper.deleteById(groupId) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待删除的风险组");
    }

    @Override
    public ResultVO<List<String>> listRuleIdsByGroup(Long groupId) {
        if (groupId == null) return ResultMapper.illegalParams("风险组ID不能为空");
        return ResultMapper.ok(riskGroupRuleMapper.selectRuleIdsByGroupId(groupId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> bindRulesToGroup(Long groupId, List<String> ruleIds) {
        if (groupId == null) return ResultMapper.illegalParams("风险组ID不能为空");
        List<String> valid = ruleIds == null ? new ArrayList<>() : new ArrayList<>(ruleIds);
        Set<String> seen = new HashSet<>();
        valid.removeIf(id -> !StringUtils.hasText(id) || !seen.add(id.trim()));
        valid.replaceAll(String::trim);
        if (riskGroupMapper.selectOptions().stream().noneMatch(g -> groupId.equals(g.getGroupId()))) {
            return ResultMapper.error("风险组不存在");
        }
        for (String ruleId : valid) {
            if (riskRuleMapper.countById(ruleId) == 0) return ResultMapper.error("风险规则不存在：" + ruleId);
        }
        riskGroupRuleMapper.deleteByGroupId(groupId);
        for (String ruleId : valid) riskGroupRuleMapper.insert(groupId, ruleId);
        return ResultMapper.ok();
    }

    @Override
    public ResultVO<PageInfo<RiskRule>> pageRules(String ruleId, String ruleName, Long groupId, Integer pageNum, Integer pageSize) {
        PageInfo<RiskRule> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> riskRuleMapper.selectPage(trimToNull(ruleId), trimToNull(ruleName), groupId));
        return ResultMapper.ok(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> createRule(RiskRule request) {
        String message = validateRule(request, true);
        if (message != null) return ResultMapper.illegalParams(message);
        normalizeRule(request);
        try {
            riskRuleMapper.insert(request);
            bindRuleGroups(request.getRuleId(), request.getGroupIds());
            return ResultMapper.ok();
        } catch (Exception exception) {
            if (!isDuplicateKeyViolation(exception)) throw exception;
            return ResultMapper.error("风险规则编号已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> updateRule(String ruleId, RiskRule request) {
        if (!StringUtils.hasText(ruleId)) return ResultMapper.illegalParams("风险规则编号不能为空");
        String message = validateRule(request, false);
        if (message != null) return ResultMapper.illegalParams(message);
        normalizeRule(request);
        request.setRuleId(ruleId.trim());
        try {
            int updated = riskRuleMapper.update(request);
            if (updated == 0) return ResultMapper.error("未找到待修改的风险规则");
            bindRuleGroups(request.getRuleId(), request.getGroupIds());
            return ResultMapper.ok();
        } catch (Exception exception) {
            if (!isDuplicateKeyViolation(exception)) throw exception;
            throw exception;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<Void> deleteRule(String ruleId) {
        if (!StringUtils.hasText(ruleId)) return ResultMapper.illegalParams("风险规则编号不能为空");
        String id = ruleId.trim();
        riskGroupRuleMapper.deleteByRuleId(id);
        return riskRuleMapper.deleteById(id) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待删除的风险规则");
    }

    @Override
    public ResultVO<PageInfo<RiskControlLog>> pageControlLogs(String cardId, String userOrderNo, String ruleId,
                                                               String riskStatus, String riskHitTimeBegin, String riskHitTimeEnd,
                                                               Integer pageNum, Integer pageSize) {
        PageInfo<RiskControlLog> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> riskControlLogMapper.selectPage(trimToNull(cardId), trimToNull(userOrderNo),
                        trimToNull(ruleId), trimToNull(riskStatus), trimToNull(riskHitTimeBegin), trimToNull(riskHitTimeEnd)));
        return ResultMapper.ok(page);
    }

    @Override
    public ResultVO<List<RiskControlLog>> exportControlLogs(String cardId, String userOrderNo, String ruleId,
                                                            String riskStatus, String riskHitTimeBegin, String riskHitTimeEnd) {
        return ResultMapper.ok(riskControlLogMapper.selectForExport(trimToNull(cardId), trimToNull(userOrderNo),
                trimToNull(ruleId), trimToNull(riskStatus), trimToNull(riskHitTimeBegin), trimToNull(riskHitTimeEnd),
                EXPORT_LIMIT));
    }

    @Override
    public ResultVO<Void> createControlLog(RiskControlLog request) {
        if (request == null) return ResultMapper.illegalParams("请求参数不能为空");
        if (!StringUtils.hasText(request.getCardId())) return ResultMapper.illegalParams("逻辑卡号不能为空");
        if (!StringUtils.hasText(request.getRuleId())) return ResultMapper.illegalParams("风险规则编号不能为空");
        if (request.getRiskHitTime() == null) return ResultMapper.illegalParams("风险发生时间不能为空");
        if (request.getRuleNameSnap() == null || request.getRuleDescSnap() == null || request.getRiskLevelSnap() == null) {
            RiskRule rule = riskRuleMapper.selectPage(request.getRuleId().trim(), null, null)
                    .stream().findFirst().orElse(null);
            if (rule != null) {
                if (request.getRuleNameSnap() == null) request.setRuleNameSnap(rule.getRuleName());
                if (request.getRuleDescSnap() == null) request.setRuleDescSnap(rule.getRuleDesc());
                if (request.getRiskLevelSnap() == null) request.setRiskLevelSnap(rule.getRiskLevel());
            }
        }
        riskControlLogMapper.insert(request);
        return ResultMapper.ok();
    }

    private void bindRuleGroups(String ruleId, List<Long> groupIds) {
        riskGroupRuleMapper.deleteByRuleId(ruleId);
        if (groupIds == null) return;
        Set<Long> unique = new HashSet<>(groupIds);
        for (Long groupId : unique) {
            if (groupId != null) riskGroupRuleMapper.insert(groupId, ruleId);
        }
    }

    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) return true;
        }
        return false;
    }

    private String validateGroup(RiskGroup request) {
        if (request == null || !StringUtils.hasText(request.getGroupName())) return "风险组名称不能为空";
        if (request.getGroupName().trim().length() > 100) return "风险组名称不能超过100个字符";
        if (request.getGroupDesc() != null && request.getGroupDesc().length() > 500) return "风险组描述不能超过500个字符";
        return null;
    }

    private String validateRule(RiskRule request, boolean requireRuleId) {
        if (request == null) return "请求参数不能为空";
        if (requireRuleId && !StringUtils.hasText(request.getRuleId())) return "风险规则编号不能为空";
        if (requireRuleId && request.getRuleId().trim().length() > 16) return "风险规则编号不能超过16个字符";
        if (!StringUtils.hasText(request.getRuleName())) return "风险规则名称不能为空";
        if (request.getRuleName().trim().length() > 100) return "风险规则名称不能超过100个字符";
        if (request.getRuleDesc() != null && request.getRuleDesc().length() > 1000) return "风险规则描述不能超过1000个字符";
        if (request.getRiskLevel() == null || request.getRiskLevel() < 1 || request.getRiskLevel() > 5) return "风险等级必须在1到5之间";
        BigDecimal value = request.getRiskLimitValue();
        if (value == null || value.signum() < 0) return "风险规则最大值不能小于0";
        return null;
    }

    private void normalizeGroup(RiskGroup request) {
        request.setGroupName(request.getGroupName().trim());
        request.setGroupDesc(trimToNull(request.getGroupDesc()));
    }

    private void normalizeRule(RiskRule request) {
        if (StringUtils.hasText(request.getRuleId())) request.setRuleId(request.getRuleId().trim());
        request.setRuleName(request.getRuleName().trim());
        request.setRuleDesc(trimToNull(request.getRuleDesc()));
    }

    private int safePageNum(Integer pageNum) { return pageNum == null || pageNum < 1 ? 1 : pageNum; }
    private int safePageSize(Integer pageSize) { return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100); }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
