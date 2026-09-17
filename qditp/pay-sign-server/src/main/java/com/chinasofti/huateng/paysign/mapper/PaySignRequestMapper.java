package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaySignRequestMapper {
    int insert(PaySignRequest record);

    PaySignRequest selectByRequestSignSeq(@Param("requestSignSeq") String requestSignSeq);

    /** 取指定流水号下最新一条**签约结果**流水（OPERATION_TYPE='RECEIVE_SIGN_RESULT'）。 */
    PaySignRequest selectLatestSignResultBySeq(@Param("requestSignSeq") String requestSignSeq);

    /** 取一批需要补偿通知的签约流水，按 CREATE_TMS 升序（最久没成功的先补），并过滤已超过最大重试次数的记录。 */
    List<PaySignRequest> selectCompensableNotify(@Param("maxRetry") int maxRetry,
                                                 @Param("staleMinutes") int staleMinutes,
                                                 @Param("limit") int limit);

    int updateNotifyStatus(PaySignRequest record);

    int increaseRetryCount(@Param("id") Long id);
}
