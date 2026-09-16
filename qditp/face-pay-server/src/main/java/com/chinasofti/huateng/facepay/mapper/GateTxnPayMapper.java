package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.GateTxnPay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * face-pay-server 对 GATE_TXN_PAY 表的**只读**访问接口，现在只剩一个方法。
 *
 * <p>{@link #selectByOrderNos} —— 补款下单校验待补款的原订单状态。</p>
 *
 * <p><b>原先还有一个 convergeDebitStatus（补款成功后把原订单改成 SUCCESS），2026-09-16 已删除</b>：
 * GATE_TXN_PAY 的 owner 是 gate-txn-pay-server，跨域直写它的热路径表违反「热路径写入定 owner」判据 ——
 * 同一张表两个模块各持一份 UPDATE，白名单一旦漂移就是资金账不平，编译与单测都发现不了。
 * 收敛现走 RPC：{@code GateTxnPayClient.convergeDebitStatusForSupplement}
 * → {@code POST /internal/gate-txn-pay/debit/converge}，对端语句名
 * {@code convergeDebitStatusForSupplement}、白名单含 {@code FAIL}、行为与删掉那条逐笔一致。
 * <b>NEVER 在本接口里加回任何 GATE_TXN_PAY 的写方法。</b></p>
 *
 * <p>剩下这个查询仍是**跨域读**（共享 Oracle schema），属有意保留的现状；若未来拆库，它也 MUST 改 RPC。</p>
 */
@Mapper
public interface GateTxnPayMapper {

    /**
     * 按订单号列表批量查询扣费订单（用于补款下单校验待补款的原订单）。
     *
     * <p>不带 TXN_DATE 因此走不到分区裁剪，只命中 UK_GATE_TXN_PAY_ORDER_NO 的前缀列。
     * 调用方 MUST 限制列表长度（Oracle IN 列表上限 1000）。</p>
     */
    List<GateTxnPay> selectByOrderNos(@Param("orderNos") List<String> orderNos);
}
