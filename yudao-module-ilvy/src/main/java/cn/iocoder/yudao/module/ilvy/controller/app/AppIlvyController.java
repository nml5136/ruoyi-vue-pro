package cn.iocoder.yudao.module.ilvy.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.*;
import cn.iocoder.yudao.module.ilvy.service.IlvyService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/ilvy")
public class AppIlvyController {
  private final IlvyService service;

  public AppIlvyController(IlvyService service) {
    this.service = service;
  }

  private long user() {
    Long id = SecurityFrameworkUtils.getLoginUserId();
    if (id == null)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录");
    return id;
  }

  @GetMapping("/places")
  @PermitAll
  public CommonResult<List<JsonNode>> places(@Valid Query q) {
    return CommonResult.success(
        Boolean.TRUE.equals(q.getFavoriteOnly())
            ? service.searchSaved(user(), q)
            : service.search(q));
  }

  @GetMapping("/places/{id}")
  @PermitAll
  public CommonResult<JsonNode> place(@PathVariable String id) {
    return CommonResult.success(service.place(id));
  }

  @GetMapping("/collections")
  @PermitAll
  public CommonResult<Map<String, List<CatalogCollection>>> collections() {
    return CommonResult.success(service.collections());
  }

  @GetMapping("/capabilities")
  @PermitAll
  public CommonResult<Map<String, Object>> capabilities() {
    Map<String, Object> c = new HashMap<>();
    for (String k : Arrays.asList("map", "weather", "panorama", "payment", "wechat"))
      c.put(k, false);
    return CommonResult.success(c);
  }

  @GetMapping("/state")
  @PreAuthorize("isAuthenticated()")
  public CommonResult<State> state() {
    return CommonResult.success(service.state(user()));
  }

  @PutMapping("/state")
  @PreAuthorize("isAuthenticated()")
  public CommonResult<State> state(@Valid @RequestBody State s) {
    return CommonResult.success(service.save(user(), s));
  }

  @PostMapping("/feedback")
  @PreAuthorize("isAuthenticated()")
  public CommonResult<Map<String, String>> feedback(@Valid @RequestBody Feedback f) {
    return CommonResult.success(service.feedback(user(), f));
  }

  @GetMapping("/feedback")
  @PreAuthorize("isAuthenticated()")
  public CommonResult<List<Map<String, Object>>> feedback() {
    return CommonResult.success(service.feedback(user()));
  }
}
