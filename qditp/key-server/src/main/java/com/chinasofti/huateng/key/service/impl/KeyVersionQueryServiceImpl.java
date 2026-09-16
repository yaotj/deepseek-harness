package com.chinasofti.huateng.key.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.mapper.MetroAgmKeyVersionMapper;
import com.chinasofti.huateng.key.mapper.MetroCaKeystoreMapper;
import com.chinasofti.huateng.key.mapper.MetroMemberStaticKeyMapper;
import com.chinasofti.huateng.key.page.KeyVersionView;
import com.chinasofti.huateng.key.service.KeyVersionQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 综管台密钥版本查看实现。三个域各自独立查询：单域失败只降级成一条「查询失败」行，
 * NEVER 让整体接口 500（监控页的意义就是在出问题时仍看得到其它域）。
 *
 * <p>状态码→中文的映射集中在 {@link #STATUS_DESC}：AGM 域用 20010/20020/20030，
 * CA 与 HCE 域用 0/1/2，两套口径共存，NEVER 合并成一张表（两个域的 1 含义不同）。</p>
 */
@Service
public class KeyVersionQueryServiceImpl implements KeyVersionQueryService {
    private static final Logger log = LoggerFactory.getLogger(KeyVersionQueryServiceImpl.class);

    private static final String DOMAIN_AGM = "AGM_KEY";
    private static final String DOMAIN_CA = "CA_KEYSTORE";
    private static final String DOMAIN_HCE = "HCE_STATIC_KEY";

    /** 域 → (状态码 → 中文)。AGM 与另两域是两套独立口径，键冲突属预期，故按域分表。 */
    private static final Map<String, Map<String, String>> STATUS_DESC = Map.of(
            DOMAIN_AGM, Map.of("20010", "初始化", "20020", "审批通过", "20030", "拒绝"),
            DOMAIN_CA, Map.of("0", "初始化", "1", "使用中", "2", "已暂停"),
            DOMAIN_HCE, Map.of("0", "初始化", "1", "使用中", "2", "已暂停"));

    private final MetroAgmKeyVersionMapper agmKeyVersionMapper;
    private final MetroCaKeystoreMapper caKeystoreMapper;
    private final MetroMemberStaticKeyMapper memberStaticKeyMapper;

    public KeyVersionQueryServiceImpl(MetroAgmKeyVersionMapper agmKeyVersionMapper,
                                      MetroCaKeystoreMapper caKeystoreMapper,
                                      MetroMemberStaticKeyMapper memberStaticKeyMapper) {
        this.agmKeyVersionMapper = agmKeyVersionMapper;
        this.caKeystoreMapper = caKeystoreMapper;
        this.memberStaticKeyMapper = memberStaticKeyMapper;
    }

    @Override
    public ResultVO<List<KeyVersionView>> versions() {
        List<KeyVersionView> views = new ArrayList<>();
        collect(views, DOMAIN_AGM, agmKeyVersionMapper::selectLatestByProvider);
        collect(views, DOMAIN_CA, caKeystoreMapper::selectVersionSummary);
        collect(views, DOMAIN_HCE, memberStaticKeyMapper::selectStatusSummary);
        return ResultMapper.ok(views);
    }

    /** 单域查询 + 域标记 + 状态翻译；失败降级成一条错误行。 */
    private void collect(List<KeyVersionView> sink, String domain, Supplier<List<KeyVersionView>> query) {
        List<KeyVersionView> rows;
        try {
            rows = query.get();
        } catch (Exception e) {
            log.error("密钥版本查询失败, domain={}", domain, e);
            sink.add(failedRow(domain));
            return;
        }
        if (rows == null || rows.isEmpty()) {
            KeyVersionView empty = new KeyVersionView();
            empty.setKeyDomain(domain);
            empty.setStatusDesc("无数据");
            sink.add(empty);
            return;
        }
        Map<String, String> descTable = STATUS_DESC.getOrDefault(domain, Map.of());
        for (KeyVersionView row : rows) {
            row.setKeyDomain(domain);
            row.setStatusDesc(descTable.getOrDefault(row.getStatus(), row.getStatus()));
            sink.add(row);
        }
    }

    private KeyVersionView failedRow(String domain) {
        KeyVersionView view = new KeyVersionView();
        view.setKeyDomain(domain);
        view.setStatusDesc("查询失败");
        return view;
    }
}
