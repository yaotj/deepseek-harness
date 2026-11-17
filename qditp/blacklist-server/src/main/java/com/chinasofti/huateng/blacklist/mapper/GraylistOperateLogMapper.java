package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.GraylistOperateLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GraylistOperateLogMapper {

    int insert(GraylistOperateLog record);

    List<GraylistOperateLog> selectPage(@Param("cardId") String cardId,
                                        @Param("operateType") String operateType);
}
