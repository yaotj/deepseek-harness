package com.chinasofti.huateng.ticket.mapper;

import com.chinasofti.huateng.ticket.entity.SupplementRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SupplementRequestMapper {

    int insertClaim(SupplementRequest record);

    SupplementRequest selectByKey(@Param("cardId") String cardId,
                                 @Param("txnSeq") String txnSeq,
                                 @Param("adviceOpt") String adviceOpt);


    int reclaimRejected(@Param("cardId") String cardId,
                        @Param("txnSeq") String txnSeq,
                        @Param("adviceOpt") String adviceOpt,
                        @Param("codeStatusSnapshot") String codeStatusSnapshot,
                        @Param("handleStationCode") String handleStationCode,
                        @Param("handleDateTime") String handleDateTime,
                        @Param("trxAmount") String trxAmount);


    int finishClaim(@Param("cardId") String cardId,
                    @Param("txnSeq") String txnSeq,
                    @Param("adviceOpt") String adviceOpt,
                    @Param("handleStatus") String handleStatus,
                    @Param("failReason") String failReason);

    List<SupplementRequest> selectUnknown(@Param("limit") int limit);
}
