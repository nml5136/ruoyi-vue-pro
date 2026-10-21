package cn.iocoder.yudao.module.ilvy.local;

import com.fasterxml.jackson.databind.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@org.springframework.core.annotation.Order(10)
@Component
@Profile("ilvy-local")
public class LocalCatalog implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public LocalCatalog(JdbcTemplate j, ObjectMapper m) {
    jdbc = j;
    mapper = m;
  }

  public void run(ApplicationArguments args) throws Exception {
    // Never connects to the host application database; local launcher uses its own properties.
    try (java.sql.Connection c = jdbc.getDataSource().getConnection()) {
      if (!c.getMetaData().getURL().startsWith("jdbc:h2:"))
        throw new IllegalStateException("Local seeder requires isolated H2");
    }
    JsonNode rows = mapper.readTree(new ClassPathResource("local-examples.json").getInputStream());
    for (JsonNode row : rows) {
      String id = row.path("id").asText();
      java.util.List<String> old =
          jdbc.query("SELECT payload FROM ilvy_place WHERE id=?", (rs, n) -> rs.getString(1), id);
      if (old.isEmpty())
        jdbc.update("INSERT INTO ilvy_place(id,payload) VALUES (?,?)", id, row.toString());
      else if (mapper.readTree(old.get(0)).path("demo").asBoolean(false))
        jdbc.update("UPDATE ilvy_place SET payload=? WHERE id=?", row.toString(), id);
    }
  }
}
