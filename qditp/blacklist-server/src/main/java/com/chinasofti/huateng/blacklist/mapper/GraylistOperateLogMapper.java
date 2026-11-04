package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.GraylistOperateLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 灰名单操作记录（{@code GRAYLIST_OPERATE_LOG}）数据访问。 */
@Mapper
public interface GraylistOperateLogMapper {

    /** 写入一条操作流水。 */
    int insert(GraylistOperateLog record);

    /** 运营页分页查询流水，按操作时间倒序；cardId / operateType 可选。 */
    List<GraylistOperateLog> selectPage(@Param("cardId") String cardId,
                                        @Param("operateType") String operateType);
}
