package com.chinasofti.huateng.online.mapper;

import com.chinasofti.huateng.online.entity.QRCodeStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QRCodeStatusMapper {
    QRCodeStatus selectByCardId(@Param("cardId") String cardId);

    QRCodeStatus selectByItpUserId(@Param("itpUserId") String itpUserId);

    int upsert(QRCodeStatus record);
}
