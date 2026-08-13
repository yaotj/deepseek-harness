package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.MetroMemberStaticKey;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * HCE 会员卡静态密钥缓存数据访问接口。
 */
@Mapper
@Component
public interface MetroMemberStaticKeyMapper {
    /**
     * 按逻辑卡号查询启用的静态密钥。
     *
     * @param metroMemberCardNum HCE 逻辑卡号
     * @return 静态密钥缓存；未命中时返回 {@code null}
     */
    MetroMemberStaticKey selectActiveByCardNum(String metroMemberCardNum);

    /**
     * 写入静态密钥缓存；若同一卡号已由并发请求写入则保持原有记录。
     *
     * @param staticKey 待缓存的 ACC KEK 加密 DPK
     * @return 受影响行数
     */
    int insertIfAbsent(MetroMemberStaticKey staticKey);
}
