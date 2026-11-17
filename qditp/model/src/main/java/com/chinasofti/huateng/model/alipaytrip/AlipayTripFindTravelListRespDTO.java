package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.List;


public class AlipayTripFindTravelListRespDTO extends CommonResult {


    private Integer pageNumber;


    private Integer pageSize;


    private Integer totalPage;


    private Integer totalCount;


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
