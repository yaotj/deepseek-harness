package com.chinasofti.huateng.ticket.mapper;

import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QRCodeStatusMapper {
    QRCodeStatus selectByCardId(@Param("cardId") String cardId);

    int upsert(QRCodeStatus record);
}
