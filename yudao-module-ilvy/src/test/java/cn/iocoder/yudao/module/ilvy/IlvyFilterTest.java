package cn.iocoder.yudao.module.ilvy;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.Query;
import cn.iocoder.yudao.module.ilvy.service.IlvyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IlvyFilterTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void missingFieldsNeverImplyFreeOrAccessibleOrShortDrive() throws Exception {
    JsonNode p = mapper.readTree("{}");
    Query q = new Query();
    assertTrue(IlvyService.matches(p, q));
    q.setFree(true);
    assertFalse(IlvyService.matches(p, q));
    q.setFree(null);
    q.setStroller(true);
    assertFalse(IlvyService.matches(p, q));
    q.setStroller(null);
    q.setMaxDrive(60);
    assertFalse(IlvyService.matches(p, q));
  }

  @Test
  void gradeIsIndependentOfPopularity() throws Exception {
    JsonNode p = mapper.readTree("{\"level\":\"待核实\",\"heat\":999,\"types\":[\"景区\"]}");
    Query q = new Query();
    q.setLevel("5A");
    assertFalse(IlvyService.matches(p, q));
  }

  @Test
  void keywordAndThemeAreCombined() throws Exception {
    JsonNode p = mapper.readTree("{\"name\":\"公园\",\"city\":\"广州\",\"themes\":[\"亲子\"]}");
    Query q = new Query();
    q.setQ("广州");
    q.setTheme("亲子");
    assertTrue(IlvyService.matches(p, q));
    q.setTheme("露营");
    assertFalse(IlvyService.matches(p, q));
  }
}
