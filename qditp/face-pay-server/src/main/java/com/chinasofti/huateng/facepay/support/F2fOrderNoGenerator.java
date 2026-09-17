package com.chinasofti.huateng.facepay.support;

import com.chinasofti.huateng.facepay.mapper.F2fSequenceMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 订单号生成入口。 */
@Component
public class F2fOrderNoGenerator {

    private final F2fSequenceMapper sequenceMapper;

    public F2fOrderNoGenerator(F2fSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    /** 单程票订单号。 */
    public String nextSingleTicketOrderNo() {
        return next(F2fOrderNo.BIZ_SINGLE_TICKET);
    }

    /** 按业务码取订单号。 */
    public String next(String bizCode) {
        Long seq = sequenceMapper.nextOrderNoSeq();
        if (seq == null) {
            throw new IllegalStateException("F2F_ORDER_NO_SEQ 取号返回 null，序列可能不存在");
        }
        return F2fOrderNo.format(bizCode, LocalDateTime.now(), seq);
    }
}
