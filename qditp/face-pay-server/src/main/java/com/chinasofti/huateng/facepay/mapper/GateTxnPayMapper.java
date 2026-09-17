package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.GateTxnPay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** face-pay-server 对 GATE_TXN_PAY 表的**只读**访问接口，现在只剩一个方法。 */
@Mapper
public interface GateTxnPayMapper {

    /** 按订单号列表批量查询扣费订单（用于补款下单校验待补款的原订单）。 */
    List<GateTxnPay> selectByOrderNos(@Param("orderNos") List<String> orderNos);
}
