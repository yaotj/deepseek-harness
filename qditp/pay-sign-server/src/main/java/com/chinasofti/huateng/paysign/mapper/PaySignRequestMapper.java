package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaySignRequestMapper {
    int insert(PaySignRequest record);

    PaySignRequest selectByRequestSignSeq(@Param("requestSignSeq") String requestSignSeq);

    PaySignRequest selectLatestSignResultBySeq(@Param("requestSignSeq") String requestSignSeq);

    List<PaySignRequest> selectCompensableNotify(@Param("maxRetry") int maxRetry,
                                                 @Param("staleMinutes") int staleMinutes,
                                                 @Param("limit") int limit);

    int updateNotifyStatus(PaySignRequest record);

    int increaseRetryCount(@Param("id") Long id);
}
