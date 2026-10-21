package cn.iocoder.yudao.server.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class IlvyHostConfigurationTest {
  MockEnvironment local() {
    return new MockEnvironment()
        .withProperty(
            "spring.datasource.dynamic.datasource.master.url",
            "jdbc:mysql://127.0.0.1:43306/ilvy_dev?useSSL=false")
        .withProperty("spring.redis.host", "127.0.0.1")
        .withProperty("spring.redis.port", "46379")
        .withProperty("server.address", "127.0.0.1")
        .withProperty("server.port", "48082")
        .withProperty("yudao.security.mock-enable", "false");
  }

  void validate(MockEnvironment env) {
    IlvyHostDatabaseGuard.ilvyHostIsolationGuard(env)
        .postProcessBeanFactory(new DefaultListableBeanFactory());
  }

  @Test
  void acceptsOnlyTheDeclaredLocalDatabase() {
    assertDoesNotThrow(() -> validate(local()));
  }

  @Test
  void rejectsRemoteDatabaseBeforeAnyConnection() {
    assertThrows(
        IllegalStateException.class,
        () ->
            validate(
                local()
                    .withProperty(
                        "spring.datasource.dynamic.datasource.master.url",
                        "jdbc:mysql://production.example/database")));
  }

  @Test
  void rejectsRemoteRedisOrMockIdentity() {
    assertThrows(
        IllegalStateException.class,
        () -> validate(local().withProperty("spring.redis.host", "production.example")));
    assertThrows(
        IllegalStateException.class,
        () -> validate(local().withProperty("yudao.security.mock-enable", "true")));
  }

  @Test
  void unavailableWechatNeverReturnsFakeIdentityOrCallsNetwork() {
    IlvyUnavailableWechatConfiguration config = new IlvyUnavailableWechatConfiguration();
    assertThrows(IllegalStateException.class, () -> config.wxMpService().getAccessToken());
    assertThrows(IllegalStateException.class, () -> config.wxMaService().getAccessToken());
  }
}
