package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import com.chinasofti.huateng.transquery.constant.TransQueryErrorCodeEnum;
import com.chinasofti.huateng.transquery.merchant.MerchantParty;
import com.chinasofti.huateng.transquery.merchant.MerchantPartyResolver;
import com.chinasofti.huateng.transquery.station.StationNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** IF8A-34 订单详情查询。 */
@Component
public class TransDetailQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(TransDetailQueryHandler.class);

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    @Autowired
    private PaySignClient paySignClient;

    @Autowired
    private StationNameResolver stationNameResolver;

    @Autowired
    private MerchantPartyResolver merchantResolver;

    @Autowired
    private DailyTicketClient dailyTicketClient;

    /** 获取订单详情 (IF8A-34)。 */
    public RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request) {
        RequestTransDetailResult response = new RequestTransDetailResult();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())
                    || !StringUtils.hasText(request.getOrderNo())) {
                response.setRetCode(TransQueryErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("thirdUserId和orderNo不能为空");
                return response;
            }

            GateTxnPayListDTO gateRecord = gateTxnPayClient.queryByOrderNo(request.getOrderNo());
            if (gateRecord == null) {
                response.setRetCode(TransQueryErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg(TransQueryErrorCodeEnum.NO_DATA.getMsg());
                return response;
            }

            if (!StringUtils.hasText(gateRecord.getThirdUserId())
                    || !gateRecord.getThirdUserId().equals(request.getThirdUserId())) {
                response.setRetCode(TransQueryErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg("订单不存在或不属于该用户");
                return response;
            }

            TransRecordDTO record = TransRecordAssembler.assemble(
                    gateRecord, queryPayDetail(request.getOrderNo()));
            enrichSingleStationNames(record);
            resolveMerchantParties(record);
            enrichDailyTicketPayInfo(record);

            response.setRetCode(TransQueryErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TransQueryErrorCodeEnum.SUCCESS.getMsg());
            response.setTicketTransRecord(record);
        } catch (Exception e) {
            log.error("IF8A-34 获取订单详情异常, request={}", request, e);
            response.setRetCode(TransQueryErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TransQueryErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    private PayTxnDetailDTO queryPayDetail(String orderNo) {
        QueryPayTxnBatchReqDTO batchReq = new QueryPayTxnBatchReqDTO();
        batchReq.setOrderNos(Collections.singletonList(orderNo));
        RequestPayTxnBatchResult batchResult = paySignClient.queryPayTxnBatch(batchReq);
        List<PayTxnDetailDTO> payDetails = batchResult != null ? batchResult.getPayTxnDetailList() : null;
        if (CollectionUtils.isEmpty(payDetails)) {
            return null;
        }
        for (PayTxnDetailDTO detail : payDetails) {
            if (detail != null && orderNo.equals(detail.getOrderNo())) {
                return detail;
            }
        }
        return null;
    }

    /** 站名解析（详情场景，只有进出站两个编码）。 */
    private void enrichSingleStationNames(TransRecordDTO record) {
        if (StringUtils.hasText(record.getEntryStationName())) {
            Set<String> codes = new LinkedHashSet<>(Collections.singletonList(record.getEntryStationName()));
            Map<String, String> stationNameMap = stationNameResolver.resolveStationNames(codes);
            record.setEntryStationName(
                    stationNameMap.getOrDefault(record.getEntryStationName(), record.getEntryStationName()));
        }
        if (StringUtils.hasText(record.getExitStationName())) {
            Set<String> codes = new LinkedHashSet<>(Collections.singletonList(record.getExitStationName()));
            Map<String, String> stationNameMap = stationNameResolver.resolveStationNames(codes);
            record.setExitStationName(
                    stationNameMap.getOrDefault(record.getExitStationName(), record.getExitStationName()));
        }
    }

    /** 补齐商户号：仅当 {@code GATE_TXN_PAY} 未落库时才按变更日期兜底。 */
    private void resolveMerchantParties(TransRecordDTO record) {
        boolean attributableMissing = !StringUtils.hasText(record.getAttributableParty());
        boolean receivingMissing = !StringUtils.hasText(record.getReceivingParty());
        if (!attributableMissing && !receivingMissing) {
            return;
        }

        String rideDate = null;
        if (StringUtils.hasText(record.getEntryDate()) && record.getEntryDate().length() >= 8) {
            rideDate = record.getEntryDate().substring(0, 8);
        } else if (StringUtils.hasText(record.getExitDate()) && record.getExitDate().length() >= 8) {
            rideDate = record.getExitDate().substring(0, 8);
        }

        MerchantParty fallback = merchantResolver.resolveFor(rideDate);

        if (attributableMissing && StringUtils.hasText(fallback.attributableParty())) {
            record.setAttributableParty(fallback.attributableParty());
        }
        if (receivingMissing && StringUtils.hasText(fallback.receivingParty())) {
            record.setReceivingParty(fallback.receivingParty());
        }
        log.info("IF8A-34 商户号兜底, orderNo={}, rideDate={}, useOld={}, attributableParty={}, receivingParty={}",
                record.getTradeOrderNo(), rideDate, fallback.useOld(),
                record.getAttributableParty(), record.getReceivingParty());
    }

    /** 日票免扣费单补三个支付字段：{@code payTradeOrderNo} / {@code payOrderNoDate} / {@code payChannelCode}。 */
    private void enrichDailyTicketPayInfo(TransRecordDTO record) {
        if (!StringUtils.hasText(record.getTicketCode())) {
            return;
        }
        if (StringUtils.hasText(record.getPayTradeOrderNo())) {
            return;
        }
        try {
            QueryDailyTicketPayInfoReqDTO req = new QueryDailyTicketPayInfoReqDTO();
            req.setTicketCode(record.getTicketCode());
            QueryDailyTicketPayInfoResult payInfo = dailyTicketClient.queryDailyTicketPayInfo(req);
            if (payInfo == null) {
                log.warn("IF8A-34 日票支付信息为空响应, orderNo={}, ticketCode={}",
                        record.getTradeOrderNo(), record.getTicketCode());
                return;
            }
            if (StringUtils.hasText(payInfo.getPayTradeOrderNo())) {
                record.setPayTradeOrderNo(payInfo.getPayTradeOrderNo());
            }
            if (StringUtils.hasText(payInfo.getPayOrderNoDate())) {
                record.setPayOrderNoDate(payInfo.getPayOrderNoDate());
            }
            if (StringUtils.hasText(payInfo.getPayChannelCode())) {
                record.setPayChannelCode(payInfo.getPayChannelCode());
            }
            log.info("IF8A-34 日票支付信息已补齐, orderNo={}, ticketCode={}, payTradeOrderNo={}, payOrderNoDate={}, payChannelCode={}",
                    record.getTradeOrderNo(), record.getTicketCode(), record.getPayTradeOrderNo(),
                    record.getPayOrderNoDate(), record.getPayChannelCode());
        } catch (Exception e) {
            log.error("IF8A-34 日票支付信息补齐失败（详情本体不受影响）, orderNo={}, ticketCode={}",
                    record.getTradeOrderNo(), record.getTicketCode(), e);
        }
    }
}
