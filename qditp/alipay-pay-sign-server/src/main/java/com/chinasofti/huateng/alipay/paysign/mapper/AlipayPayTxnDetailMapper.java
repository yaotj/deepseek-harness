package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AlipayPayTxnDetailMapper {


    int insert(AlipayPayTxnDetail record);

    AlipayPayTxnDetail selectByOrderNo(@Param("orderNo") String orderNo);


    List<AlipayPayTxnDetail> selectByOrderNos(@Param("orderNos") List<String> orderNos);

    int markRequesting(@Param("orderNo") String orderNo);

    int updateRequestResult(AlipayPayTxnDetail record);

    int updatePayCallback(AlipayPayTxnDetail record);

    int updatePayQueryResultIfNotSuccess(AlipayPayTxnDetail record);

    int updateRefundSummary(@Param("orderNo") String orderNo);
}
