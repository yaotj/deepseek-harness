package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.BlacklistReleased;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 黑名单解除历史表数据访问接口。
 */
@Mapper
@Component
public interface BlacklistReleasedMapper {
    /**
     * 写入解除快照。与主表 DELETE 必须在同一个本地事务内。
     *
     * @param record 解除快照
     * @return 影响行数
     */
    int insert(BlacklistReleased record);

    /**
     * 按卡号查询解除历史，按解除时间倒序。
     *
     * @param cardId 卡ID
     * @return 解除历史记录
     */
    List<BlacklistReleased> selectByCardId(@Param("cardId") String cardId);
}
