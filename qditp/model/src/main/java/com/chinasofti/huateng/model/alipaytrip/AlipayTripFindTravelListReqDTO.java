package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;

/**
 * 支付宝出行-查询乘车记录请求参数。
 */
public class AlipayTripFindTravelListReqDTO {

    /**
     * 第三方用户ID，格式化后的用户标识。
     */
    private String thirdUserId;

    /**
     * 页码，从0开始。
     */
    private String page;

    /**
     * 每页大小。
     */
    private String size;

    /**
     * 扣款请求结果筛选（可选）
     */
    private String debitRequestResult;

    /**
     * 发票状态（可选）。
     *
     * <p><b>按用户裁决刻意不参与筛选</b>（2026-09-18 定、2026-09-20 重申，见
     * {@code docs/business/alipay-channel.md} 与 ADR-D146 一带）：本字段在
     * {@code AlipayTravelQueryHandler.findTravelList} 里<b>零引用</b>，传 {@code 0} / {@code 1} / 不传
     * 三种返回逐字一致 —— 这是预期行为，<b>NEVER 当成漏实现去补过滤谓词</b>。
     * 发票语义只体现在响应侧：{@code AlipayTripTravelRecordDTO.invoice} 原样透出
     * {@code ALIPAY_PAY_TXN_DETAIL.INVOICE}（该列全库尚无写入方，故实际恒为空）。
     * 本字段属对外契约，<b>NEVER 因为无人引用就删掉</b>。
     */
    private String invoice;

    /**
     * 开始日期（可选）
     */
    private String startDate;

    /**
     * 结束日期（可选）
     */
    private String endDate;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getDebitRequestResult() {
        return debitRequestResult;
    }

    public void setDebitRequestResult(String debitRequestResult) {
        this.debitRequestResult = debitRequestResult;
    }

    public String getInvoice() {
        return invoice;
    }

    public void setInvoice(String invoice) {
        this.invoice = invoice;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}
