package cn.iocoder.yudao.module.ilvy.dal;

import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.EvidenceRecord;
import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.State;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IlvyRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public IlvyRepository(JdbcTemplate j, ObjectMapper m) {
    jdbc = j;
    mapper = m;
  }

  private long tenant() {
    cn.iocoder.yudao.framework.security.core.LoginUser u =
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser();
    if (u == null || u.getTenantId() == null || !Integer.valueOf(1).equals(u.getUserType()))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "需要具备租户上下文的会员登录");
    return u.getTenantId();
  }

  public List<JsonNode> places() {
    return jdbc.query(
        "SELECT payload FROM ilvy_place ORDER BY id",
        (rs, n) -> read(rs.getString(1), JsonNode.class));
  }

  public JsonNode place(String id) {
    List<JsonNode> rows =
        jdbc.query(
            "SELECT payload FROM ilvy_place WHERE id=?",
            (r, n) -> read(r.getString(1), JsonNode.class),
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public State state(long user) {
    List<State> rows =
        jdbc.query(
            "SELECT payload FROM ilvy_user_state WHERE user_id=? AND tenant_id=? AND user_type=1",
            (rs, n) -> read(rs.getString(1), State.class),
            user,
            tenant());
    return rows.isEmpty() ? new State() : rows.get(0);
  }

  public int update(long user, State s, long expected) {
    return jdbc.update(
        "UPDATE ilvy_user_state SET payload=?,version=? WHERE user_id=? AND version=? AND"
            + " tenant_id=? AND user_type=1",
        write(s),
        s.getVersion(),
        user,
        expected,
        tenant());
  }

  public void insert(long user, State s) {
    jdbc.update(
        "INSERT INTO ilvy_user_state(user_id,version,payload,tenant_id,user_type) VALUES"
            + " (?,?,?,?,1)",
        user,
        s.getVersion(),
        write(s),
        tenant());
  }

  public void feedback(long user, String id, String place, String category, String description) {
    jdbc.update(
        "INSERT INTO"
            + " ilvy_feedback(id,user_id,place_id,category,description,status,tenant_id,user_type)"
            + " VALUES (?,?,?,?,?,?,?,1)",
        id,
        user,
        place,
        category,
        description,
        "待核实",
        tenant());
  }

  public List<Map<String, Object>> feedback(long user) {
    return lower(
        jdbc.queryForList(
            "SELECT id,place_id,category,description,status,created_at FROM ilvy_feedback WHERE"
                + " user_id=? AND tenant_id=? AND user_type=1 ORDER BY created_at DESC",
            user,
            tenant()));
  }

  public void assertOwner(long user) {
    cn.iocoder.yudao.framework.security.core.LoginUser u =
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser();
    if (u == null || !java.util.Objects.equals(u.getId(), user))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录");
    tenant();
  }

  public void addEvidence(long user, EvidenceRecord row, String hash) {
    jdbc.update(
        "INSERT INTO"
            + " ilvy_feedback_evidence(id,user_id,tenant_id,user_type,content_type,file_path,file_size,sha256)"
            + " VALUES (?,?,?,1,?,?,?,?)",
        row.getId(),
        user,
        tenant(),
        row.getContentType(),
        row.getFilePath(),
        row.getSize(),
        hash);
  }

  public EvidenceRecord evidence(long user, String id) {
    List<EvidenceRecord> rows =
        jdbc.query(
            "SELECT id,content_type,file_path,file_size,feedback_id,deleted_at FROM"
                + " ilvy_feedback_evidence WHERE id=? AND user_id=? AND tenant_id=? AND"
                + " user_type=1",
            (rs, n) ->
                new EvidenceRecord()
                    .setDeleted(rs.getTimestamp("deleted_at") != null)
                    .setId(rs.getString("id"))
                    .setContentType(rs.getString("content_type"))
                    .setFilePath(rs.getString("file_path"))
                    .setSize(rs.getLong("file_size"))
                    .setFeedbackId(rs.getString("feedback_id")),
            id,
            user,
            tenant());
    return rows.isEmpty() ? null : rows.get(0);
  }

  public int linkEvidence(long user, String id, String feedback) {
    return jdbc.update(
        "UPDATE ilvy_feedback_evidence SET feedback_id=? WHERE id=? AND user_id=? AND tenant_id=?"
            + " AND user_type=1 AND feedback_id IS NULL AND deleted_at IS NULL",
        feedback,
        id,
        user,
        tenant());
  }

  public int removeUnsubmittedEvidence(long user, String id) {
    return jdbc.update(
        "UPDATE ilvy_feedback_evidence SET deleted_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?"
            + " AND tenant_id=? AND user_type=1 AND feedback_id IS NULL AND deleted_at IS NULL",
        id,
        user,
        tenant());
  }

  public int detachEvidence(long user, String id, String feedback) {
    return jdbc.update(
        "UPDATE ilvy_feedback_evidence SET deleted_at=CURRENT_TIMESTAMP WHERE id=? AND"
            + " feedback_id=? AND user_id=? AND tenant_id=? AND user_type=1 AND deleted_at IS NULL",
        id,
        feedback,
        user,
        tenant());
  }

  public List<Map<String, Object>> expiredEvidence(long user) {
    return lower(
        jdbc.queryForList(
            "SELECT id,content_type,file_size,created_at FROM ilvy_feedback_evidence WHERE"
                + " user_id=? AND tenant_id=? AND user_type=1 AND feedback_id IS NULL AND"
                + " deleted_at IS NULL AND created_at<? ORDER BY created_at,id LIMIT 50",
            user,
            tenant(),
            java.sql.Timestamp.from(
                java.time.Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS))));
  }

  public int expireEvidence(long user, String id) {
    return jdbc.update(
        "UPDATE ilvy_feedback_evidence SET deleted_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?"
            + " AND tenant_id=? AND user_type=1 AND feedback_id IS NULL AND deleted_at IS NULL AND"
            + " created_at<?",
        id,
        user,
        tenant(),
        java.sql.Timestamp.from(
            java.time.Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS)));
  }

  public List<Map<String, Object>> purgeCandidates(int limit) {
    return jdbc.queryForList(
        "SELECT id,file_path FROM ilvy_feedback_evidence WHERE deleted_at<? AND purged_at IS NULL"
            + " ORDER BY deleted_at,id LIMIT ?",
        java.sql.Timestamp.from(
            java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS)),
        limit);
  }

  public void recordPurge(String id, String error) {
    if (error == null)
      jdbc.update(
          "UPDATE ilvy_feedback_evidence SET"
              + " purged_at=CURRENT_TIMESTAMP,purge_attempts=purge_attempts+1,last_purge_error=NULL"
              + " WHERE id=? AND purged_at IS NULL",
          id);
    else
      jdbc.update(
          "UPDATE ilvy_feedback_evidence SET purge_attempts=purge_attempts+1,last_purge_error=?"
              + " WHERE id=? AND purged_at IS NULL",
          error,
          id);
  }

  public List<String> evidenceIds(long user, String feedback) {
    return jdbc.query(
        "SELECT id FROM ilvy_feedback_evidence WHERE feedback_id=? AND user_id=? AND tenant_id=?"
            + " AND user_type=1 AND deleted_at IS NULL ORDER BY created_at,id",
        (rs, n) -> rs.getString(1),
        feedback,
        user,
        tenant());
  }

  private List<Map<String, Object>> lower(List<Map<String, Object>> rows) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      Map<String, Object> normalized = new LinkedHashMap<>();
      row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
      result.add(normalized);
    }
    return result;
  }

  public <T> T read(String s, Class<T> type) {
    try {
      return mapper.readValue(s, type);
    } catch (Exception e) {
      throw new IllegalStateException("存储数据格式错误", e);
    }
  }

  public String write(Object o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
