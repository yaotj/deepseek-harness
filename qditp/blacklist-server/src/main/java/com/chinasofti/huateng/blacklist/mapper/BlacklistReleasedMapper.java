package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.BlacklistReleased;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;


@Mapper
@Component
public interface BlacklistReleasedMapper {

    int insert(BlacklistReleased record);


    List<BlacklistReleased> selectByCardId(@Param("cardId") String cardId);
}
