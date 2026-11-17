package com.chinasofti.huateng.acc.es.server.model;

import lombok.Data;

@Data
public class PageInfo {

    private int pageSize;

    private int pageNum;

    private String sortBy;

    private String order;
}
