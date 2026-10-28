package com.chinasofti.huateng.web.controller.importer;

import java.util.List;

/**
 * 导出/导入统一处理器接口。
 *
 * <p>业务域按如下步骤接入：
 * <ol>
 *   <li>定义 {@code @Excel} 注解的 Entity 类（放在对应模块的 model 包）</li>
 *   <li>实现本接口，注册为 Spring Bean（建议加 {@code @Component("someBizHandler")}）</li>
 *   <li>在初始化时向 {@link BizRouter} 注册 {@code bizCode} → 实例的映射</li>
 * </ol>
 *
 * <h3>安全红线（AGENTS.md §4.2）</h3>
 * 涉及支付 / 订单 / 扣款 / 签约 / 开户 / 卡池 / 对账链路的 biz，
 * {@link #handle} 内部 MUST 做幂等兜底（DB 唯一索引 + {@code DuplicateKeyException} 处理），
 * 不得依赖网络层重试（见 {@code ProxyWebClient.isNetworkRetryEnabled()} 已在支付客户端置 false）。
 *
 * @param <T> Excel 行对应的实体类
 */
public interface IImportExportHandler<T>
{
    /**
     * 返回 Excel 行对应的实体类，供 {@link com.chinasofti.huateng.common.utils.poi.ExcelUtil}
     * 通过反射解析 {@code @Excel} 注解。
     */
    Class<T> getEntityClass();

    /**
     * 返回本 biz 在 Excel 中展示的名称（用于标题 / 提示信息）。
     */
    String getEntityLabel(String biz);

    /**
     * 导出查询：根据 biz 与 queryJson 查库返回待导出列表。
     *
     * @param biz       业务标识（与注册时一致）
     * @param queryJson 前端传入的查询条件（JSON 字符串，子类按需解析）
     * @return 待导出的实体列表
     * @throws Exception 业务异常
     */
    List<T> getExportList(String biz, String queryJson) throws Exception;

    /**
     * 导入业务处理：解析后的列表落库或调用下游微服务。
     *
     * <p>幂等兜底 MUST 在本方法内完成。失败明细以异常或日志形式记录，
     * 如需把失败行回写给前端，可在 handler 内部拼装并抛出自定义异常（框架会捕获并返回错误信息）。
     *
     * @param biz             业务标识
     * @param list            已解析好的实体列表
     * @param updateSupport   已存在时是否更新（由业务决定是否生效）
     * @param operator        操作人 username
     * @return 处理结果说明，允许带成功/失败统计
     */
    String handle(String biz, List<T> list, boolean updateSupport, String operator);
}
