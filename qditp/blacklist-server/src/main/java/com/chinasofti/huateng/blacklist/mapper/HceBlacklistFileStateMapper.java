package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.HceBlacklistFileState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;


@Mapper
@Component
public interface HceBlacklistFileStateMapper {


    HceBlacklistFileState selectForUpdate(@Param("paramType") String paramType);


    int insert(@Param("paramType") String paramType, @Param("currentVersion") Long currentVersion);


    int updateVersion(@Param("paramType") String paramType, @Param("currentVersion") Long currentVersion);
}
