package com.chinasofti.huateng.ticket.mapper;

import com.chinasofti.huateng.ticket.entity.SupplementRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** IF5A-03 补站请求台账。 */
@Mapper
public interface SupplementRequestMapper {

    /** 声明一次补站请求。 */
    int insertClaim(SupplementRequest record);

    SupplementRequest selectByKey(@Param("cardId") String cardId,
                                 @Param("txnSeq") String txnSeq,
                                 @Param("adviceOpt") String adviceOpt);

    /**
     * 把上一次已被闸机明确拒绝的请求重新置为处理中，供 BOM 合法重发。
     *
     * @return 1 = 重新声明成功，0 = 不可重新声明
     */
    int reclaimRejected(@Param("cardId") String cardId,
                        @Param("txnSeq") String txnSeq,
                        @Param("adviceOpt") String adviceOpt,
                        @Param("codeStatusSnapshot") String codeStatusSnapshot,
                        @Param("handleStationCode") String handleStationCode,
                        @Param("handleDateTime") String handleDateTime,
                        @Param("trxAmount") String trxAmount);

    /**
     * 收口：只允许从 {@code PENDING} 迁出，避免重复回写把终态覆盖掉。
     *
     * @return 影响行数，0 表示已被别人收口过
     */
    int finishClaim(@Param("cardId") String cardId,
                    @Param("txnSeq") String txnSeq,
                    @Param("adviceOpt") String adviceOpt,
                    @Param("handleStatus") String handleStatus,
                    @Param("failReason") String failReason);

    /** 扫出结果未知的请求，供人工或外部定时任务处置。 */
    List<SupplementRequest> selectUnknown(@Param("limit") int limit);
}
