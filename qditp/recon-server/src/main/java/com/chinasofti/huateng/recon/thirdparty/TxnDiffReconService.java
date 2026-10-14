package com.chinasofti.huateng.recon.thirdparty;

import com.chinasofti.huateng.recon.mapper.ReconTxnDiffMapper;
import com.chinasofti.huateng.recon.storage.ThirdPartyReconProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * ITP 侧台账 vs 渠道账单 的 FULL OUTER 比对，产出差异明细并落 RECON_TXN_DIFF。
 *
 * <p>差异方取值（recon-thirdparty.md §五 项6 默认假设）：
 * ITP_ONLY=ITP 有渠道无；THIRD_ONLY=渠道有 ITP 无；AMOUNT_MISMATCH=双方都有但金额不符；STATUS_MISMATCH=双方都有但状态不符。
 * 仅「有差异」的记录进入 TXNDIFF（符合「差异明细文件」语义）。</p>
 *
 * <p>比对口径（P1 整改，2026-10-12）：
 * <ul>
 *   <li>状态比对仅在「双方都到终态」时进行，给定显式映射：渠道 SUCCESS↔ITP SUCCESS、渠道 FAIL/CLOSED↔ITP FAIL/CLOSED；
 *       任一边处于 PROCESSING/RETRY/UNKNOWN/null 等非终态时跳过状态比对，避免误报。</li>
 *   <li>金额比对采用「双金额对」：订单金额(order_amount ↔ orderAmount) 与 实付金额(transaction_amount ↔ actualAmount) 任一不符即记 AMOUNT_MISMATCH。</li>
 * </ul>
 * ITP 侧源为 PAY_TXN_DETAIL(pay-sign) 与 F2F_*(face-pay) 的 Union，见 {@link ItpTxnExtractor} 与 §八 设计评估。</p>
 */
@Service
public class TxnDiffReconService {

    public static final String ITP_ONLY = "ITP_ONLY";
    public static final String THIRD_ONLY = "THIRD_ONLY";
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String STATUS_MISMATCH = "STATUS_MISMATCH";

    /** 终态成功。 */
    private static final String ST_SUCCESS = "SUCCESS";
    /** 终态失败。 */
    private static final String ST_FAIL = "FAIL";

    private static final Logger log = LoggerFactory.getLogger(TxnDiffReconService.class);

    private final ReconTxnDiffMapper txnDiffMapper;
    private final Map<String, String> channelMapping;

    public TxnDiffReconService(ReconTxnDiffMapper txnDiffMapper, ThirdPartyReconProperties properties) {
        this.txnDiffMapper = txnDiffMapper;
        Map<String, String> mapping = properties.getChannelMapping();
        this.channelMapping = mapping == null ? new HashMap<>() : mapping;
    }

