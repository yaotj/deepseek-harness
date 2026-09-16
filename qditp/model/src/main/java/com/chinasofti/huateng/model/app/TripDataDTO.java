package com.chinasofti.huateng.model.app;

/**
 * IF8A-41 查询账单统计应答中的统计数据对象。
 *
 * <p><b>四个金额的口径（2026-09-10 校准，统计源表已从 {@code QRCODE_TXN_DETAIL} 换到
 * {@code GATE_TXN_PAY}）</b>：
 * <pre>
 * totalPrice     地铁原价合计   SUM(NVL(ORIGINAL_FARE, TRX_AMOUNT))
 * totalDebit     实付合计       SUM(TOTAL_AMOUNT)  = 票价 + 超时费
 * totalDiscount  优惠合计       SUM(MAX(原价 - TRX_AMOUNT, 0))
 * totalOvertime  超时费合计     SUM(OVERTIME_AMOUNT)
 * </pre>
 * <p><b>NEVER 把 {@code OVERTIME_AMOUNT} 映射到 {@code totalDiscount}</b>：超时加收与优惠语义相反，
 * 旧实现（{@code QRCodeTxnDetailMapper.selectTransStatistics}）三个标签全部错位——把 12 元超时费
 * 显示成「已优惠 12 元」、把实付说成原价、把纯票价说成实付（少报超时费）。
 * <p>{@code totalDiscount} 的减数 <b>MUST 是 {@code TRX_AMOUNT} 而非 {@code TOTAL_AMOUNT}</b>：
 * 后者含超时费，会让加收抵掉优惠并算出负数。
 */
public class TripDataDTO {
    /** 总金额（元）：地铁原价合计，原价缺失时退化为票价 */
    private String totalPrice;
    /** 总支付（元）：实付合计，含超时加收 */
    private String totalDebit;
    /** 总优惠（元）：原价 - 票价，逐笔下取 0，NEVER 放超时费 */
    private String totalDiscount;
    /** 总超时费（元）：超时加收合计，与优惠是相反方向的量 */
    private String totalOvertime;
    /** 订单数量 */
    private Integer count;

    public String getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(String totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getTotalDebit() {
        return totalDebit;
    }

    public void setTotalDebit(String totalDebit) {
        this.totalDebit = totalDebit;
    }

    public String getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(String totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getTotalOvertime() {
        return totalOvertime;
    }

    public void setTotalOvertime(String totalOvertime) {
        this.totalOvertime = totalOvertime;
    }

    @Override
    public String toString() {
        return "TripDataDTO{totalPrice='" + totalPrice + "', totalDebit='" + totalDebit
                + "', totalDiscount='" + totalDiscount + "', totalOvertime='" + totalOvertime
                + "', count=" + count + "}";
    }
}
