package com.chinasofti.huateng.key.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.constant.AgmKeyApprovalStatus;
import com.chinasofti.huateng.key.mapper.MetroAgmKeyVersionMapper;
import com.chinasofti.huateng.key.service.AgmKeyApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AGM 密钥版本审批实现：白名单前置快速失败 + 数据库 CAS 兜并发。
 *
 * <p>只改审批状态元信息（PUBLIC_KEY_VERSION_STATUS / MANAGER_ID / UPDATE_DATE），
 * <b>不生成、不返回、不触碰任何密钥材料</b>。安全红线动作，上线前 MUST 经人工安全合规复核。</p>
 */
@Service
public class AgmKeyApprovalServiceImpl implements AgmKeyApprovalService {
    private static final Logger log = LoggerFactory.getLogger(AgmKeyApprovalServiceImpl.class);

    private final MetroAgmKeyVersionMapper agmKeyVersionMapper;

    public AgmKeyApprovalServiceImpl(MetroAgmKeyVersionMapper agmKeyVersionMapper) {
        this.agmKeyVersionMapper = agmKeyVersionMapper;
    }

    @Override
    public ResultVO<Void> approve(String providerId, Long batchNumber, String targetStatus, String operator) {
        if (!StringUtils.hasText(providerId) || batchNumber == null) {
            return ResultMapper.illegalParams("接入方与密钥批次号均必填");
        }
        AgmKeyApprovalStatus target = AgmKeyApprovalStatus.parseByCode(targetStatus);
        if (target == null || !target.isTerminal()) {
            // 目标必须落到终态（20020 审批通过 / 20030 拒绝）；INIT 不是合法审批目标。
            return ResultMapper.illegalParams("目标状态只能是 20020(审批通过) 或 20030(拒绝)");
        }

        String currentRaw = agmKeyVersionMapper.selectApprovalStatus(providerId.trim(), batchNumber);
        if (currentRaw == null) {
            return ResultMapper.error("未找到该接入方该批次密钥版本记录");
        }
        AgmKeyApprovalStatus current = AgmKeyApprovalStatus.parseByCode(currentRaw);
        if (current == null) {
            return ResultMapper.error("库内审批状态值无法识别: " + currentRaw + "，请人工核对");
        }
        if (!current.canTransitTo(target)) {
            return ResultMapper.error("非法迁移：当前 " + currentRaw + "(" + current + ") 不能迁到 "
                    + target.getCode() + "(" + target + ")");
        }

        int affected = agmKeyVersionMapper.transitionApprovalStatus(
                providerId.trim(), batchNumber, current.getCode(), target.getCode(),
                StringUtils.hasText(operator) ? operator.trim() : "admin");
        if (affected == 0) {
            // CAS 0 行：并发已改。回查后区分「已被改成目标」(幂等成功) 与「被改成别值」(冲突)。
            String now = agmKeyVersionMapper.selectApprovalStatus(providerId.trim(), batchNumber);
            if (target.getCode().equals(now)) {
                log.info("AGM 密钥版本审批幂等命中: provider={}, batch={}, now={}", providerId, batchNumber, now);
                return ResultMapper.ok();
            }
            log.warn("AGM 密钥版本审批 CAS 冲突: provider={}, batch={}, 期望从 {} 迁到 {}, 现={}",
                    providerId, batchNumber, current.getCode(), target.getCode(), now);
            return ResultMapper.error("审批状态已被并发修改（现 " + now + "），请刷新后重试");
        }
        log.info("AGM 密钥版本审批成功: provider={}, batch={}, {} -> {}, operator={}",
                providerId, batchNumber, current.getCode(), target.getCode(), operator);
        return ResultMapper.ok();
    }
}
