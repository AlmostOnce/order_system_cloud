package com.hbue.ordering.common.web.handler;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.error.ErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>统一捕获业务异常、参数校验异常和未知异常，
 * 将异常转换成统一的 HTTP 响应格式。</p>
 *
 * <p>业务代码中只需要抛出 BusinessException，
 * 不需要在每个 Controller 中重复拼装错误响应。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 当前类的日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception) {

        // 获取业务异常携带的错误码。
        ErrorCode errorCode = exception.getErrorCode();

        // 优先使用异常中携带的详细信息。
        String message = exception.getMessage();

        // 业务异常属于可预期异常，使用 warn 级别记录。
        log.warn(
                "业务异常，code={}, message={}",
                errorCode.code(),
                message
        );

        // 根据错误码转换为对应的 HTTP 状态码。
        HttpStatus httpStatus = resolveHttpStatus(errorCode);

        // 构造统一的失败响应。
        ApiResponse<Void> response = ApiResponse.failure(errorCode, message);

        // 返回正确的 HTTP 状态和响应体。
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * 处理请求对象参数校验异常。
     *
     * <p>该异常通常由 Controller 方法参数上的 @Valid 触发。</p>
     *
     * @param exception 参数校验异常
     * @return 参数错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {

        // 获取第一条字段校验错误，避免一次返回过多内部信息。
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse(CommonErrorCode.BAD_REQUEST.message());

        // 参数错误属于客户端请求问题，记录 warn 日志即可。
        log.warn("请求参数校验失败，message={}", message);

        // 构造参数错误响应。
        ApiResponse<Void> response = ApiResponse.failure(
                CommonErrorCode.BAD_REQUEST,
                message
        );

        // 返回 HTTP 400。
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理方法参数约束校验异常。
     *
     * <p>该异常通常由 @RequestParam、@PathVariable 等参数上的校验注解触发。</p>
     *
     * @param exception 参数约束异常
     * @return 参数错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {

        // 获取第一条约束校验错误。
        String message = exception.getConstraintViolations()
                .stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse(CommonErrorCode.BAD_REQUEST.message());

        // 参数校验失败属于客户端问题，记录 warn 日志。
        log.warn("请求参数约束校验失败，message={}", message);

        // 构造参数错误响应。
        ApiResponse<Void> response = ApiResponse.failure(
                CommonErrorCode.BAD_REQUEST,
                message
        );

        // 返回 HTTP 400。
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理请求体缺失或 JSON 格式错误。
     *
     * <p>这类异常由客户端提交的请求内容导致，不能按系统内部错误返回。</p>
     *
     * @param exception 请求体读取异常
     * @return 参数错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {

        // 请求体缺失或格式错误属于客户端请求问题，只记录简要警告。
        log.warn("请求体缺失或格式错误");

        // 使用统一参数错误码构造响应。
        ApiResponse<Void> response = ApiResponse.failure(
                CommonErrorCode.BAD_REQUEST
        );

        // 返回 HTTP 400，避免将客户端错误误报为系统内部异常。
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理未被明确处理的系统异常。
     *
     * <p>不能将异常堆栈直接返回给前端，
     * 但必须在服务端日志中记录完整堆栈，便于排查问题。</p>
     *
     * @param exception 未知异常
     * @return 系统错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {

        // 记录完整异常堆栈，但不将堆栈返回给客户端。
        log.error("系统发生未处理异常", exception);

        // 构造统一系统错误响应。
        ApiResponse<Void> response = ApiResponse.failure(
                CommonErrorCode.INTERNAL_ERROR
        );

        // 返回 HTTP 500。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 将业务错误码转换为 HTTP 状态码。
     *
     * @param errorCode 业务错误码
     * @return 对应的 HTTP 状态码
     */
    private HttpStatus resolveHttpStatus(ErrorCode errorCode) {
        // 参数错误对应 HTTP 400。
        if (CommonErrorCode.BAD_REQUEST.code().equals(errorCode.code())) {
            return HttpStatus.BAD_REQUEST;
        }

        // 未登录对应 HTTP 401。
        if (CommonErrorCode.UNAUTHORIZED.code().equals(errorCode.code())) {
            return HttpStatus.UNAUTHORIZED;
        }

        // 无权限对应 HTTP 403。
        if (CommonErrorCode.FORBIDDEN.code().equals(errorCode.code())) {
            return HttpStatus.FORBIDDEN;
        }

        // 资源不存在对应 HTTP 404。
        if (CommonErrorCode.NOT_FOUND.code().equals(errorCode.code())) {
            return HttpStatus.NOT_FOUND;
        }

        // 请求与已有资源状态冲突时返回 HTTP 409。
        if (CommonErrorCode.CONFLICT.code().equals(errorCode.code())) {
            return HttpStatus.CONFLICT;
        }

        // 服务暂不可用对应 HTTP 503。
        if (CommonErrorCode.SERVICE_UNAVAILABLE.code()
                .equals(errorCode.code())) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }

        // 其他业务异常默认返回 HTTP 500。
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
