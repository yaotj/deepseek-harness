package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.MetroCaKeystore;
import com.chinasofti.huateng.key.page.KeyVersionView;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 地铁CA密钥仓库数据访问接口。
 */
@Mapper
@Component
public interface MetroCaKeystoreMapper {
    /**
     * 查询可用CA密钥列表。
     *
     * @return 可用CA密钥列表
     */
    List<MetroCaKeystore> selectActiveList();

    /**
     * 综管台密钥版本查看：CA 密钥仓库元信息（KEY_IDX/生效日期/状态）。
     *
     * <p>NEVER 在对应 SQL 里 select KEY_PRIVATE / KEY_PUBLIC / KEY_PAIR。</p>
     */
    List<KeyVersionView> selectVersionSummary();
}
