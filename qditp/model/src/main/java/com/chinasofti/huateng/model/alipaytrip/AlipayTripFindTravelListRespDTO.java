package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;

/**
 * 支付宝出行-查询乘车记录响应参数。
 */
public class AlipayTripFindTravelListRespDTO extends CommonResult {

    /**
     * 当前页码
     */
    private Integer pageNumber;

    /**
     * 每页大小
     */
    private Integer pageSize;

    /**
     * 总页数
     */
    private Integer totalPage;

    /**
     * 总记录数
     */
    private Integer totalCount;

    /**
     * 乘车记录列表
     */
    private List<AlipayTripTravelRecordDTO> ticketTransRecord;

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getTotalPage() {
        return totalPage;
    }

    public void setTotalPage(Integer totalPage) {
        this.totalPage = totalPage;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public List<AlipayTripTravelRecordDTO> getTicketTransRecord() {
        return ticketTransRecord;
    }

    public void setTicketTransRecord(List<AlipayTripTravelRecordDTO> ticketTransRecord) {
        this.ticketTransRecord = ticketTransRecord;
    }
}
