package com.chinasofti.huateng.acc.security.server.component;

import com.chinasofti.huateng.acc.security.server.exception.HsmUnavailableException;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理Controller层所有异常
     */
//    @ExceptionHandler
//    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ResultVO<Object> handle(ConstraintViolationException exception) {
        StringBuilder message = new StringBuilder();
        if (exception instanceof ValidationException) {
            logger.error("controller传入参数异常");
            Set<ConstraintViolation<?>> violations = exception.getConstraintViolations();
            for (ConstraintViolation<?> item : violations) {
                message.append(item.getMessage());
                message.append(",");
            }
        }
        return ResultMapper.illegalParams(message.toString());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
//        Map<String, String> errors = new HashMap<>();
        StringBuilder message = new StringBuilder();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
//            errors.put(fieldName, errorMessage);
            message.append(fieldName + "：" + errorMessage);
        });
//        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
        logger.error(message.toString());
        return ResultMapper.illegalParams(message.toString());
    }

    @ExceptionHandler(HsmUnavailableException.class)
    public ResultVO<Object> handleHsmUnavailableException(HsmUnavailableException ex) {
        logger.error("加密机连接不可用: {}", ex.getMessage());
        return ResultMapper.hsmUnavailable();
    }

}

