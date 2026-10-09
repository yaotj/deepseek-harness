package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchView;
import com.chinasofti.huateng.alipay.account.service.AlipayUserPageQueryService;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 支付宝注册用户运营查询实现：三种查询类型的分派 + 展示脱敏 + 内存分页。
 *
 * <p>只读 `ALIPAY_USER_INFO` 一张表、不出网、不带事务（都是单条 SELECT，自动提交即可）。
 *
 * <p><b>本类 NEVER 使用 PageHelper（`PageHelper.startPage` / `doSelectPageInfo`），三支统一在内存里切片。</b>
 * 原因是实测缺陷（2026-09-20 修复，1.0.20）：`alipay-account-server` 的启动类**没有**
 * {@code @EnableDefaultMybatisAutoConfig}（全仓 12 个模块有、只有本模块与其它支付宝模块没有），
 * 因此 {@code DefaultMybatisConfiguration} 不会被 `@Import`、`PageInterceptor` 这个 bean 压根不存在。
 * 没有拦截器时 {@code PageHelper.startPage(...)} 只往 ThreadLocal 放了一个空 `Page`，
 * {@code doSelectPageInfo} 读回来的就是那个空对象 ⇒ **DB 明明返回了行（日志 `fetchRowCount:1`），
 * 接口却返 `total=0, list=[]`**，且不报错、不打日志、单测也发现不了。综管台「支付宝注册用户」页
 * 按手机号查因此从上线起 100% 空，而按逻辑卡号 / 第三方用户 ID 查正常（那两支本就没走 PageHelper）。
 *
 * <p><b>NEVER 改用「给启动类加 `@EnableDefaultMybatisAutoConfig`」来修</b>：那个注解同时 `@Import`
 * {@code DynamicMybatisConfiguration} 与 {@code @EnableTransactionManagement(proxyTargetClass = true)}，
 * 会改变本模块的 SqlSessionFactory 装配与事务代理方式，为一个只读查询页承担这个风险不值当。
 *
 * <p>内存分页在本表可接受：手机号无唯一约束但同号命中量是个位数，`THIRD_USER_ID`（主键
 * `PK_ALIPAY_USER_INFO`）与 `CARD_ID`（唯一索引 `IDX_ALIPAY_USER_INFO_CARD_ID`，2026-09-20 实测）
 * 更是恒 ≤1 行。**若将来该表量级变大，MUST 改成 mapper 侧 `ROWNUM` 分页 + 独立 count，NEVER 回退到 PageHelper。**
 */
@Service
public class AlipayUserPageQueryServiceImpl implements AlipayUserPageQueryService {
    private final AlipayUserInfoMapper alipayUserInfoMapper;

    public AlipayUserPageQueryServiceImpl(AlipayUserInfoMapper alipayUserInfoMapper) {
        this.alipayUserInfoMapper = alipayUserInfoMapper;
    }

    @Override
    public PageInfo<AlipayUserSearchView> search(String queryType, String keyword, Integer pageNum, Integer pageSize) {
        String trimmed = keyword == null ? null : keyword.trim();
        return switch (queryType) {
            case "THIRD_USER_ID" -> singleRowPage(alipayUserInfoMapper.selectByThirdUserId(trimmed));
            case "CARD_ID" -> singleRowPage(alipayUserInfoMapper.selectByCardId(trimmed));
            case "MSISDN" -> slicePage(alipayUserInfoMapper.selectByMsisdn(trimmed), safePageNum(pageNum), safePageSize(pageSize));
            default -> null;
        };
    }

    /** 唯一键命中的单行结果包成一页，行数即 0 或 1。 */
    private PageInfo<AlipayUserSearchView> singleRowPage(AlipayUserInfo user) {
        return new PageInfo<>(user == null ? List.of() : List.of(toView(user)));
    }

    /**
     * 把整表命中结果在内存里切成一页，并**手工补齐分页元数据**。
     *
     * <p><b>MUST 自己 set total / pageNum / pageSize / pages</b>：`new PageInfo<>(切片后的 list)` 会把
     * total 写成当页条数，前台 `web/src/views/trans/user/alipay/index.vue:42` 的分页条据此判断有没有下一页，
     * total 失真即翻不到第二页。
     */
    private PageInfo<AlipayUserSearchView> slicePage(List<AlipayUserInfo> users, int pageNum, int pageSize) {
        List<AlipayUserSearchView> views = users == null ? List.of() : users.stream().map(this::toView).toList();
        int total = views.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        PageInfo<AlipayUserSearchView> result = new PageInfo<>(views.subList(from, to));
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setPages((total + pageSize - 1) / pageSize);
        return result;
    }

    private AlipayUserSearchView toView(AlipayUserInfo user) {
        // 手机号和第三方支付标识均按运营展示要求脱敏。
        AlipayUserSearchView view = new AlipayUserSearchView();
        view.setThirdUserId(user.getThirdUserId());
        view.setCardId(user.getCardId());
        view.setCardType(user.getCardType());
        view.setMsisdn(maskPhone(user.getMsisdn()));
        view.setChannel(user.getChannel());
        view.setThirdPayId(maskValue(user.getThirdPayId()));
        view.setStatus(user.getStatus());
        view.setCreateTime(user.getCreateTime());
        view.setUpdateTime(user.getUpdateTime());
        return view;
    }

    private String maskPhone(String value) { return value != null && value.length() >= 7 ? value.substring(0, 3) + "****" + value.substring(value.length() - 4) : value; }
    private String maskValue(String value) { return value != null && value.length() > 6 ? value.substring(0, 3) + "****" + value.substring(value.length() - 3) : value; }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
