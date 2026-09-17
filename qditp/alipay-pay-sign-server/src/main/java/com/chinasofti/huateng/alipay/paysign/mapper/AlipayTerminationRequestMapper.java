package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlipayTerminationRequestMapper {

    /** 按协议号查登记记录。 */
    AlipayTerminationRequest selectByAgreementCode(@Param("agreementCode") String agreementCode);

    /** 判断协议号是否已有登记记录，避免 selectByAgreementCode 在重复数据上抛异常。 */
    int countByAgreementCode(@Param("agreementCode") String agreementCode);

    int insert(AlipayTerminationRequest request);

    /** 按协议号改状态，无状态 CAS。 */
    int updateStatus(@Param("agreementCode") String agreementCode, @Param("status") String status,
                     @Param("updateTime") LocalDateTime updateTime);

    /**
     * 按主键 + 原状态改状态（CAS）。返回 0 表示状态已被别的执行流改走，调用方 MUST 放弃本次处理。
     */
    int updateStatusCas(@Param("terminationSeq") String terminationSeq,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("updateTime") LocalDateTime updateTime);

    /** 按状态取一批登记记录，最老优先。 */
    List<AlipayTerminationRequest> selectByStatusLimit(@Param("status") String status,
                                                      @Param("limit") int limit);

    /** 按状态取一批登记记录，只取 CREATE_TIME 在 cutoff 及之前登记的，最老优先。 */
    List<AlipayTerminationRequest> selectByStatusBefore(@Param("status") String status,
                                                       @Param("cutoff") LocalDateTime cutoff,
                                                       @Param("limit") int limit);
}
