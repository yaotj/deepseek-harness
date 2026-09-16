package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;

/**
 * 将 GATE_TXN_PAY + PAY_TXN_DETAIL 双源数据组装为 APP 应答 {@link TransRecordDTO}。
 *
 * <p>组装规则：
 * <ul>
 *   <li>基础信息（卡号、设备、流水号、金额、商户号等）取 GATE_TXN_PAY。</li>
 *   <li>扣款结果、支付状态、支付时间等支付细节以 PAY_TXN_DETAIL 为准。</li>
 *   <li>进出站：GateTxnPayListDTO 一条记录已包含完整进出站信息，直接映射，不再按 TRX_TYPE 区分。</li>
 * </ul>
 *
 * <p><b>2026-09-14：删除了中间模型 {@code TransListEntry}（213 行 / 65 个字段与 65 组读写方法，
 * 与 ticket-server 那份除包名外逐字节相同），本方法改为直接吃两个上游 DTO。</b>
 * 那个类的存在理由是「承接 mapper ResultSet」，而两个模块的列表侧数据都来自 RPC、
 * 从来没有 mapper —— 它只是把两个 DTO 的字段手工抄了一遍（40 + 25 行赋值），
 * 上游加一列就要两个模块各改四处（field / getter / setter / 拷贝行），漏一处即静默 null。
 * <b>NEVER 再引入这类「与上游 DTO 1:1 同形」的中间容器</b>；真需要区分来源时，
 * 来源标识就是参数名本身（{@code gate} / {@code pay}），由编译器保证。</p>
 */
public class TransRecordAssembler {

