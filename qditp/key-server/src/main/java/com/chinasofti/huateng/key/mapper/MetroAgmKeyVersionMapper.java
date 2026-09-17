package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.page.KeyVersionView;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface MetroAgmKeyVersionMapper {
    Long selectMaxApprovedBatchNumber(String providerId);

    /**
     * 综管台密钥版本查看：按接入方取最新一条 AGM 密钥版本记录（只读元信息）。
     *
     * <p>NEVER 在本 mapper 新增返回 KEY_VALUE 的方法。</p>
     */
    List<KeyVersionView> selectLatestByProvider();
}
