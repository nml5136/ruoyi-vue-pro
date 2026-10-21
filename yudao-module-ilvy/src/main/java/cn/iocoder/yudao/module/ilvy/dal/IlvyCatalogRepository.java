package cn.iocoder.yudao.module.ilvy.dal;

import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class IlvyCatalogRepository {
  private final JdbcTemplate jdbc;
  private final IlvyRepository repo;

  public IlvyCatalogRepository(JdbcTemplate jdbc, IlvyRepository repo) {
    this.jdbc = jdbc;
    this.repo = repo;
  }

  private static byte[] exact(String s) {
    return (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
  }

  private Object number(JsonNode p, String key) {
    if (!p.path(key).isNumber()) return null;
    if (Arrays.asList("driveMinutes", "ageMin", "ageMax").contains(key)) return p.path(key).asInt();
    return p.path(key).asDouble();
  }

  private static String hash(String payload) {
    try {
      StringBuilder s = new StringBuilder();
      for (byte b :
          MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)))
        s.append(String.format("%02x", b & 255));
      return s.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean index(JsonNode p) {
    String id = p.path("id").asText(), sha = hash(p.toString());
    List<String> old =
        jdbc.query(
            "SELECT payload_hash FROM ilvy_place_index WHERE place_id=?",
            (r, n) -> r.getString(1),
            id);
    if (old.size() == 1 && sha.equals(old.get(0))) return false;
    jdbc.update("DELETE FROM ilvy_place_tag WHERE place_id=?", id);
    jdbc.update("DELETE FROM ilvy_place_index WHERE place_id=?", id);
    String text =
        Arrays.asList("name", "alias", "province", "city", "district", "type", "themes").stream()
            .map(k -> p.path(k).toString())
            .collect(Collectors.joining(" "))
            .toLowerCase(Locale.ROOT);
    boolean stroller = false;
    for (JsonNode f : p.path("facilities"))
      if (f.path("label").asText().equals("推车通行") && f.path("state").asText().equals("有"))
        stroller = true;
    jdbc.update(
        "INSERT INTO"
            + " ilvy_place_index(place_id,payload_hash,province,city,district,grade_label,search_text,drive_minutes,fee_amount,age_min,age_max,stay_hours,booking_needed,stroller)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        sha,
        exact(p.path("province").asText()),
        exact(p.path("city").asText()),
        exact(p.path("district").asText()),
        exact(p.path("level").asText()),
        text,
        number(p, "driveMinutes"),
        number(p, "feeAmount"),
        number(p, "ageMin"),
        number(p, "ageMax"),
        number(p, "stayHours"),
        p.path("booking").path("needed").asBoolean(false),
        stroller);
    for (String kind : Arrays.asList("type", "theme")) {
      Set<String> tags = new LinkedHashSet<>();
      if (kind.equals("type")) tags.add(p.path("type").asText());
      for (JsonNode tag : p.path(kind.equals("type") ? "types" : "themes")) tags.add(tag.asText());
      tags.remove("");
      for (String tag : tags)
        jdbc.update(
            "INSERT INTO ilvy_place_tag(place_id,kind,tag_value) VALUES (?,?,?)",
            id,
            kind,
            exact(tag));
    }
    return true;
  }

  // Called only by an explicitly enabled local development initializer. Derived index, no external
  // fetch.
  @Transactional
  public void rebuild() {
    jdbc.queryForObject(
        "SELECT revision FROM ilvy_catalog_version WHERE id=1 FOR UPDATE", Long.class);
    String last = "";
    boolean changed = false;
    while (true) {
      List<JsonNode> batch =
          jdbc.query(
              "SELECT payload FROM ilvy_place WHERE id>? ORDER BY id LIMIT 200",
              (r, n) -> repo.read(r.getString(1), JsonNode.class),
              last);
      if (batch.isEmpty()) break;
      for (JsonNode p : batch) {
        changed = index(p) || changed;
        last = p.path("id").asText();
      }
    }
    if (changed) jdbc.update("UPDATE ilvy_catalog_version SET revision=revision+1 WHERE id=1");
  }

  // Internal content write contract: source, projection and version share one transaction.
  @Transactional
  public void put(JsonNode place) {
    jdbc.queryForObject(
        "SELECT revision FROM ilvy_catalog_version WHERE id=1 FOR UPDATE", Long.class);
    String id = place.path("id").asText();
    if (id.isEmpty() || id.length() > 80) throw new IllegalArgumentException("Invalid place ID");
    if (jdbc.update("UPDATE ilvy_place SET payload=? WHERE id=?", place.toString(), id) == 0)
      jdbc.update("INSERT INTO ilvy_place(id,payload) VALUES (?,?)", id, place.toString());
    if (index(place)) jdbc.update("UPDATE ilvy_catalog_version SET revision=revision+1 WHERE id=1");
  }

  @Transactional
  public void remove(String id) {
    jdbc.queryForObject(
        "SELECT revision FROM ilvy_catalog_version WHERE id=1 FOR UPDATE", Long.class);
    if (jdbc.update("DELETE FROM ilvy_place WHERE id=?", id) > 0)
      jdbc.update("UPDATE ilvy_catalog_version SET revision=revision+1 WHERE id=1");
  }

  private List<String> facets(String one, List<String> many) {
    Set<String> set = new LinkedHashSet<>(many);
    if (one != null && !one.isEmpty()) set.add(one);
    return new ArrayList<>(set);
  }

  private void in(
      StringBuilder where,
      List<Object> args,
      String expression,
      List<String> values,
      boolean binary) {
    if (values.isEmpty()) return;
    where
        .append(" AND ")
        .append(expression)
        .append(" IN (")
        .append(String.join(",", Collections.nCopies(values.size(), "?")))
        .append(")");
    for (String v : values) args.add(binary ? exact(v) : v);
  }

  private void tags(StringBuilder where, List<Object> args, String kind, List<String> values) {
    if (values.isEmpty()) return;
    where.append(
        " AND EXISTS (SELECT 1 FROM ilvy_place_tag t WHERE t.place_id=i.place_id AND t.kind=?");
    args.add(kind);
    in(where, args, "t.tag_value", values, true);
    where.append(")");
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Map<String, Object> page(PageQuery q, Long user) {
    long version =
        jdbc.queryForObject("SELECT revision FROM ilvy_catalog_version WHERE id=1", Long.class);
    if (q.getPageNo() > 1 && q.getCatalogVersion() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "继续翻页需要目录版本");
    if (q.getCatalogVersion() != null && q.getCatalogVersion() != version)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "目录已变化，请刷新后继续浏览");
    List<Object> args = new ArrayList<>();
    StringBuilder w = new StringBuilder(" WHERE 1=1");
    for (String[] pair :
        new String[][] {
          {"province", q.getProvince()}, {"city", q.getCity()}, {"district", q.getDistrict()}
        })
      if (pair[1] != null && !pair[1].isEmpty()) {
        w.append(" AND i.").append(pair[0]).append("=?");
        args.add(exact(pair[1]));
      }
    tags(w, args, "type", facets(q.getType(), q.getTypes()));
    tags(w, args, "theme", facets(q.getTheme(), q.getThemes()));
    in(w, args, "i.grade_label", facets(q.getLevel(), q.getLevels()), true);
    if (q.getQ() != null && !q.getQ().trim().isEmpty()) {
      boolean mysql =
          Boolean.TRUE.equals(
              jdbc.execute(
                  (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                      c -> "MySQL".equalsIgnoreCase(c.getMetaData().getDatabaseProductName())));
      w.append(
          mysql
              ? " AND BINARY i.search_text LIKE BINARY ? ESCAPE '!'"
              : " AND i.search_text LIKE ? ESCAPE '!'");
      args.add(
          "%"
              + q.getQ()
                  .trim()
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
    }
    if (q.getMaxDrive() != null) {
      w.append(" AND i.drive_minutes<=?");
      args.add(q.getMaxDrive());
    }
    if (Boolean.TRUE.equals(q.getFree())) w.append(" AND i.fee_amount=0");
    if (q.getBudget() != null) {
      w.append(" AND i.fee_amount<=?");
      args.add(q.getBudget());
    }
    if (q.getAge() != null) {
      w.append(" AND i.age_min<=? AND i.age_max>=?");
      args.add(q.getAge());
      args.add(q.getAge());
    }
    if (q.getMaxStay() != null) {
      w.append(" AND i.stay_hours<=?");
      args.add(q.getMaxStay());
    }
    if (Boolean.TRUE.equals(q.getBooking())) w.append(" AND i.booking_needed=TRUE");
    if (Boolean.TRUE.equals(q.getStroller())) w.append(" AND i.stroller=TRUE");
    Long userVersion = null;
    if (Boolean.TRUE.equals(q.getFavoriteOnly())) {
      State state = repo.state(user);
      userVersion = state.getVersion();
      if (q.getPageNo() > 1 && q.getUserVersion() == null)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收藏翻页需要用户版本");
      if (q.getUserVersion() != null && !q.getUserVersion().equals(userVersion))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "收藏已变化，请刷新后继续浏览");
      List<String> ids =
          state.getFavorites().stream().map(Favorite::getPlaceId).collect(Collectors.toList());
      if (ids.isEmpty()) w.append(" AND 1=0");
      else in(w, args, "i.place_id", ids, false);
    }
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ilvy_place_index i" + w, Long.class, args.toArray());
    List<Object> paging = new ArrayList<>(args);
    paging.add(q.getPageSize());
    paging.add((q.getPageNo() - 1) * q.getPageSize());
    List<JsonNode> list =
        jdbc.query(
            "SELECT p.payload FROM ilvy_place_index i JOIN ilvy_place p ON p.id=i.place_id"
                + w
                + " ORDER BY i.place_id ASC LIMIT ? OFFSET ?",
            (r, n) -> repo.read(r.getString(1), JsonNode.class),
            paging.toArray());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", total);
    result.put("pageNo", q.getPageNo());
    result.put("pageSize", q.getPageSize());
    result.put("catalogVersion", version);
    result.put("userVersion", userVersion);
    return result;
  }
}
