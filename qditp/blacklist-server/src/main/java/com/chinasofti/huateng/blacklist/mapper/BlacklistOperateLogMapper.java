package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.BlacklistOperateLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

import java.util.List;


@Mapper
@Component
public interface BlacklistOperateLogMapper {

    int insert(BlacklistOperateLog record);

    @Select("SELECT * FROM BLACKLIST_OPERATE_LOG WHERE CARD_ID = #{cardId} ORDER BY OPERATE_TIME DESC")
    List<BlacklistOperateLog> selectByCardId(@Param("cardId") String cardId);
}
