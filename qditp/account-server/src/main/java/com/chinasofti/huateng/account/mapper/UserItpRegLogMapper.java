package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserItpRegLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface UserItpRegLogMapper {
    int insert(UserItpRegLog record);

    /**
     * 综管台已注销用户查询：并列输入框的 AND 组合查询（{@code OPER_TYPE=2} 销户记录）。
     *
     * <p>三个筛选参数之间是 AND，{@code null} 表示该条件未填、不参与谓词；{@code OPER_TYPE} 恒参与谓词。
     * <b>MUST 由调用方保证至少一个筛选参数非空</b>，否则退化成整表扫描。
     *
     * <p>本表没有签约流水号列，按流水号查不了，该条件在 controller 层就被拒绝。
     */
    List<UserItpRegLog> selectCancelLogByFilters(@Param("msisdn") String msisdn,
                                                 @Param("cardId") String cardId,
                                                 @Param("thirdUserId") String thirdUserId);
}
