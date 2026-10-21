package cn.iocoder.yudao.module.ilvy.dal;

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
    return jdbc.queryForList(
        "SELECT id,place_id,category,description,status,created_at FROM ilvy_feedback WHERE"
            + " user_id=? AND tenant_id=? AND user_type=1 ORDER BY created_at DESC",
        user,
        tenant());
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
