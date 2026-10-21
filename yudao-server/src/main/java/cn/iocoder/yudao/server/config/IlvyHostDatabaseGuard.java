package cn.iocoder.yudao.server.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Runs before datasource initialization; the development host cannot target a remote environment.
 */
@Configuration
@Profile("ilvy-host")
public class IlvyHostDatabaseGuard {
  @Bean
  static BeanFactoryPostProcessor ilvyHostIsolationGuard(Environment env) {
    return beans -> {
      String jdbc = env.getProperty("spring.datasource.dynamic.datasource.master.url", "");
      if (!"jdbc:mysql://127.0.0.1:43306/ilvy_dev".equals(jdbc.split("\\?", 2)[0]))
        throw new IllegalStateException(
            "ilvy-host requires the isolated loopback MySQL development database");
      if (!"127.0.0.1".equals(env.getProperty("spring.redis.host"))
          || !"46379".equals(env.getProperty("spring.redis.port")))
        throw new IllegalStateException(
            "ilvy-host requires the isolated loopback Redis development instance");
      if (!"127.0.0.1".equals(env.getProperty("server.address"))
          || !"48082".equals(env.getProperty("server.port")))
        throw new IllegalStateException(
            "ilvy-host must bind only to its loopback development port");
      if (!"false".equals(env.getProperty("yudao.security.mock-enable")))
        throw new IllegalStateException(
            "ilvy-host must use real authentication; mock login is forbidden");
    };
  }
}
