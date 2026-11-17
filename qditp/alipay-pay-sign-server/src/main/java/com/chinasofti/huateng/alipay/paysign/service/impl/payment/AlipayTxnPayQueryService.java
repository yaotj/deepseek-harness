package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.domain.PayCenterTradeStatus;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayTxnBriefDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
public class AlipayTxnPayQueryService {

    private static final Logger log = LoggerFactory.getLogger(AlipayTxnPayQueryService.class);

    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";

    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    private static final String PAY_STATUS_FAIL = "FAIL";

    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;
    private final PayCenterPort payCenterPort;

    public AlipayTxnPayQueryService(AlipayPayTxnDetailMapper alipayPayTxnDetailMapper,
                                    PayCenterPort payCenterPort) {
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
        this.payCenterPort = payCenterPort;
    }

    public AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        String orderNo = request.getOrderNo();
        log.info("收到支付结果查询（新链路）: orderNo={}", orderNo);

        AlipayPayTxnDetail detail = alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
        if (detail == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "支付明细不存在");
        }

        Map<String, Object> bizData = buildBizData(request, detail);
        log.info("支付宝出行-支付结果查询,调用支付中心,orderNo={}, 请求参数={}", orderNo, JSON.toJSONString(bizData));

        PayCenterReply reply;
        try {
            reply = payCenterPort.payQuery(bizData);
        } catch (RuntimeException e) {
            log.error("支付宝出行-支付结果查询调用支付中心抛异常，本地支付明细保持原状、NEVER 落 FAIL, orderNo={}", orderNo, e);
            return response(alipayPayTxnDetailMapper.selectByOrderNo(orderNo), null, "查询支付中心失败");
        }

        String centerPayDate = applyReply(reply, orderNo);
        return response(alipayPayTxnDetailMapper.selectByOrderNo(orderNo), centerPayDate, null);
    }


    public List<AlipayTripPayTxnBriefDTO> queryPayTxnBrief(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            log.info("支付宝出行-批量补齐支付明细,入参为空,直接返回空列表");
            return List.of();
        }
        List<AlipayPayTxnDetail> details = alipayPayTxnDetailMapper.selectByOrderNos(orderNos);
        if (details == null || details.isEmpty()) {
            log.info("支付宝出行-批量补齐支付明细,未命中任何支付明细, 请求条数={}", orderNos.size());
            return List.of();
        }
        List<AlipayTripPayTxnBriefDTO> result = new ArrayList<>(details.size());
        for (AlipayPayTxnDetail detail : details) {
            AlipayTripPayTxnBriefDTO brief = new AlipayTripPayTxnBriefDTO();
            brief.setOrderNo(detail.getOrderNo());
            brief.setPayStatus(detail.getPayStatus());
            brief.setChannelOrderNo(detail.getChannelOrderNo());
            brief.setTransTime(detail.getTransTime());
            brief.setInvoice(detail.getInvoice());
            result.add(brief);
        }
        log.info("支付宝出行-批量补齐支付明细完成, 请求条数={}, 命中条数={}", orderNos.size(), result.size());
        return result;
    }


    private String applyReply(PayCenterReply reply, String orderNo) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                String tradeStatus = firstNonBlank(accepted.field("status"), accepted.field("tradeStatus"));
                String channelOrderNo = firstNonBlank(accepted.field("channelOrderNo"), accepted.field("tradeNo"));
                String payDate = firstNonBlank(accepted.field("payDate"), accepted.field("paymentTime"));
                log.info("支付宝出行-支付结果查询,支付中心业务应答: orderNo={}, retCode={}, retMsg={}, tradeStatus={}, channelOrderNo={}, payDate={}, totalAmount={}",
                        orderNo, accepted.retCode(), accepted.retMsg(), tradeStatus, channelOrderNo, payDate,
                        accepted.field("totalAmount"));

                if (!PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    log.error("支付宝出行-支付结果查询被支付中心拒绝，本地支付明细保持原状、NEVER 落 FAIL, orderNo={}, retCode={}, retMsg={}",
                            orderNo, accepted.retCode(), accepted.retMsg());
                    return payDate;
                }
                String payStatus = resolveTradeStatus(tradeStatus);
                if (payStatus == null) {
                    log.error("支付宝出行-支付结果查询拿到的交易状态无法判定，拒绝回写、MUST 人工到支付中心核对, orderNo={}, tradeStatus={}",
                            orderNo, tradeStatus);
                    return payDate;
                }
                applyQueryResult(orderNo, payStatus, channelOrderNo, accepted.field("payCenterOrderNo"));
                return payDate;
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("支付宝出行-支付结果查询未拿到业务应答，本地支付明细保持原状、NEVER 落 FAIL, orderNo={}, code={}, msg={}, success={}",
                        orderNo, rejected.code(), rejected.msg(), rejected.success());
                return null;
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                log.error("支付宝出行-支付结果查询无响应，本地支付明细保持原状、NEVER 落 FAIL, orderNo={}", orderNo);
                return null;
            }
        }
    }


    private String resolveTradeStatus(String tradeStatus) {
        return PayCenterTradeStatus.normalize(tradeStatus);
    }


    private void applyQueryResult(String orderNo, String payStatus, String channelOrderNo, String payCenterOrderNo) {
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setChannelOrderNo(channelOrderNo);
        update.setPayCenterOrderNo(payCenterOrderNo);
        int affected = alipayPayTxnDetailMapper.updatePayQueryResultIfNotSuccess(update);
        if (affected > 0) {
            log.info("支付宝出行-支付结果查询,本地支付明细已回写, orderNo={}, payStatus={}, channelOrderNo={}",
                    orderNo, payStatus, channelOrderNo);
            return;
        }
        AlipayPayTxnDetail current = alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
        String currentStatus = current == null ? null : current.getPayStatus();
        if (payStatus.equals(currentStatus)) {
            log.info("支付宝出行-支付结果查询,本地已是目标状态，跳过回写, orderNo={}, payStatus={}", orderNo, currentStatus);
            return;
        }
        log.error("支付宝出行-支付结果查询与本地口径冲突，本地为 SUCCESS 终态、拒绝覆盖，MUST 人工到支付中心核对, orderNo={}, 本地PAY_STATUS={}, 支付中心给出={}",
                orderNo, currentStatus, payStatus);
    }


    private Map<String, Object> buildBizData(AlipayTripPayQueryReqDTO request, AlipayPayTxnDetail detail) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", request.getOrderNo());
        bizData.put("cardIssueCode", StringUtils.hasText(request.getCardIssueCode())
                ? request.getCardIssueCode() : CARD_ISSUE_CODE_ALIPAY);
        if (StringUtils.hasText(request.getCardNum())) {
            bizData.put("cardNum", request.getCardNum());
        }
        String channelAgreementNo = firstNonBlank(request.getChannelAgreementNo(), detail.getChannelAgreementNo());
        if (StringUtils.hasText(channelAgreementNo)) {
            bizData.put("channelAgreementNo", channelAgreementNo);
        }
        return bizData;
    }


    private AlipayTripPayQueryRespDTO response(AlipayPayTxnDetail detail, String centerPayDate, String tradeDesc) {
        AlipayTripPayQueryRespDTO response = new AlipayTripPayQueryRespDTO();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("查询成功");
        if (detail != null) {
            response.setOutTradeNo(detail.getOrderNo());
            response.setTradeStatus(detail.getPayStatus());
            response.setTotalAmount(detail.getAmount() == null ? null : String.valueOf(detail.getAmount()));
            response.setTradeNo(detail.getChannelOrderNo());
        }
        response.setPaymentTime(centerPayDate);
        response.setTradeDesc(StringUtils.hasText(tradeDesc) ? tradeDesc : response.getTradeStatus());
        log.info("支付宝出行-支付结果查询完成（新链路）: {}", JSON.toJSONString(response));
        return response;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
