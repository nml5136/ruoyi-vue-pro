package cn.iocoder.yudao.module.ilvy.local;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Reject remote datasource overrides before Flyway or JDBC beans are initialized. */
@Configuration
@Profile("ilvy-local")
public class LocalDatabaseGuard {
  @Bean
  static BeanFactoryPostProcessor isolatedDatabaseGuard(Environment env) {
    return beans -> {
      String url = env.getProperty("spring.datasource.url", "");
      if (!(url.startsWith("jdbc:h2:file:./.local/") || url.startsWith("jdbc:h2:mem:ilvy")))
        throw new IllegalStateException(
            "ilvy-local permits only the isolated .local H2 file or ilvy test memory database");
      if (!"127.0.0.1".equals(env.getProperty("server.address")))
        throw new IllegalStateException("ilvy-local must bind to loopback");
    };
  }
}