    /**
     * @param billDate  yyyy-MM-dd 账单日（自然日）
     * @param txnDate   YYYYMMDD ITP 侧取数日（自然日，与 billDate 同一天）
     * @param bills     渠道账单
     * @param itpRecords ITP 侧台账（pay-sign + face-pay Union）
     * @return 差异记录（已落库）
     */
    public List<TxnDiffRecord> reconcile(String billDate, String txnDate,
                                        List<ThirdPartyBill> bills, List<ItpTxnRecord> itpRecords) {
        Map<String, ThirdPartyBill> channelMap = new HashMap<>();
        for (ThirdPartyBill b : bills) {
            channelMap.putIfAbsent(b.joinKey(), b);
        }
        // ITP 侧同关联键多行（重试/多次请求）取「支付成功优先」的一行；非成功则取最后一条。
        Map<String, ItpTxnRecord> itpMap = new HashMap<>();
        for (ItpTxnRecord r : itpRecords) {
            itpMap.merge(r.joinKey(), r,
                    (oldR, newR) -> ST_SUCCESS.equalsIgnoreCase(effectiveItpStatus(oldR)) ? oldR : newR);
        }

        List<TxnDiffRecord> diffs = new ArrayList<>();
        // 以渠道账单为主遍历，处理 BOTH / THIRD_ONLY
        for (ThirdPartyBill bill : bills) {
            ItpTxnRecord itp = itpMap.get(bill.joinKey());
            if (itp == null) {
                diffs.add(fromChannel(bill, billDate, THIRD_ONLY, failReasonForThirdOnly(bill)));
                continue;
            }
            String diffSide = null;
            String failReason = null;
            long channelOrderCents = yuanToCents(bill.getOrderAmount());
            long channelTxnCents = yuanToCents(bill.getTransactionAmount());
            long itpTotalCents = itp.getOrderAmount() == null ? 0L : itp.getOrderAmount();
            long itpCashCents = itp.getActualAmount() == null ? 0L : itp.getActualAmount();
            if (channelOrderCents != itpTotalCents || channelTxnCents != itpCashCents) {
                diffSide = AMOUNT_MISMATCH;
                failReason = "金额不符:订单金额 渠道=" + channelOrderCents + "分,ITP=" + itpTotalCents
                        + "分; 实付金额 渠道=" + channelTxnCents + "分,ITP=" + itpCashCents + "分";
            } else if (statusMismatch(bill.getTradeStatus(), itp)) {
                diffSide = STATUS_MISMATCH;
                failReason = "状态不符:渠道=" + nvl(bill.getTradeStatus()) + ",ITP=" + nvl(effectiveItpStatus(itp));
            }
            if (diffSide != null) {
                diffs.add(fromItp(itp, billDate, diffSide, failReason));
            }
        }
        // 遍历 ITP 侧，处理 ITP_ONLY
        for (ItpTxnRecord itp : itpRecords) {
            if (!channelMap.containsKey(itp.joinKey())) {
                diffs.add(fromItp(itp, billDate, ITP_ONLY, failReasonForItpOnly(itp)));
            }
        }

        if (!diffs.isEmpty()) {
            txnDiffMapper.deleteByBillDate(billDate);
            txnDiffMapper.insertBatch(diffs);
        }
        log.info("第三方对账比对完成 billDate={} 渠道={} ITP={} 差异={}", billDate, bills.size(), itpRecords.size(), diffs.size());
        return diffs;
    }

    /**
     * ITP 侧有效状态：直接取抽取层已归并的 {@code itpPayStatus}（SUCCESS / FAIL / null 非终态）。
     * pay-sign 流由 SQL CASE 归并 PAY_STATUS/DEBIT_REQUEST_RESULT；face-pay 流取 F2F_PAYMENT.PAY_STATUS。
     */
    private String effectiveItpStatus(ItpTxnRecord itp) {
        return itp.getItpPayStatus();
    }

    /**
     * 状态比对：仅当渠道与 ITP 双方都到达终态且结论相反（成功 vs 失败）时才判为不符。
     * 任一边处于非终态（PROCESSING/RETRY/UNKNOWN/null）即跳过，避免终态前误报。
     */
    private boolean statusMismatch(String channelTradeStatus, ItpTxnRecord itp) {
        String itpStatus = effectiveItpStatus(itp);
        if (!isTerminal(channelTradeStatus) || itpStatus == null) {
            return false;
        }
        boolean channelOk = ST_SUCCESS.equalsIgnoreCase(channelTradeStatus);
        boolean itpOk = ST_SUCCESS.equalsIgnoreCase(itpStatus);
        return channelOk != itpOk;
    }

    private boolean isTerminal(String status) {
        return ST_SUCCESS.equalsIgnoreCase(status)
                || ST_FAIL.equalsIgnoreCase(status)
                || "CLOSED".equalsIgnoreCase(status);
    }

    private String failReasonForThirdOnly(ThirdPartyBill bill) {
        if (ST_FAIL.equalsIgnoreCase(bill.getTradeStatus())) {
            return "渠道交易失败:" + nvl(bill.getTradeStatus());
        }
        if (!isTerminal(bill.getTradeStatus())) {
            return "渠道交易未到终态:" + nvl(bill.getTradeStatus());
        }
        return "ITP侧无对应记录";
    }

