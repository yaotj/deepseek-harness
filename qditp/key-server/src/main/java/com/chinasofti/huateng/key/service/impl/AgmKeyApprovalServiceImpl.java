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
