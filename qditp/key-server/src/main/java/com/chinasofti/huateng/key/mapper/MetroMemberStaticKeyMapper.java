package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.MetroMemberStaticKey;
import com.chinasofti.huateng.key.page.KeyVersionView;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;

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

    /**
     * 综管台密钥版本查看：按状态聚合的 HCE 静态密钥卡数汇总。
     *
     * <p>NEVER 在对应 SQL 里 select KEY_WRAP_VALUE1 明文。</p>
     */
    List<KeyVersionView> selectStatusSummary();
}