    private String failReasonForItpOnly(ItpTxnRecord itp) {
        String status = itp.getItpPayStatus();
        if (ST_FAIL.equalsIgnoreCase(status)) {
            return "ITP扣款失败:" + nvl(status);
        }
        if (status == null) {
            return "ITP交易未到终态:" + nvl(status);
        }
        return "渠道账单无对应记录";
    }

    /** 由 ITP 侧记录直接映射 TXNDIFF 第 1~19 段（与 DETAIL 同义字段）；缺失字段由本层兜底为空串/0。 */
    private TxnDiffRecord fromItp(ItpTxnRecord itp, String billDate, String diffSide, String failReason) {
        TxnDiffRecord r = new TxnDiffRecord();
        r.setBillDate(billDate);
        r.setTxnType(nvl(itp.getTxnType()));
        r.setLogicCardNo(nvl(itp.getLogicCardNo()));
        r.setTxnDateTime(nvl(itp.getTxnDateTime()));
        r.setOriginalFare(itp.getOriginalFare());
        r.setCurStationName(nvl(itp.getCurStationName()));
        r.setDeviceNo(nvl(itp.getDeviceNo()));
        r.setTicketType(nvl(itp.getTicketType()));
        r.setTicketSubType(nvl(itp.getTicketSubType()));
        r.setTimes(nvl(itp.getTimes()));
        r.setPayChannel(nvl(itp.getPayChannel()));
        r.setActivateTime(nvl(itp.getActivateTime()));
        r.setQuantity(itp.getQuantity() == null ? "1" : itp.getQuantity());
        r.setTicketTransSeq(nvl(itp.getTicketTransSeq()));
        r.setOrderNo(nvl(itp.getOrderNo()));
        r.setOrderAmount(itp.getOrderAmount());
        r.setActualAmount(itp.getActualAmount());
        r.setDiscountCumulativeAmount(itp.getDiscountCumulativeAmount());
        r.setOvertimeAmount(itp.getOvertimeAmount());
        r.setDiffSide(diffSide);
        r.setFailReason(failReason);
        r.setBatchNo(billDate);
        return r;
    }

    /** 渠道侧有记录、ITP 缺失时的 1~19 段映射（乘车域字段渠道账单不持有，留空或尽力填充）。 */
    private TxnDiffRecord fromChannel(ThirdPartyBill bill, String billDate, String diffSide, String failReason) {
        TxnDiffRecord r = new TxnDiffRecord();
        r.setBillDate(billDate);
        r.setTxnType(nvl(bill.getBusinessType()));
        r.setLogicCardNo("");
        r.setTxnDateTime(nvl(bill.getTradeTime()));
        r.setOriginalFare(yuanToCents(bill.getOrderAmount()));
        r.setCurStationName("");
        r.setDeviceNo(nvl(bill.getTerminalNo()));
        r.setTicketType("");
        r.setTicketSubType("");
        r.setTimes("");
        r.setPayChannel(normalizeChannel(bill.getPaymentChannel(), bill.getPaymentVendor()));
        r.setActivateTime("");
        r.setQuantity("1");
        r.setTicketTransSeq(nvl(bill.getChannelTradeNo()));
        r.setOrderNo(nvl(bill.getMerchantOrderNo()));
        r.setOrderAmount(yuanToCents(bill.getOrderAmount()));
        r.setActualAmount(yuanToCents(bill.getTransactionAmount()));
        r.setDiscountCumulativeAmount(yuanToCents(bill.getDiscountAmount()));
        r.setOvertimeAmount(null);
        r.setDiffSide(diffSide);
        r.setFailReason(failReason);
        r.setBatchNo(billDate);
        return r;
    }

    /** 元→分，四舍五入；null 按 0。 */
    private long yuanToCents(BigDecimal yuan) {
        if (yuan == null) return 0L;
        return yuan.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private String normalizeChannel(String paymentChannel, String paymentVendor) {
        String key = (paymentChannel == null ? "" : paymentChannel) + "|" + (paymentVendor == null ? "" : paymentVendor);
        String mapped = channelMapping.get(key);
        if (mapped != null) return mapped;
        String mapped2 = channelMapping.get(paymentChannel == null ? "" : paymentChannel);
        return mapped2 != null ? mapped2 : nvl(paymentChannel);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
