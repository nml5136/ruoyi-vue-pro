package cn.iocoder.yudao.module.ilvy;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.*;
import cn.iocoder.yudao.module.ilvy.local.IlvyLocalApplication;
import cn.iocoder.yudao.module.ilvy.service.IlvyService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    classes = IlvyLocalApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ilvy-owner;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "logging.level.root=ERROR"
    })
@ActiveProfiles("ilvy-local")
class IlvyTenantTest {
  @Autowired IlvyService service;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void login(long tenant, int type) {
    SecurityFrameworkUtils.setLoginUser(
        new LoginUser().setId(999L).setUserType(type).setTenantId(tenant),
        new org.springframework.mock.web.MockHttpServletRequest());
  }

  @Test
  void sameUserIdInDifferentTenantsDoesNotShareStateOrFeedback() {
    login(71, 1);
    State s = new State();
    s.setPlanName("租户71");
    service.save(999, s);
    Feedback f = new Feedback();
    f.setPlaceId("forest-park");
    f.setCategory("设施");
    f.setDescription("仅租户71可见");
    service.feedback(999, f);
    login(72, 1);
    assertEquals(0, service.state(999).getVersion());
    assertTrue(service.feedback(999).isEmpty());
    State b = new State();
    b.setPlanName("租户72");
    service.save(999, b);
    login(71, 1);
    assertEquals("租户71", service.state(999).getPlanName());
    assertEquals(1, service.feedback(999).size());
  }

  @Test
  void adminIdentityCannotReadMemberData() {
    login(0, 2);
    assertThrows(ResponseStatusException.class, () -> service.state(999));
  }
}
