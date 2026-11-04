package com.chinasofti.huateng.blacklist.mapper;

import com.chinasofti.huateng.blacklist.entity.Graylist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 灰名单表（{@code GRAYLIST}）数据访问。 */
@Mapper
public interface GraylistMapper {

    /** 运营页分页查询，按进灰时间倒序；全部条件可选，缺省查全表（表小、观察名单量级有限）。 */
    List<Graylist> selectPage(@Param("cardId") String cardId,
                             @Param("thirdUserId") String thirdUserId,
                             @Param("createTimeBegin") String createTimeBegin,
                             @Param("createTimeEnd") String createTimeEnd);

    /** 直接 INSERT，靠 UK_GRAYLIST_CARD_ID 保证幂等（重灰即已存在，调用侧按约束冲突判定成功）。 */
    int insert(Graylist record);

    /** 按卡ID查当前在灰记录。 */
    Graylist selectByCardId(@Param("cardId") String cardId);

    /** 移出灰名单：物理删除当前在灰行。 */
    int deleteByCardId(@Param("cardId") String cardId);
}
