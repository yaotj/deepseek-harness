package com.chinasofti.huateng.web.controller.importer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;


@Component
public class BizRouter
{
    private final Map<String, IImportExportHandler<?>> handlers = new HashMap<>();


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


    public Map<String, IImportExportHandler<?>> allHandlers()
    {
        return Collections.unmodifiableMap(handlers);
    }
}
