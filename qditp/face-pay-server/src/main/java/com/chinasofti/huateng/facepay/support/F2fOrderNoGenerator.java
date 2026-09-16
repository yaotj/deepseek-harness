package com.chinasofti.huateng.facepay.support;

import com.chinasofti.huateng.facepay.mapper.F2fSequenceMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单号生成入口。取号走 {@link F2fSequenceMapper}，拼装走 {@link F2fOrderNo}。
 *
 * <p><b>NEVER 在事务里调用本类之后又做 RPC</b>：取号是一条独立 SQL，下单链路的正确顺序是
 * 「取号 → INSERT 订单 → 事务外调支付中心」（AGENTS.md §5.2）。</p>
 */
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
