package com.chinasofti.huateng.web.controller.importer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * BizCode → 导入/导出 handler 的路由表。
 *
 * <p>业务模块在 Spring 容器启动时调用 {@link #register} 注册；
 * 未注册的 biz 在调用 export/importData/importTemplate 时抛 {@link IllegalArgumentException}。
 *
 * <p>注意：这是一个全局共享的单例，所有业务模块共用同一张路由表。
 * 若多个模块同名字段冲突（例如两个 biz 都叫 {@code "user"}），请确保 bizCode 命名空间隔离。
 *
 * @author tuanjie
 */
@Component
public class BizRouter
{
    private final Map<String, IImportExportHandler<?>> handlers = new HashMap<>();

    /**
     * 注册一个 biz 对应的 handler。重复注册会覆盖上一次。
     *
     * @param biz      业务标识，必须非空且不与已有注册冲突
     * @param handler  业务处理器，必须非空
     * @throws IllegalArgumentException 若参数为空
     */
    public void register(String biz, IImportExportHandler<?> handler)
    {
        if (biz == null || biz.isBlank())
        {
            throw new IllegalArgumentException("biz 不能为空");
        }
        if (handler == null)
        {
            throw new IllegalArgumentException("handler 不能为空，biz=" + biz);
        }
        handlers.put(biz.trim(), handler);
    }

    /**
     * 获取已注册的 handler。
     *
     * @param biz 业务标识
     * @return 对应 handler
     * @throws IllegalArgumentException 若未注册
     */
    @SuppressWarnings("unchecked")
    public <H extends IImportExportHandler<?>> H getHandler(String biz)
    {
        H handler = (H) handlers.get(biz);
        if (handler == null)
        {
            throw new IllegalArgumentException("未注册的 biz: " + biz
                    + "，请在业务初始化时调用 BizRouter.register(biz, handler)");
        }
        return handler;
    }

    /**
     * 返回当前已注册的所有 bizCode（只读视图）。
     */
    public Map<String, IImportExportHandler<?>> allHandlers()
    {
        return Collections.unmodifiableMap(handlers);
    }
}
