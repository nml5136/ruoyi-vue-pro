package cn.iocoder.yudao.module.ilvy;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.ilvy.dal.IlvyCatalogRepository;
import cn.iocoder.yudao.module.ilvy.local.IlvyLocalApplication;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = IlvyLocalApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ilvy-page;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "logging.level.root=ERROR"
    })
@ActiveProfiles("ilvy-local")
class IlvyCatalogPageTest {
  @Autowired TestRestTemplate rest;
  @Autowired IlvyCatalogRepository catalog;
  @Autowired ObjectMapper mapper;
  final List<String> created = new ArrayList<>();

  @BeforeEach
  void client() {
    org.springframework.http.client.SimpleClientHttpRequestFactory f =
        new org.springframework.http.client.SimpleClientHttpRequestFactory();
    f.setOutputStreaming(false);
    rest.getRestTemplate().setRequestFactory(f);
  }

  @AfterEach
  void cleanup() {
    for (String id : created) catalog.remove(id);
    created.clear();
  }

  ResponseEntity<JsonNode> call(String path, HttpMethod method, String token, Object body) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) h.setBearerAuth(token);
    return rest.exchange("/app-api/ilvy" + path, method, new HttpEntity<>(body, h), JsonNode.class);
  }

  JsonNode get(String path) {
    return call(path, HttpMethod.GET, null, null).getBody().path("data");
  }

  String session() {
    return rest.postForObject("/app-api/ilvy/local-session", null, JsonNode.class)
        .path("data")
        .path("token")
        .asText();
  }

  List<String> ids(JsonNode rows) {
    List<String> ids = new ArrayList<>();
    for (JsonNode r : rows) ids.add(r.path("id").asText());
    return ids;
  }

  ObjectNode fixture(String id) {
    ObjectNode p = (ObjectNode) get("/places/forest-park").deepCopy();
    p.put("id", id);
    p.put("name", "分页测试样本 " + id);
    p.put("demo", true);
    return p;
  }

  @Test
  void stableOrderingNoDuplicatesAndFinalEmptyPage() {
    for (int i = 0; i < 61; i++) {
      String id = String.format("paging-test-%03d", i);
      catalog.put(fixture(id));
      created.add(id);
    }
    List<String> all = new ArrayList<>();
    long version = get("/places/page?pageSize=10").path("catalogVersion").asLong();
    for (int page = 1; page <= 7; page++)
      all.addAll(
          ids(
              get("/places/page?pageNo=" + page + "&pageSize=10&catalogVersion=" + version)
                  .path("list")));
    assertEquals(65, all.size());
    assertEquals(all.size(), new HashSet<>(all).size());
    List<String> sorted = new ArrayList<>(all);
    Collections.sort(sorted);
    assertEquals(sorted, all);
    JsonNode empty = get("/places/page?pageNo=8&pageSize=10&catalogVersion=" + version);
    assertEquals(0, empty.path("list").size());
    assertEquals(65, empty.path("total").asInt());
  }

  @Test
  void allFacetsAndKnownOnlyConditionsMatchLegacySearch() {
    for (String query :
        Arrays.asList(
            "",
            "q=广州",
            "province=广东&city=广州&district=天河区",
            "types=公园,营地",
            "themes=亲子,露营",
            "levels=待核实",
            "free=true",
            "budget=0",
            "age=3",
            "maxDrive=60",
            "maxStay=3",
            "booking=true",
            "stroller=true",
            "q=%",
            "q=_",
            "q=' OR 1=1",
            "themes=露营&free=true&stroller=true")) {
      JsonNode legacy = get("/places?" + query), page = get("/places/page?pageSize=50&" + query);
      assertEquals(ids(legacy), ids(page.path("list")), query);
      assertEquals(legacy.size(), page.path("total").asInt(), query);
    }
  }

  @Test
  void invalidBoundsAndUnversionedContinuationAreRejected() {
    for (String q : Arrays.asList("pageSize=0", "pageSize=51", "pageNo=0", "pageNo=2"))
      assertEquals(400, call("/places/page?" + q, HttpMethod.GET, null, null).getStatusCodeValue());
    assertEquals(0, get("/places/page?q=不存在的名称").path("total").asInt());
  }

  @Test
  void insertUpdateAndDeleteInvalidateContinuation() {
    String id = "paging-test-concurrent";
    ObjectNode p = fixture(id);
    catalog.put(p);
    created.add(id);
    long version = get("/places/page?pageSize=1").path("catalogVersion").asLong();
    p.put("name", "目录变更后的测试样本");
    catalog.put(p);
    assertEquals(
        409,
        call(
                "/places/page?pageNo=2&pageSize=1&catalogVersion=" + version,
                HttpMethod.GET,
                null,
                null)
            .getStatusCodeValue());
    version = get("/places/page?pageSize=1").path("catalogVersion").asLong();
    catalog.remove(id);
    assertEquals(
        409,
        call(
                "/places/page?pageNo=2&pageSize=1&catalogVersion=" + version,
                HttpMethod.GET,
                null,
                null)
            .getStatusCodeValue());
    version = get("/places/page?pageSize=1").path("catalogVersion").asLong();
    catalog.put(p);
    assertEquals(
        409,
        call(
                "/places/page?pageNo=2&pageSize=1&catalogVersion=" + version,
                HttpMethod.GET,
                null,
                null)
            .getStatusCodeValue());
  }

  @Test
  void favoriteScopeAndUserVersionCannotBeReusedAcrossChanges() {
    assertEquals(
        401,
        call("/places/page?favoriteOnly=true", HttpMethod.GET, null, null).getStatusCodeValue());
    String a = session(), b = session();
    ObjectNode state = (ObjectNode) call("/state", HttpMethod.GET, a, null).getBody().path("data");
    state.set(
        "favorites",
        mapper.valueToTree(
            Arrays.asList(
                Collections.singletonMap("placeId", "forest-park"),
                Collections.singletonMap("placeId", "creek-camp"))));
    // Group is required by state validation.
    for (JsonNode f : state.path("favorites")) ((ObjectNode) f).put("group", "分页测试");
    assertEquals(200, call("/state", HttpMethod.PUT, a, state).getStatusCodeValue());
    JsonNode page =
        call("/places/page?favoriteOnly=true&pageSize=1", HttpMethod.GET, a, null)
            .getBody()
            .path("data");
    assertEquals(2, page.path("total").asInt());
    assertEquals(
        0,
        call("/places/page?favoriteOnly=true", HttpMethod.GET, b, null)
            .getBody()
            .path("data")
            .path("total")
            .asInt());
    ObjectNode changed =
        (ObjectNode) call("/state", HttpMethod.GET, a, null).getBody().path("data");
    changed.set("favorites", mapper.createArrayNode());
    call("/state", HttpMethod.PUT, a, changed);
    assertEquals(
        409,
        call(
                "/places/page?favoriteOnly=true&pageNo=2&pageSize=1&catalogVersion="
                    + page.path("catalogVersion").asLong()
                    + "&userVersion="
                    + page.path("userVersion").asLong(),
                HttpMethod.GET,
                a,
                null)
            .getStatusCodeValue());
  }

  @Test
  void fractionalNumbersDoNotBecomeFreeOrRoundIntoKnownBounds() {
    String id = "paging-test-fractional";
    ObjectNode p = fixture(id);
    p.put("feeAmount", 0.0004);
    p.put("stayHours", 3.0001);
    p.put("driveMinutes", 9.9);
    p.put("ageMin", 3.5);
    p.put("ageMax", 4.9);
    catalog.put(p);
    created.add(id);
    for (String q : Arrays.asList("free=true", "budget=0", "maxStay=3", "maxDrive=9", "age=3"))
      assertEquals(
          ids(get("/places?" + q)), ids(get("/places/page?pageSize=50&" + q).path("list")), q);
  }
}
