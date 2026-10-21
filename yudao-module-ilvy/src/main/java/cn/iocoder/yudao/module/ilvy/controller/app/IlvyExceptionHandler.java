package cn.iocoder.yudao.module.ilvy.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "cn.iocoder.yudao.module.ilvy")
public class IlvyExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<CommonResult<Object>> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatus())
        .body(CommonResult.error(e.getStatus().value(), e.getReason()));
  }

  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.validation.BindException.class,
    javax.validation.ConstraintViolationException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  public ResponseEntity<CommonResult<Object>> invalid(Exception e) {
    return ResponseEntity.badRequest().body(CommonResult.error(400, "输入格式或长度不符合要求"));
  }
}
