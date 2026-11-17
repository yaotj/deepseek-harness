package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;


public class QueryUserItineraryResult extends CommonResult {
    private MemberItineraryDTO memberItinerary;

    public MemberItineraryDTO getMemberItinerary() {
        return memberItinerary;
    }

    public void setMemberItinerary(MemberItineraryDTO memberItinerary) {
        this.memberItinerary = memberItinerary;
    }
}
