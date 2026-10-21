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

  public List<JsonNode> searchSaved(long user, Query q) {
    Set<String> ids =
        repo.state(user).getFavorites().stream()
            .map(Favorite::getPlaceId)
            .collect(Collectors.toSet());
    return repo.places().stream()
        .filter(p -> ids.contains(p.path("id").asText()) && matches(p, q))
        .collect(Collectors.toList());
  }

  private static List<String> facets(String legacy, List<String> values) {
    Set<String> result = new LinkedHashSet<>();
    if (legacy != null && !legacy.isEmpty()) result.add(legacy);
    if (values != null) result.addAll(values);
    return new ArrayList<>(result);
  }

  public static boolean matches(JsonNode p, Query q) {
    for (String[] f :
        new String[][] {
          {"province", q.getProvince()},
          {"city", q.getCity()},
          {"district", q.getDistrict()}
        }) if (f[1] != null && !f[1].isEmpty() && !p.path(f[0]).asText().equals(f[1])) return false;
    if (q.getQ() != null && !q.getQ().trim().isEmpty()) {
      String text =
          Arrays.asList("name", "alias", "province", "city", "district", "type", "themes").stream()
              .map(k -> p.path(k).toString())
              .collect(Collectors.joining(" "));
      if (!text.toLowerCase(Locale.ROOT).contains(q.getQ().trim().toLowerCase(Locale.ROOT)))
        return false;
    }
    List<String> types = facets(q.getType(), q.getTypes()),
        themes = facets(q.getTheme(), q.getThemes()),
        levels = facets(q.getLevel(), q.getLevels());
    if (!types.isEmpty()
        && types.stream()
            .noneMatch(t -> t.equals(p.path("type").asText()) || contains(p.path("types"), t)))
      return false;
    if (!themes.isEmpty() && themes.stream().noneMatch(t -> contains(p.path("themes"), t)))
      return false;
    if (!levels.isEmpty() && !levels.contains(p.path("level").asText())) return false;
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

  public Map<String, List<CatalogCollection>> collections() {
    List<JsonNode> places = repo.places();
    Map<String, CatalogCollection> regions = new LinkedHashMap<>();
    for (JsonNode p : places) {
      String province = p.path("province").asText(),
          city = p.path("city").asText(),
          district = p.path("district").asText();
      String parent = null;
      for (int depth = 0; depth < 3; depth++) {
        String kind = new String[] {"province", "city", "district"}[depth];
        String name = new String[] {province, city, district}[depth];
        if (name.isEmpty()) break;
        String key =
            kind
                + ":"
                + province
                + (depth >= 1 ? "/" + city : "")
                + (depth >= 2 ? "/" + district : "");
        CatalogCollection c = regions.get(key);
        if (c == null) {
          c =
              new CatalogCollection()
                  .setId(key)
                  .setParentId(parent)
                  .setKind(kind)
                  .setName(name)
                  .setProvince(province)
                  .setCity(depth >= 1 ? city : "")
                  .setDistrict(depth >= 2 ? district : "")
                  .setTitle(name + " · 区域探索")
                  .setIntro("先按城市与主题查看当前目录；目的地介绍、适合季节和建议天数尚待核实。")
                  .setSource("根据当前地点目录聚合；数量仅代表已加载目录，不代表全国覆盖");
          regions.put(key, c);
        }
        c.getPlaceIds().add(p.path("id").asText());
        parent = key;
      }
    }
    List<CatalogCollection> topics = new ArrayList<>();
    String[][] definitions = {
      {"family", "亲子", "一起发现小世界", "先看适龄、推车与休息条件，再留出一起探索的时间。"},
      {"architecture", "古建", "从一处屋檐开始", "结合结构示意与短讲解，从整体看到一处构件。"},
      {"camp", "露营", "把日子留给自然", "分别确认车辆、过夜、用火和收费单位，再决定怎么安排。"},
      {"photo", "摄影", "慢慢看一片风景", "先看资料和拍摄条件；照片与示意图不代表当前天气或客流。"},
      {"nature", "自然探索", "沿途，发现小世界", "把兴趣与实际体力条件一起考虑，给休息留些空间。"},
      {"night", "夜游", "入夜之后，慢慢走", "出发前核实开放时段和返程方式，不推测灯光或夜间开放状态。"}
    };
    for (String[] def : definitions) {
      CatalogCollection c =
          new CatalogCollection()
              .setId(def[0])
              .setKind("topic")
              .setName(def[1])
              .setTheme(def[1])
              .setTitle(def[2])
              .setIntro(def[3])
              .setSource("产品编辑说明；关联地点以当前目录为准");
      for (JsonNode p : places)
        if (contains(p.path("themes"), def[1])) c.getPlaceIds().add(p.path("id").asText());
      topics.add(c);
    }
    List<CatalogCollection> destinations = new ArrayList<>(regions.values());
    List<CatalogCollection> all = new ArrayList<>(destinations);
    all.addAll(topics);
    for (CatalogCollection c : all) {
      c.setPlaceCount(c.getPlaceIds().size());
      List<JsonNode> rows =
          places.stream()
              .filter(p -> c.getPlaceIds().contains(p.path("id").asText()))
              .collect(Collectors.toList());
      c.setSampleOnly(
          !rows.isEmpty() && rows.stream().allMatch(p -> p.path("demo").asBoolean(false)));
      Set<String> themes = new LinkedHashSet<>();
      for (JsonNode p : rows) for (JsonNode theme : p.path("themes")) themes.add(theme.asText());
      c.setThemes(new ArrayList<>(themes));
    }
    Map<String, List<CatalogCollection>> result = new LinkedHashMap<>();
    result.put("destinations", destinations);
    result.put("topics", topics);
    return result;
  }

  public JsonNode place(String id) {
    JsonNode row = repo.place(id);
    if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地点不存在");
    return row;
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

  @Transactional
  public Map<String, String> feedback(long user, Feedback f) {
    place(f.getPlaceId());
    String id = UUID.randomUUID().toString();
    if (new HashSet<>(f.getEvidenceIds()).size() != f.getEvidenceIds().size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "证据图片重复");
    repo.feedback(user, id, f.getPlaceId(), f.getCategory(), f.getDescription());
    for (String evidence : f.getEvidenceIds())
      if (repo.linkEvidence(user, evidence, id) != 1)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "证据不存在、已提交或不属于当前会员");
    Map<String, String> r = new HashMap<>();
    r.put("id", id);
    r.put("status", "待核实");
    return r;
  }

  public List<Map<String, Object>> feedback(long user) {
    List<Map<String, Object>> rows = repo.feedback(user);
    for (Map<String, Object> row : rows)
      row.put("evidence_ids", repo.evidenceIds(user, String.valueOf(row.get("id"))));
    return rows;
  }
}
