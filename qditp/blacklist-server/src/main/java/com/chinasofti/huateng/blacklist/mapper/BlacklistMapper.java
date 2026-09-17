package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 黑名单表数据访问接口。
 */
@Mapper
@Component
public interface BlacklistMapper {
    /**
     * 按卡号列表统计黑名单记录数。
     *
     * @param cardIds 卡号列表
     * @return 命中的黑名单记录数
     */
    int countByCardIds(@Param("cardIds") List<String> cardIds);

    /**
     * 按卡号列表查询黑名单记录。
     *
     * @param cardIds 卡号列表
     * @return 黑名单记录列表
     */
    List<Blacklist> selectByCardIds(@Param("cardIds") List<String> cardIds);

    /**
     * 分页查询黑名单管理记录。
     *
     * @param cardId 卡ID，可选
     * @param thirdUserId 三方用户ID，可选
     * @param createTimeBegin 创建时间起，可选
     * @param createTimeEnd 创建时间止，可选
     * @return 黑名单记录
     */
    List<Blacklist> selectPage(@Param("cardId") String cardId,
                               @Param("thirdUserId") String thirdUserId,
                               @Param("createTimeBegin") String createTimeBegin,
                               @Param("createTimeEnd") String createTimeEnd);

    /**
     * 新增黑名单记录。
     *
     * @param record 黑名单记录
     * @return 影响行数
     */
    int insert(Blacklist record);

    /**
     * 按卡号列表物理删除黑名单记录。
     *
     * @param cardIds 卡号列表
     * @return 影响行数
     */
    int deleteByCardIds(@Param("cardIds") List<String> cardIds);

    /**
     * 分批查询黑名单记录，供「可解除性」只读盘点使用。按 CREATE_TIME 升序取最早的 limit 条。
     *
     * @param limit 单次上限
     * @return 黑名单记录列表
     */
    List<Blacklist> selectForInspect(@Param("limit") int limit);
}
