package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipayTerminationRequestMapper;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationInternalService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 支付宝出行销卡批处理。
 *
 * <p><b>本方法 NEVER 加 {@code @Transactional}</b>：逐条执行时会调支付中心（HTTP），
 * 事务包住等于按对端响应时长持有行锁（AGENTS.md §5.2 已有生产事故）。每条记录各自
 * 独立收口，一条失败不影响其余。</p>
 *
 * <p><b>当前未做欠费校验</b>：2026-09-07 实测 {@code GATE_TXN_PAY} 与 {@code ALIPAY_PAY_LOG}
 * 订单号交集为 0、{@code THIRD_USER_ID} 体系与 {@code PAYMENT_VENDOR} 均不同，
 * 支付宝出行的「未结清」语义在现有数据里找不到落点。因此这一步留成显式扩展点，
 * **NEVER** 补一个恒返回「无欠费」的假校验冒充已校验。</p>
 */
@Service
public class AlipayTerminationInternalServiceImpl implements AlipayTerminationInternalService {

    private static final Logger log = LoggerFactory.getLogger(AlipayTerminationInternalServiceImpl.class);

    private static final String STATUS_PENDING = "PENDING";

    /** 单批上限。排空靠调用方多轮调用，不在这里循环。 */
    private static final int BATCH_SIZE = 200;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private AlipayTerminationRequestMapper alipayTerminationRequestMapper;

    @Autowired
    private TerminationNotifier terminationNotifier;

    @Override
    public AlipayProcessTerminationRespDTO processTermination(AlipayProcessTerminationReqDTO request) {
        AlipayProcessTerminationRespDTO response = new AlipayProcessTerminationRespDTO();
        String referenceTime = request == null ? null : request.getReferenceTime();

        LocalDateTime cutoff = null;
        if (referenceTime != null && !referenceTime.trim().isEmpty()) {
            cutoff = parseReferenceTime(referenceTime.trim());
            if (cutoff == null) {
                log.warn("销卡批处理入参解析失败, referenceTime={}", referenceTime);
                response.setResultCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setResultMsg("referenceTime 格式应为 yyyyMMdd 或 yyyyMMddHHmmss");
                return response;
            }
        }

        List<AlipayTerminationRequest> records;
        try {
            records = cutoff == null
                    ? alipayTerminationRequestMapper.selectByStatusLimit(STATUS_PENDING, BATCH_SIZE)
                    : alipayTerminationRequestMapper.selectByStatusBefore(STATUS_PENDING, cutoff, BATCH_SIZE);
        } catch (Exception e) {
            log.error("销卡批处理查询待处理登记失败, referenceTime={}", referenceTime, e);
            response.setResultCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setResultMsg(FepAppErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }

        int scanned = records == null ? 0 : records.size();
        int terminated = 0;
        int failed = 0;
        int skipped = 0;
        log.info("销卡批处理开始, referenceTime={}, cutoff={}, scanned={}", referenceTime, cutoff, scanned);

        for (int i = 0; i < scanned; i++) {
            AlipayTerminationRequest record = records.get(i);
            String thirdUserId = record.getThirdUserId();
            if (thirdUserId == null || thirdUserId.trim().isEmpty()) {
                // 历史脏数据：登记时未回填 thirdUserId，做不了用户维度校验，跳过并留日志等人工处理。
                log.error("销卡登记缺少 thirdUserId，跳过, agreementCode={}, terminationSeq={}",
                        record.getAgreementCode(), record.getTerminationSeq());
                skipped++;
                continue;
            }
            TerminationNotifier.Outcome outcome = terminationNotifier.execute(record);
            switch (outcome) {
                case TERMINATED -> terminated++;
                case FAILED -> failed++;
                default -> skipped++;
            }
        }

        response.setResultCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(FepAppErrorCodeEnum.SUCCESS.getMsg());
        response.setScanned(scanned);
        response.setTerminated(terminated);
        response.setFailed(failed);
        response.setSkipped(skipped);
        log.info("销卡批处理完成, referenceTime={}, scanned={}, terminated={}, failed={}, skipped={}",
                referenceTime, scanned, terminated, failed, skipped);
        return response;
    }

    /**
     * 解析基准时间。
     *
     * @return 解析失败返回 null，由调用方转成 INVALID_PARAM，**NEVER** 静默退化成全表扫描
     */
    private LocalDateTime parseReferenceTime(String referenceTime) {
        try {
            if (referenceTime.length() == 8) {
                return LocalDate.parse(referenceTime, DATE_FORMATTER).atTime(LocalTime.MAX);
            }
            if (referenceTime.length() == 14) {
                return LocalDateTime.parse(referenceTime, DATE_TIME_FORMATTER);
            }
            return null;
        } catch (Exception e) {
            log.warn("销卡批处理 referenceTime 解析异常, referenceTime={}", referenceTime, e);
            return null;
        }
    }
}
