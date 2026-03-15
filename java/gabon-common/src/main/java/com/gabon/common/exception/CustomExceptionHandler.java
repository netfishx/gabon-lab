package com.gabon.common.exception;

import com.gabon.common.util.JsonData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.stream.Collectors;

/**


 **/

@ControllerAdvice
//@RestControllerAdvice
@Slf4j
public class CustomExceptionHandler {

    /**
     * 处理 @Valid 和 @Validated 注解的参数校验异常
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    @ResponseBody
    public JsonData handleMethodArgumentNotValidException(MethodArgumentNotValidException e){
        log.error("[参数校验异常]{}", e.getMessage());
        
        // 提取所有字段错误信息
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        
        return JsonData.buildCodeAndMsg(400, errorMsg);
    }

    /**
     * 处理 @Validated 注解的参数校验异常（用于简单参数）
     */
    @ExceptionHandler(value = BindException.class)
    @ResponseBody
    public JsonData handleBindException(BindException e){
        log.error("[参数绑定异常]{}", e.getMessage());
        
        // 提取所有字段错误信息
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        
        return JsonData.buildCodeAndMsg(400, errorMsg);
    }

    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public JsonData handler(Exception e){

        if(e instanceof BizException){
            BizException bizException = (BizException) e;
            log.error("[业务异常]{}",e);
            return JsonData.buildCodeAndMsg(bizException.getCode(),bizException.getMsg());
        }else {
            log.error("[系统异常]{}",e);
            return JsonData.buildError("系统异常");
        }

    }

}
