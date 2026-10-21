package cn.iocoder.yudao.module.ilvy.service;

import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.*;
import cn.iocoder.yudao.module.ilvy.dal.IlvyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IlvyService {
  private final IlvyRepository repo;

  public IlvyService(IlvyRepository repo) {
    this.repo = repo;
  }

  private static boolean contains(JsonNode values, String expected) {
    for (JsonNode v : values) if (expected.equals(v.asText())) return true;
    return false;
  }

  public List<JsonNode> search(Query q) {
    return repo.places().stream().filter(p -> matches(p, q)).collect(Collectors.toList());
  }

  public static boolean matches(JsonNode p, Query q) {
    for (String[] f :
        new String[][] {
          {"province", q.getProvince()},
          {"city", q.getCity()},
          {"district", q.getDistrict()},
          {"type", q.getType()},
          {"level", q.getLevel()}
        })
      if (f[1] != null
          && !f[1].isEmpty()
          && !p.path(f[0]).asText().equals(f[1])
          && !(f[0].equals("type") && contains(p.path("types"), f[1]))) return false;
    if (q.getQ() != null && !q.getQ().trim().isEmpty()) {
      String text =
          Arrays.asList("name", "alias", "province", "city", "district", "type", "themes").stream()
              .map(k -> p.path(k).toString())
              .collect(Collectors.joining(" "));
      if (!text.toLowerCase(Locale.ROOT).contains(q.getQ().trim().toLowerCase(Locale.ROOT)))
        return false;
    }
    if (q.getTheme() != null && !q.getTheme().isEmpty()) {
      boolean found = false;
      for (JsonNode t : p.path("themes")) if (t.asText().equals(q.getTheme())) found = true;
      if (!found) return false;
    }
    if (q.getMaxDrive() != null
        && (!p.path("driveMinutes").isNumber() || p.path("driveMinutes").asInt() > q.getMaxDrive()))
      return false;
    if (Boolean.TRUE.equals(q.getFree())
        && (!p.path("feeAmount").isNumber() || p.path("feeAmount").asDouble() != 0)) return false;
    if (q.getBudget() != null
        && (!p.path("feeAmount").isNumber() || p.path("feeAmount").asDouble() > q.getBudget()))
      return false;
    if (q.getAge() != null
        && (!p.path("ageMin").isNumber()
            || !p.path("ageMax").isNumber()
            || q.getAge() < p.path("ageMin").asInt()
            || q.getAge() > p.path("ageMax").asInt())) return false;
    if (q.getMaxStay() != null
        && (!p.path("stayHours").isNumber() || p.path("stayHours").asDouble() > q.getMaxStay()))
      return false;
    if (Boolean.TRUE.equals(q.getBooking()) && !p.path("booking").path("needed").asBoolean(false))
      return false;
    if (Boolean.TRUE.equals(q.getStroller())) {
      boolean found = false;
      for (JsonNode f : p.path("facilities"))
        if (f.path("label").asText().equals("推车通行") && f.path("state").asText().equals("有"))
          found = true;
      if (!found) return false;
    }
    return true;
  }

  public JsonNode place(String id) {
    return repo.places().stream()
        .filter(p -> p.path("id").asText().equals(id))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "地点不存在"));
  }

  public State state(long user) {
    return repo.state(user);
  }

  @Transactional
  public State save(long user, State s) {
    if (!s.getPlanDate().isEmpty())
      try {
        LocalDate.parse(s.getPlanDate());
      } catch (Exception e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "计划日期无效");
      }
    Set<String> ids = new HashSet<>();
    for (PlanItem item : s.getPlan()) {
      place(item.getPlaceId());
      if (!ids.add(item.getPlaceId()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "计划地点重复");
    }
    ids.clear();
    for (Favorite f : s.getFavorites()) {
      place(f.getPlaceId());
      if (!ids.add(f.getPlaceId()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收藏地点重复");
    }
    ids.clear();
    for (String id : s.getCompare()) {
      place(id);
      if (!ids.add(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对比地点重复");
    }
    long expected = s.getVersion();
    s.setVersion(expected + 1);
    if (expected == 0) {
      try {
        repo.insert(user, s);
      } catch (DuplicateKeyException e) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "数据已更新，请刷新后重试");
      }
    } else if (repo.update(user, s, expected) != 1)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "数据已更新，请刷新后重试");
    return s;
  }

  public Map<String, String> feedback(long user, Feedback f) {
    place(f.getPlaceId());
    String id = UUID.randomUUID().toString();
    repo.feedback(user, id, f.getPlaceId(), f.getCategory(), f.getDescription());
    Map<String, String> r = new HashMap<>();
    r.put("id", id);
    r.put("status", "待核实");
    return r;
  }

  public List<Map<String, Object>> feedback(long user) {
    return repo.feedback(user);
  }
}
