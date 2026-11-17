package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;


public class AlipayTripFindTravelDetailRespDTO extends CommonResult {


    private AlipayTripTravelDetailDTO data;

    public AlipayTripTravelDetailDTO getData() {
        return data;
    }

    public void setData(AlipayTripTravelDetailDTO data) {
        this.data = data;
    }
}
