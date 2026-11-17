package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;


sealed interface SignOutcome {


    record AlreadySigned(AlipaySignInfo existing) implements SignOutcome {
    }

    record Reactivated(AlipaySignInfo signInfo) implements SignOutcome {
    }

    record Created(AlipaySignInfo signInfo) implements SignOutcome {
    }
}
