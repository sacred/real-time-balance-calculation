package com.sacred.balance.aspect;

import com.sacred.balance.exception.BusinessException;
import com.sacred.balance.model.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExceptionHandlingAspect {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandlingAspect.class);

    /**
     * 拦截Controller方法并处理异常
     */
    @Around("execution(* com.sacred.balance.controller..*(..))")
    public Object handleControllerExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (BusinessException e) {
            logger.warn("Business exception in {}: code={}, message={}",
                       joinPoint.getSignature().getName(), e.getCode(), e.getMessage());
            return ApiResponse.error(e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Illegal argument exception in {}: {}",
                       joinPoint.getSignature().getName(), e.getMessage());
            return ApiResponse.error(400, "Invalid request: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception in {}: {}",
                        joinPoint.getSignature().getName(), e.getMessage(), e);
            return ApiResponse.error(500, "Internal server error: " + e.getMessage());
        }
    }
}
