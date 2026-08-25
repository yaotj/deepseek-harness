package com.chinasofti.huateng.ticket.config;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * ticket-server 全局异常处理器。
 *
 * <p>统一拦截 Controller 层未捕获的异常，返回标准错误响应，避免将异常堆栈直接暴露给调用方。
 */
@RestControllerAdvice
public class TicketExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TicketExceptionHandler.class);

    /**
     * 参数类型不匹配（如字符串传入整数字段）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public CommonResult handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配, name={}, value={}, requiredType={}",
                ex.getName(), ex.getValue(), ex.getRequiredType());
        return errorResult(TicketErrorCodeEnum.INVALID_PARAM, "参数格式错误: " + ex.getName());
    }

    /**
     * 通用运行时异常兜底。
     */
    @ExceptionHandler(RuntimeException.class)
    public CommonResult handleRuntimeException(RuntimeException ex) {
        log.error("运行时异常", ex);
        return errorResult(TicketErrorCodeEnum.SYSTEM_ERROR, "系统内部错误");
    }

    /**
     * 其他异常兜底。
     */
    @ExceptionHandler(Exception.class)
    public CommonResult handleException(Exception ex) {
        log.error("未处理异常", ex);
        return errorResult(TicketErrorCodeEnum.SYSTEM_ERROR, "系统内部错误");
    }

    private CommonResult errorResult(TicketErrorCodeEnum errorCode, String msg) {
        CommonResult result = new CommonResult();
        result.setRetCode(errorCode.getCode());
        result.setRetMsg(msg);
        return result;
    }
}
