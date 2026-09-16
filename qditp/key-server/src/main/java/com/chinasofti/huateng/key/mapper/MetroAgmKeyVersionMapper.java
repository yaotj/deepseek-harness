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
     * 综管台密钥版本查看：按接入方取最新一条 AGM 密钥版本记录。
     *
     * <p>只读元信息（批次号/状态/时间），本表不存密钥材料。NEVER 在本接口
     * 加返回 KEY_VALUE 的方法——密钥材料在 METRO_AGM_KEY_POOL，出库即越红线。</p>
     */
    List<KeyVersionView> selectLatestByProvider();
}
