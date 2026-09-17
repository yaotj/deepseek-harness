package com.chinasofti.huateng.ticket.mapper;

import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QRCodeStatusMapper {
    QRCodeStatus selectByCardId(@Param("cardId") String cardId);

    int upsert(QRCodeStatus record);

    /**
     * CAS 版 upsert：UPDATE 分支要求 TXN_SEQ 与期望值匹配才写入。
     *
     * @param record 目标状态（含新的 TXN_SEQ = 当前值 + 1）
     * @param expectedTxnSeq CAS 条件：调用方读到的当前 TXN_SEQ
     * @return 影响行数：1 = CAS 成功写入，0 = 序号已被其他请求推进
     */
    int upsertWithCas(@Param("record") QRCodeStatus record, @Param("expectedTxnSeq") String expectedTxnSeq);

    /** 运营端仅修改当前乘车状态，保留进出站和末次交易等行程字段。 */
    int updateCodeStatus(@Param("cardId") String cardId, @Param("codeStatus") String codeStatus);
}
