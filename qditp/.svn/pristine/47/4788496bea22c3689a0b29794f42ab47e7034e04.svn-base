package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.MetroCaKeystore;
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
}
