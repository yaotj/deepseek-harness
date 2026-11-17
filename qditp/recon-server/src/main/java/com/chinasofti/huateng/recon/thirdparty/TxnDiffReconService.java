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


@Service
public class TxnDiffReconService {

    public static final String ITP_ONLY = "ITP_ONLY";
    public static final String THIRD_ONLY = "THIRD_ONLY";
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String STATUS_MISMATCH = "STATUS_MISMATCH";

    private static final String ST_SUCCESS = "SUCCESS";
    private static final String ST_FAIL = "FAIL";

    private static final Logger log = LoggerFactory.getLogger(TxnDiffReconService.class);

    private final ReconTxnDiffMapper txnDiffMapper;
    private final Map<String, String> channelMapping;

    public TxnDiffReconService(ReconTxnDiffMapper txnDiffMapper, ThirdPartyReconProperties properties) {
        this.txnDiffMapper = txnDiffMapper;
        Map<String, String> mapping = properties.getChannelMapping();
        this.channelMapping = mapping == null ? new HashMap<>() : mapping;
    }


    public List<TxnDiffRecord> reconcile(String billDate, String txnDate,
                                        List<ThirdPartyBill> bills, List<ItpTxnRecord> itpRecords) {
        Map<String, ThirdPartyBill> channelMap = new HashMap<>();
        for (ThirdPartyBill b : bills) {
            channelMap.putIfAbsent(b.joinKey(), b);
        }
        Map<String, ItpTxnRecord> itpMap = new HashMap<>();
        for (ItpTxnRecord r : itpRecords) {
            itpMap.merge(r.joinKey(), r,
                    (oldR, newR) -> ST_SUCCESS.equalsIgnoreCase(effectiveItpStatus(oldR)) ? oldR : newR);
        }

        List<TxnDiffRecord> diffs = new ArrayList<>();
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


    private String effectiveItpStatus(ItpTxnRecord itp) {
        return itp.getItpPayStatus();
    }


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
