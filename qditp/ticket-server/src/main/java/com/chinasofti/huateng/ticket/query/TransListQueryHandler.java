package com.chinasofti.huateng.ticket.query;

import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IF8A-05 交易记录列表查询。
 *
 * <p>2026-09-14 从 {@code TransQueryHandler}（626 行）拆出：那个类把 IF8A-05 / IF8A-34 / IF8A-41
 * 三个**互不调用**的入口塞在一起，还夹带两段死代码（空方法 {@code enrichTradeOrderNos}、
 * 无调用点的 {@code mergeTransRecord}）。三者共享的只有入参归一化，已收口到
 * {@link TransQueryParamNormalizer}。</p>
 *
 * <p><b>数据源固定为 {@code GATE_TXN_PAY}（经 RPC）+ {@code PAY_TXN_DETAIL}（经 RPC）。</b>
 * 全票种（含日票）统一查 {@code GATE_TXN_PAY}，<b>NEVER 按票种分流到本地 {@code QRCODE_TXN_DETAIL}</b>：
 * 日票出站同样落 {@code GATE_TXN_PAY}（{@code GateTxnPayServiceImpl:166} 的 {@code isDailyTicket}
 * 分支照常 INSERT，只是跳过 pay-sign 并直接标 {@code DEBIT_STATUS=SUCCESS}），且该表的日票行字段更全
 * （站名已落库、有 {@code ORDER_NO} / {@code TICKET_CODE} / {@code COUNTING_*}）。</p>
 */
@Component
public class TransListQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(TransListQueryHandler.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    @Autowired
    private PaySignClient paySignClient;

    @Autowired
    private TransQueryParamNormalizer paramNormalizer;

    /**
     * 查询交易记录列表 (IF8A-05)。
     */
    public RequestTransListResult requestTransList(QueryTransListReqDTO request) {
        RequestTransListResult response = new RequestTransListResult();
        try {
            if (!StringUtils.hasText(request.getThirdUserId())) {
                return invalidParam(response, "thirdUserId不能为空");
            }

            int pageNumber = request.getPageNumber() != null && request.getPageNumber() > 0
                    ? request.getPageNumber() : 1;
            int pageSize = request.getPageSize() != null && request.getPageSize() > 0
                    ? request.getPageSize() : DEFAULT_PAGE_SIZE;
            pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
            int offset = (pageNumber - 1) * pageSize;

            String startDate = paramNormalizer.normalizeDate(request.getStartDate());
            String endDate = paramNormalizer.normalizeDate(request.getEndDate());
            if (startDate != null && endDate != null && startDate.compareTo(endDate) > 0) {
                return invalidParam(response, "开始日期不能大于结束日期");
            }
            request.setStartDate(startDate);
            request.setEndDate(endDate);

            if (StringUtils.hasText(request.getCardType())) {
                List<String> cardTypes = paramNormalizer.expandCardTypes(request.getCardType());
                if (cardTypes == null) {
                    return invalidParam(response, "卡类型非法: " + request.getCardType());
                }
                request.setCardTypeList(cardTypes);
                request.setCardType(null);
            }
            request.setCardIdList(paramNormalizer.parseCardIds(request.getCardId()));

            request.setOffset(offset);
            request.setLimit(pageSize);
            List<GateTxnPayListDTO> gateRecords = gateTxnPayClient.requestTransList(request);
            int total = gateTxnPayClient.countTransList(request);
            int totalPage = (int) Math.ceil((double) total / pageSize);

            List<TransRecordDTO> finalRecords = CollectionUtils.isEmpty(gateRecords)
                    ? Collections.emptyList()
                    : assembleRecords(gateRecords);
            fillPageSuccess(response, pageNumber, pageSize, totalPage, finalRecords);
        } catch (IllegalArgumentException e) {
            log.warn("IF8A-05 查询交易记录参数非法, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(e.getMessage());
        } catch (Exception e) {
            log.error("IF8A-05 查询交易记录异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    private RequestTransListResult invalidParam(RequestTransListResult response, String msg) {
        response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
        response.setRetMsg(msg);
        return response;
    }

    private void fillPageSuccess(RequestTransListResult response, int pageNumber, int pageSize,
                                 int totalPage, List<TransRecordDTO> records) {
        response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
        response.setPageNumber(String.valueOf(pageNumber));
        response.setPageSize(String.valueOf(pageSize));
        response.setTotalPage(String.valueOf(totalPage));
        response.setTicketTransRecord(records);
        response.setSignType("00");
        response.setSign("");
    }

    /**
     * 双源合并 → {@link TransRecordDTO}：先按 {@code orderNo} 批量拉支付明细，再逐行拼装。
     *
     * <p>字段落库时已写全（站名、商户号、计次），**此处不做二次 enrich**，NEVER 加回 ——
     * 详情侧（IF8A-34）曾按 {@code merchant-change-date} 覆盖已落库的商户号，把「进站时间错」
     * 放大成「资金归属方错」（2026-09-07 修复），列表侧一直是直取库内值、口径以本处为准。</p>
     *
     * <p>2026-09-14：删除中间模型 {@code TransListEntry} 与 {@code copyGateFields} /
     * {@code copyPayFields} 两段手工拷贝（共 65 行赋值：40 + 25），直接把两个上游 DTO 交给
     * {@link TransRecordAssembler}。上游加列时本方法零改动；<b>NEVER 再加回中间容器</b>。</p>
     */
    private List<TransRecordDTO> assembleRecords(List<GateTxnPayListDTO> gateRecords) {
        Map<String, PayTxnDetailDTO> payDetailMap = queryPayDetails(gateRecords);
        List<TransRecordDTO> records = new ArrayList<>(gateRecords.size());
        for (GateTxnPayListDTO gate : gateRecords) {
            // payDetailMap 取不到是正常情况（BOM 补站单、日票免扣费单没有 PAY_TXN_DETAIL 行），
            // assemble 内部按 pay==null 处理，NEVER 在此过滤掉这类记录。
            records.add(TransRecordAssembler.assemble(gate, payDetailMap.get(gate.getOrderNo())));
        }
        return records;
    }

    private Map<String, PayTxnDetailDTO> queryPayDetails(List<GateTxnPayListDTO> gateRecords) {
        List<String> orderNos = gateRecords.stream()
                .map(GateTxnPayListDTO::getOrderNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(orderNos)) {
            return Collections.emptyMap();
        }
        QueryPayTxnBatchReqDTO batchReq = new QueryPayTxnBatchReqDTO();
        batchReq.setOrderNos(orderNos);
        RequestPayTxnBatchResult batchResult = paySignClient.queryPayTxnBatch(batchReq);
        List<PayTxnDetailDTO> payDetails = batchResult != null ? batchResult.getPayTxnDetailList() : null;
        if (CollectionUtils.isEmpty(payDetails)) {
            return Collections.emptyMap();
        }
        return payDetails.stream()
                .collect(Collectors.toMap(PayTxnDetailDTO::getOrderNo, Function.identity(), (a, b) -> a));
    }
}
