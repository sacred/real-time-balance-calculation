package com.sacred.balance.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceAspect {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceAspect.class);

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 记录Controller方法的执行时间
     */
    @Around("execution(* com.sacred.balance.controller..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        // 记录方法参数
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            try {
                logger.debug("Entering method: {}.{} with args: {}", className, methodName,
                           objectMapper.writeValueAsString(args));
            } catch (Exception e) {
                logger.debug("Entering method: {}.{} with args: {}", className, methodName, args);
            }
        } else {
            logger.debug("Entering method: {}.{}", className, methodName);
        }

        try {
            Object result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            logger.info("Method: {}.{} executed in {} ms", className, methodName, (endTime - startTime));
            return result;
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            logger.error("Exception in method: {}.{} executed in {} ms, Exception: {}",
                        className, methodName, (endTime - startTime), e.getMessage(), e);
            throw e;
        }
    }
}
