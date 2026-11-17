package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.page.PayRefundOrderView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;


@Service
public class PayRefundPageQueryService {


    private static final int MAX_ORDER_NOS = 200;

    private final PayRefundDetailMapper payRefundDetailMapper;

    public PayRefundPageQueryService(PayRefundDetailMapper payRefundDetailMapper) {
        this.payRefundDetailMapper = payRefundDetailMapper;
    }


    public List<PayRefundOrderView> byOrderNos(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            throw new IllegalArgumentException("订单号列表不能为空");
        }
        List<String> distinct = orderNos.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("订单号列表不能为空");
        }
        if (distinct.size() > MAX_ORDER_NOS) {
            throw new IllegalArgumentException("单次最多查询 " + MAX_ORDER_NOS + " 个订单号");
        }
        return payRefundDetailMapper.selectViewsByOrderNos(distinct);
    }
}
