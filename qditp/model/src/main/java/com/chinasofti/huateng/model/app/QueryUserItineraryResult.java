package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * IF8A-29 查询用户上次行程响应参数。
 */
public class QueryUserItineraryResult extends CommonResult {
    private MemberItineraryDTO memberItinerary;

    public MemberItineraryDTO getMemberItinerary() {
        return memberItinerary;
    }

    public void setMemberItinerary(MemberItineraryDTO memberItinerary) {
        this.memberItinerary = memberItinerary;
    }
}
