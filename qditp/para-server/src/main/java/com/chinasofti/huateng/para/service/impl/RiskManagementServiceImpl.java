package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.chinasofti.huateng.para.mapper.risk.RiskControlLogMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskGroupMapper;
import com.chinasofti.huateng.para.mapper.risk.RiskRuleMapper;
import com.chinasofti.huateng.para.service.RiskManagementService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
/**
 * 风险管理实现。
 * <p>页面输入统一在此处做裁剪和边界校验，避免风险参数直接依赖数据库异常提示。</p>
 */
public class RiskManagementServiceImpl implements RiskManagementService {
    private final RiskGroupMapper riskGroupMapper;
    private final RiskRuleMapper riskRuleMapper;
    private final RiskControlLogMapper riskControlLogMapper;

    public RiskManagementServiceImpl(RiskGroupMapper riskGroupMapper, RiskRuleMapper riskRuleMapper,
                                     RiskControlLogMapper riskControlLogMapper) {
        this.riskGroupMapper = riskGroupMapper;
        this.riskRuleMapper = riskRuleMapper;
        this.riskControlLogMapper = riskControlLogMapper;
    }

    /** 分页查询风险组，名称条件为模糊匹配。 */
    @Override
    public ResultVO<PageInfo<RiskGroup>> pageGroups(String groupName, Integer pageNum, Integer pageSize) {
        PageInfo<RiskGroup> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> riskGroupMapper.selectPage(trimToNull(groupName)));
        return ResultMapper.ok(page);
    }

    /** 查询供规则维护页使用的完整风险组列表。 */
    @Override
    public ResultVO<List<RiskGroup>> listGroupOptions() {
        return ResultMapper.ok(riskGroupMapper.selectOptions());
    }

    /** 风险组名称唯一，重复时转为可读的业务错误。 */
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

    /** 修改风险组，重复名称同样转为业务错误。 */
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

    /** 删除前先判断规则引用，防止丢失规则归属关系。 */
    @Override
    public ResultVO<Void> deleteGroup(Long groupId) {
        if (groupId == null) return ResultMapper.illegalParams("风险组ID不能为空");
        if (riskRuleMapper.countByGroupId(groupId) > 0) return ResultMapper.error("该风险组下存在风险规则，不能删除");
        return riskGroupMapper.deleteById(groupId) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待删除的风险组");
    }

    /** 分页返回规则以及通过左关联取得的风险组名称。 */
    @Override
    public ResultVO<PageInfo<RiskRule>> pageRules(String ruleId, String ruleName, Long groupId, Integer pageNum, Integer pageSize) {
        PageInfo<RiskRule> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> riskRuleMapper.selectPage(trimToNull(ruleId), trimToNull(ruleName), groupId));
        return ResultMapper.ok(page);
    }

    /** 新建规则时校验编号、等级与非负阈值。 */
    @Override
    public ResultVO<Void> createRule(RiskRule request) {
        String message = validateRule(request, true);
        if (message != null) return ResultMapper.illegalParams(message);
        normalizeRule(request);
        try {
            riskRuleMapper.insert(request);
            return ResultMapper.ok();
        } catch (Exception exception) {
            if (!isDuplicateKeyViolation(exception)) throw exception;
            return ResultMapper.error("风险规则编号已存在，或所属风险组不存在");
        }
    }

    /** 规则编号取路径参数，防止请求体篡改主键。 */
    @Override
    public ResultVO<Void> updateRule(String ruleId, RiskRule request) {
        if (!StringUtils.hasText(ruleId)) return ResultMapper.illegalParams("风险规则编号不能为空");
        String message = validateRule(request, false);
        if (message != null) return ResultMapper.illegalParams(message);
        normalizeRule(request);
        request.setRuleId(ruleId.trim());
        try {
            return riskRuleMapper.update(request) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待修改的风险规则");
        } catch (Exception exception) {
            if (!isDuplicateKeyViolation(exception)) throw exception;
            return ResultMapper.error("所属风险组不存在");
        }
    }

    /**
     * 判断异常链上是否存在 {@link DuplicateKeyException}，即唯一约束冲突。
     *
     * <p><b>MUST</b> 逐层遍历 cause，<b>NEVER</b> 直接 {@code catch (DuplicateKeyException)}：
     * {@code MapperAspectToTrace}（{@code resource/micro/web/src/main/java/com/chinasofti/huateng/
     * micro/monitor/trace/MapperAspectToTrace.java:51}）把 mapper 抛出的任何异常统一包成
     * {@code RuntimeException}，单层类型判断在 {@code management.tracing.enabled=true}
     * 的模块（para-server 即是）捕不到。2026-09-08 实测：重复风险组名原本落到全局异常
     * 处理器、返回 UUID retCode，页面完全看不到「名称已存在」。</p>
     */
    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }

    /** 删除规则配置；风险控制历史日志不会级联删除。 */
    @Override
    public ResultVO<Void> deleteRule(String ruleId) {
        if (!StringUtils.hasText(ruleId)) return ResultMapper.illegalParams("风险规则编号不能为空");
        return riskRuleMapper.deleteById(ruleId.trim()) > 0 ? ResultMapper.ok() : ResultMapper.error("未找到待删除的风险规则");
    }

    /** 风险命中记录是审计数据，因此只提供查询。 */
    @Override
    public ResultVO<PageInfo<RiskControlLog>> pageControlLogs(String cardId, String userOrderNo, String ruleId,
                                                               String riskHitTimeBegin, String riskHitTimeEnd,
                                                               Integer pageNum, Integer pageSize) {
        PageInfo<RiskControlLog> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> riskControlLogMapper.selectPage(trimToNull(cardId), trimToNull(userOrderNo),
                        trimToNull(ruleId), trimToNull(riskHitTimeBegin), trimToNull(riskHitTimeEnd)));
        return ResultMapper.ok(page);
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
        if (request.getRiskLevel() == null || request.getRiskLevel() < 1 || request.getRiskLevel() > 5) return "风险等级必须在1到5之间";
        BigDecimal value = request.getRiskLimitValue();
        if (value == null || value.signum() < 0) return "风险规则最大值不能小于0";
        if (request.getManagerCode() != null && request.getManagerCode().length() > 64) return "管理编码不能超过64个字符";
        if (request.getRemark() != null && request.getRemark().length() > 500) return "备注不能超过500个字符";
        return null;
    }

    private void normalizeGroup(RiskGroup request) {
        request.setGroupName(request.getGroupName().trim());
        request.setGroupDesc(trimToNull(request.getGroupDesc()));
    }

    private void normalizeRule(RiskRule request) {
        if (StringUtils.hasText(request.getRuleId())) request.setRuleId(request.getRuleId().trim());
        request.setRuleName(request.getRuleName().trim());
        request.setManagerCode(trimToNull(request.getManagerCode()));
        request.setRemark(trimToNull(request.getRemark()));
    }

    private int safePageNum(Integer pageNum) { return pageNum == null || pageNum < 1 ? 1 : pageNum; }
    private int safePageSize(Integer pageSize) { return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100); }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
