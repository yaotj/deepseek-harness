package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCenterMsgLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code ALIPAY_PAY_CENTER_MSG_LOG} 数据访问 —— 只有一条写、一条读。
 *
 * <p><b>只有 INSERT，没有任何 UPDATE，这是本表的核心不变量。</b>一次调用一行、写完即不可变；
 * 一旦允许 UPDATE，就退回了「覆盖式留痕、重试只剩最后一次」的老问题。NEVER 加 update 方法。
 *
 * <p>写入时机 MUST 是「出网前后各一次都写同一行」的<b>反面</b> —— 即：<b>拿到应答（或超时异常）
 * 之后写一行，把请求与应答一起落进去</b>。理由是本项目的观测切面会把异常换类型、且出网前后两次
 * 写会让一次调用变两行、破坏「一次调用一行」。若调用抛异常拿不到应答，{@code RET_CODE} 留空、
 * {@code REMARK} 记异常摘要，报文照样落。
 *
 * <p>写库失败 MUST 只记 WARN、NEVER 打断业务链路：留痕失败不该让一笔能成功的支付失败。
 */
@Mapper
public interface AlipayPayCenterMsgLogMapper {

    /** 追加一行出网记录。本表无唯一索引，重复写入不会冲突，也不需要幂等兜底。 */
    int insert(AlipayPayCenterMsgLog record);

    /**
     * 按订单号取这一单的全部出网记录，供排查用。
     * {@code ORDER BY ID DESC} 是刻意的：{@code CREATE_TIME} 同毫秒时无法定序，而序列单调递增。
     */
    List<AlipayPayCenterMsgLog> selectByOrderNo(@Param("orderNo") String orderNo);
}
