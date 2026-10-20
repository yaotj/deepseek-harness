package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.entity.UserItpRegLog;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface UserItpRegLogMapper {
    int insert(UserItpRegLog record);

    /**
     * 按第三方用户 ID 查询销户日志（OPER_TYPE=2）。
     */
    List<UserItpRegLog> selectCancelLogByThirdUserId(String thirdUserId);

    /**
     * 按手机号查询销户日志（OPER_TYPE=2）。
     */
    List<UserItpRegLog> selectCancelLogByMsisdn(String msisdn);

    /**
     * 按逻辑卡号查询销户日志（OPER_TYPE=2）。
     */
    List<UserItpRegLog> selectCancelLogByCardId(String cardId);
}
