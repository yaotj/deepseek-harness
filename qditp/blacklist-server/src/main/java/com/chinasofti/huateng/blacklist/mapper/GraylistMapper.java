package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.Graylist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GraylistMapper {

    List<Graylist> selectPage(@Param("cardId") String cardId,
                             @Param("thirdUserId") String thirdUserId,
                             @Param("createTimeBegin") String createTimeBegin,
                             @Param("createTimeEnd") String createTimeEnd);

    int insert(Graylist record);

    Graylist selectByCardId(@Param("cardId") String cardId);

    int deleteByCardId(@Param("cardId") String cardId);
}
