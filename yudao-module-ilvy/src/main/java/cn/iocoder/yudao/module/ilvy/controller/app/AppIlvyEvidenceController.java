package cn.iocoder.yudao.module.ilvy.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.EvidenceRecord;
import cn.iocoder.yudao.module.ilvy.service.IlvyEvidenceService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ilvy/feedback/evidence")
@PreAuthorize("isAuthenticated()")
public class AppIlvyEvidenceController {
  private final IlvyEvidenceService service;

  public AppIlvyEvidenceController(IlvyEvidenceService service) {
    this.service = service;
  }

  private long user() {
    Long id = SecurityFrameworkUtils.getLoginUserId();
    if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    return id;
  }

  @PostMapping
  public CommonResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
    return CommonResult.success(service.upload(user(), file));
  }

  @GetMapping("/{id}/info")
  public CommonResult<Map<String, Object>> info(@PathVariable String id) {
    return CommonResult.success(service.info(service.get(user(), id)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<byte[]> read(@PathVariable String id) {
    long user = user();
    EvidenceRecord row = service.get(user, id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(row.getContentType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\""
                + row.getId()
                + (row.getContentType().equals("image/png") ? ".png" : ".jpg")
                + "\"")
        .body(service.bytes(user, id));
  }

  @GetMapping("/expired")
  public CommonResult<List<Map<String, Object>>> expired() {
    return CommonResult.success(service.expired(user()));
  }

  @PostMapping("/expired/discard")
  public CommonResult<Integer> discard(@RequestBody List<String> ids) {
    return CommonResult.success(service.discardExpired(user(), ids));
  }

  @DeleteMapping("/{id}/feedback/{feedbackId}")
  public CommonResult<Boolean> detach(@PathVariable String id, @PathVariable String feedbackId) {
    service.detach(user(), id, feedbackId);
    return CommonResult.success(true);
  }

  @DeleteMapping("/{id}")
  public CommonResult<Boolean> delete(@PathVariable String id) {
    service.remove(user(), id);
    return CommonResult.success(true);
  }
}