    /**
     * @param gate GATE_TXN_PAY 行，null 时整条记录无意义、直接返 null
     * @param pay  PAY_TXN_DETAIL 行，<b>可以为 null</b>：BOM 补站单与日票免扣费单没有支付明细行，
     *             此时所有 pay 侧字段保持「未取到」的语义（String 空串、Integer null），
     *             与删除 {@code TransListEntry} 之前 {@code copyPayFields} 直接 return 的行为一致
     */
    public static TransRecordDTO assemble(GateTxnPayListDTO gate, PayTxnDetailDTO pay) {
        if (gate == null) {
            return null;
        }
        TransRecordDTO dto = new TransRecordDTO();
        // 基础信息
        dto.setCardNum(nullSafe(gate.getCardId()));
        // 金额单位分，原样输出。2026-09-07 生产实测：库内 TRX_AMOUNT=200（2 元），
        // 此处原先除 100 转成 "2.00"，APP 再按分除一次 100，最终展示 0.02。
        // APP 侧按分解析（与 IF8A-34 详情、同 DTO 其余金额字段一致），NEVER 在此转元。
        // 取 TOTAL_AMOUNT（车费 + 超时费）而非 TRX_AMOUNT：TOTAL_AMOUNT 才是实际请求扣款的
        // 金额（GateTxnPayServiceImpl.requestPaySign 传的就是它），也与 IF8A-34 详情口径一致。
        dto.setPayAmount(toFen(gate.getTotalAmount()));
        dto.setOrderExpType(nullSafe(gate.getOrderExpType()));
        dto.setTradeOrderNo(nullSafe(gate.getOrderNo()));
        // payTradeOrderNo 是「渠道订单号」——支付渠道方生成的号，支付宝就是支付宝交易号
        // （PAY_TXN_DETAIL.CHANNEL_ORDER_NO，如 2026082623001467281456586133）。
        // NEVER 用 MERCHANT_ORDER_NO：支付回调把它回写成了我方订单号（receivePayResult 里
        // update.setMerchantOrderNo(request.getMerchantOrderNo())，而回调的 merchantOrderNo
        // 就是我方 orderNo），该列与 ORDER_NO 完全重复，APP 上「渠道订单号」显示成 GT 开头的
        // 自家订单号就是这么来的（2026-08-27 实测）。
        dto.setPayTradeOrderNo(nullSafe(pay != null ? pay.getChannelOrderNo() : null));
        // payOrderNoDate 是支付时间（PAY_TXN_DETAIL.PAY_TIME，yyyyMMddHHmmss），
        // 不是 GATE_TXN_PAY.TXN_DATE（只有 yyyyMMdd）。2026-08-27 与业务确认：
        // 规格没有独立 payTime 字段，APP 用本字段展示支付时间。NEVER 改回 TXN_DATE。
        dto.setPayOrderNoDate(nullSafe(pay != null ? pay.getPayTime() : null));
        // 商户号
        dto.setAttributableParty(nullSafe(gate.getAttributableParty()));
        dto.setReceivingParty(nullSafe(gate.getReceivingParty()));
        // 支付信息（来自 PAY_TXN_DETAIL）
        // GateTxnPayListDTO 也有一个同名的 paymentVendor 字段，但本字段一直只取支付明细侧，
        // NEVER 改成 gate.getPaymentVendor()——那是行为变更，闸机侧那一列在多数行为空。
        dto.setPayChannelCode(nullSafe(pay != null ? pay.getPaymentVendor() : null));
        dto.setDebitRequestResult(toAppDebitResult(gate.getDebitStatus(),
                pay != null ? pay.getDebitRequestResult() : null));
        dto.setDiscountFee(pay != null ? pay.getDiscountFee() : null);
        dto.setDiscountInfo(pay != null ? pay.getDiscountInfo() : null);
        dto.setTransferFlag(gate.getTransferFlag());
        dto.setCumulativeType(gate.getCumulativeType());
        dto.setOriginalFare(gate.getOriginalFare());
        // totalAmount 是 APP 侧要的原价字段名，值与 originalFare 同源（分）。
        // NEVER 改成 gate.getTotalAmount()——那是实付（车费 + 超时费），已经在 payAmount 里了。
        dto.setTotalAmount(gate.getOriginalFare());
        dto.setWalletTotalAmt(gate.getWalletTotalAmt());
        dto.setDiscountLevelAmt(gate.getDiscountLevelAmt());
        dto.setDiscountRate(gate.getDiscountRate());
        dto.setExpectedGateAmount(gate.getExpectedGateAmount());
        // 进出站信息（GateTxnPayListDTO 一条记录已包含完整信息）
        dto.setEntryStationName(nullSafe(gate.getEntryStationName() != null ? gate.getEntryStationName() : gate.getInStation()));
        dto.setEntryDate(nullSafe(gate.getInTime()));
        dto.setExitStationName(nullSafe(gate.getExitStationName() != null ? gate.getExitStationName() : gate.getOutStation()));
        dto.setExitDate(nullSafe(gate.getOutTime()));
        // 票卡信息
        dto.setCompanionFlag(nullSafe(gate.getCompanionFlag()));
        dto.setTicketCode(nullSafe(gate.getTicketCode()));
        dto.setCountingTimes(gate.getCountingTimes());
        dto.setCountingFlag(nullSafe(gate.getCountingFlag()));
        dto.setOfflineFlag(nullSafe(gate.getOfflineFlag()));
        return dto;
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    /**
     * 把库内扣款结果转成 APP 侧值域。
     *
     * <p><b>以 {@code GATE_TXN_PAY.DEBIT_STATUS} 为准，{@code PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT}
     * 只在前者为空时兜底</b>（2026-09-10 修复）。理由是 IF8A-05 的筛选条件
     * {@code Debit_Result_Filter} 走的就是 {@code GATE_TXN_PAY.DEBIT_STATUS}
     * （{@code GateTxnPayMapper.xml} 的 {@code Debit_Result_Filter}），
     * 展示侧若改读另一张表，就会出现「按已扣费筛出来的记录逐条显示未支付」。
     * <b>NEVER 让筛选与展示取不同表</b>。</p>
     *
     * <p>已发生（2026-09-10 生产实测，卡 {@code 0178885088135717} 9 月 7 单）：
     * 4 单 {@code GATE_TXN_PAY.DEBIT_STATUS='SUCCESS'} 而 {@code PAY_TXN_DETAIL} 侧是
     * {@code FAIL}（其中 BOM 补站单 {@code GT20260910172129730135717} 连 {@code PAY_TXN_DETAIL}
     * 行都没有），于是 APP 上 7 单**全部**渲染成未支付。</p>
     *
     * <p>值域依据接口规范 R6 表13 IF8A-05 请求参数「扣款结果 空查全部，0查成功，1查失败」——
     * 规范只在请求侧给出了 0/1，响应侧那一栏是空的，两侧同名字段按同一套编码是唯一自洽解释。
     * 直接把 SUCCESS 透传给 APP 会落到 APP 的值域之外，扣款成功的订单会被渲染成未支付
     * （2026-08-26 生产实测：APP 送 debitRequestResult=1 查失败，后端却返回了
     * debitRequestResult=SUCCESS 的记录）。</p>
     *
     * <p>只有 SUCCESS 映射为 0，PROCESSING / FAIL / null 一律映射为 1。规范没有「处理中」这一档，
     * NEVER 反过来把未知值当成功——那等于告诉乘客钱已经扣了。</p>
     *
     * @param gateDebitStatus       {@code GATE_TXN_PAY.DEBIT_STATUS}，权威扣费状态
     * @param payDebitRequestResult {@code PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT}，仅兜底
     */
    static String toAppDebitResult(String gateDebitStatus, String payDebitRequestResult) {
        String authoritative = gateDebitStatus != null && !gateDebitStatus.isBlank()
                ? gateDebitStatus : payDebitRequestResult;
        return "SUCCESS".equals(authoritative) ? "0" : "1";
    }

    private static String toFen(Integer fen) {
        if (fen == null) {
            return null;
        }
        return String.valueOf(fen);
    }
}
