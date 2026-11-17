package com.chinasofti.huateng.alipay.paysign.model.response;

import java.io.Serializable;
import java.util.List;


public class PageResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<T> list;

    private long total;

    private int pageNum;

    private int pageSize;

    private long totalPage;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPage = pageSize > 0 ? (long) Math.ceil((double) total / pageSize) : 0;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalPage() {
        return totalPage;
    }

    public void setTotalPage(long totalPage) {
        this.totalPage = totalPage;
    }
}
