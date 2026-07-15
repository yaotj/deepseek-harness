package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaySignRequestMapper {
    int insert(PaySignRequest record);

    // 通过 requestSignSeq 查询（取最新一条，用于补充 thirdUserId）
    PaySignRequest selectByRequestSignSeq(@Param("requestSignSeq") String requestSignSeq);

    // 查询需要补偿的通知
    List<PaySignRequest> selectByNotifyStatus(@Param("notifyStatus") String notifyStatus,
                                               @Param("maxRetry") int maxRetry);

    // 更新通知状态
    int updateNotifyStatus(PaySignRequest record);

    // 增加重试次数
    int increaseRetryCount(@Param("id") Long id);
}
