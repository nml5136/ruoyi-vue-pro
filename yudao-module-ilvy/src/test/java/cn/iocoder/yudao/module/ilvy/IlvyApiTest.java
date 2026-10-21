package cn.iocoder.yudao.module.ilvy;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.ilvy.local.IlvyLocalApplication;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
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
      "spring.datasource.url=jdbc:h2:mem:ilvy-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "logging.level.root=ERROR"
    })
@ActiveProfiles("ilvy-local")
class IlvyApiTest {
  @org.springframework.boot.test.web.server.LocalServerPort int port;
  @Autowired TestRestTemplate rest;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void nonStreamingClient() {
    org.springframework.http.client.SimpleClientHttpRequestFactory f =
        new org.springframework.http.client.SimpleClientHttpRequestFactory();
    f.setOutputStreaming(false);
    rest.getRestTemplate().setRequestFactory(f);
  }

  private String session() {
    return rest.postForObject("/app-api/ilvy/local-session", null, JsonNode.class)
        .path("data")
        .path("token")
        .asText();
  }

  private ResponseEntity<JsonNode> call(String path, HttpMethod method, String token, Object data) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) h.setBearerAuth(token);
    return rest.exchange("/app-api/ilvy" + path, method, new HttpEntity<>(data, h), JsonNode.class);
  }

  private ObjectNode state(String token) {
    return (ObjectNode) call("/state", HttpMethod.GET, token, null).getBody().path("data");
  }

  @Test
  void anonymousBrowsingAndAuthentication() {
    assertEquals(200, call("/places", HttpMethod.GET, null, null).getStatusCodeValue());
    assertEquals(401, call("/state", HttpMethod.GET, null, null).getStatusCodeValue());
    assertEquals(
        401, call("/state", HttpMethod.PUT, null, mapper.createObjectNode()).getStatusCodeValue());
    assertEquals(401, call("/state", HttpMethod.GET, "invalid", null).getStatusCodeValue());
  }

  @Test
  void userIsolationPersistenceAndConflict() {
    String a = session(), b = session();
    ObjectNode s = state(a);
    ((ArrayNode) s.path("favorites"))
        .addObject()
        .put("placeId", "forest-park")
        .put("group", "亲子周末");
    s.put("planName", "API 验收");
    s.put("planDate", "2026-10-25");
    ((ArrayNode) s.path("plan")).addObject().put("placeId", "forest-park").put("note", "休息");
    assertEquals(200, call("/state", HttpMethod.PUT, a, s).getStatusCodeValue());
    assertEquals(1, state(a).path("version").asInt());
    assertEquals("亲子周末", state(a).path("favorites").get(0).path("group").asText());
    assertEquals(0, state(b).path("favorites").size());
    assertEquals(409, call("/state", HttpMethod.PUT, a, s).getStatusCodeValue());
    assertEquals(0, state(b).path("version").asInt());
  }

  @Test
  void rejectsDuplicatesInvalidDatesReferencesAndForgedOwner() {
    String token = session();
    ObjectNode s = state(token);
    s.put("planDate", "2026-02-30");
    assertEquals(400, call("/state", HttpMethod.PUT, token, s).getStatusCodeValue());
    s.put("planDate", "");
    ArrayNode items = (ArrayNode) s.path("plan");
    items.addObject().put("placeId", "forest-park").put("note", "");
    items.addObject().put("placeId", "forest-park").put("note", "");
    assertEquals(400, call("/state", HttpMethod.PUT, token, s).getStatusCodeValue());
    items.removeAll();
    items.addObject().put("placeId", "missing").put("note", "");
    assertEquals(404, call("/state", HttpMethod.PUT, token, s).getStatusCodeValue());
    items.removeAll();
    s.put("userId", 123);
    assertEquals(400, call("/state", HttpMethod.PUT, token, s).getStatusCodeValue());
    assertEquals(0, state(token).path("version").asInt());
  }

  @Test
  void feedbackRecordsArePrivateAndValidated() {
    String a = session(), b = session();
    ObjectNode f =
        mapper
            .createObjectNode()
            .put("placeId", "forest-park")
            .put("category", "设施")
            .put("description", "需要核实推车通行范围");
    assertEquals(200, call("/feedback", HttpMethod.POST, a, f).getStatusCodeValue());
    assertEquals(1, call("/feedback", HttpMethod.GET, a, null).getBody().path("data").size());
    assertEquals(0, call("/feedback", HttpMethod.GET, b, null).getBody().path("data").size());
    f.put("description", "");
    assertEquals(400, call("/feedback", HttpMethod.POST, a, f).getStatusCodeValue());
  }

  private java.util.Map<String, String> preflight(String origin) throws Exception {
    try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
      socket.setSoTimeout(3000);
      java.io.Writer w =
          new java.io.OutputStreamWriter(
              socket.getOutputStream(), java.nio.charset.StandardCharsets.US_ASCII);
      w.write(
          "OPTIONS /app-api/ilvy/places HTTP/1.1\r\nHost: 127.0.0.1:"
              + port
              + "\r\nOrigin: "
              + origin
              + "\r\n"
              + "Access-Control-Request-Method: GET\r\n"
              + "Access-Control-Request-Headers: content-type\r\n"
              + "Connection: close\r\n\r\n");
      w.flush();
      java.io.BufferedReader reader =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(
                  socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
      java.util.Map<String, String> headers = new java.util.HashMap<>();
      headers.put("status", reader.readLine());
      String line;
      while ((line = reader.readLine()) != null && !line.isEmpty()) {
        int split = line.indexOf(':');
        if (split > 0)
          headers.put(line.substring(0, split).toLowerCase(), line.substring(split + 1).trim());
      }
      return headers;
    }
  }

  @Test
  void localCorsAllowsOnlyExplicitH5Origins() throws Exception {
    java.util.Map<String, String> good = preflight("http://127.0.0.1:10086");
    assertTrue(good.get("status").contains("200"));
    assertEquals("http://127.0.0.1:10086", good.get("access-control-allow-origin"));
    assertTrue(preflight("https://untrusted.example").get("status").contains("403"));
  }

  @Test
  void queryUnknownsAndLimits() {
    assertEquals(
        0, call("/places?maxDrive=60", HttpMethod.GET, null, null).getBody().path("data").size());
    assertEquals(
        1,
        call("/places?free=true&stroller=true", HttpMethod.GET, null, null)
            .getBody()
            .path("data")
            .size());
    assertEquals(
        0, call("/places?level=5A", HttpMethod.GET, null, null).getBody().path("data").size());
    assertEquals(400, call("/places?maxDrive=-1", HttpMethod.GET, null, null).getStatusCodeValue());
    assertEquals(404, call("/places/missing", HttpMethod.GET, null, null).getStatusCodeValue());
  }

  @Test
  void collectionsAreAggregatedAndUnknownFactsStayUnknown() {
    JsonNode data = call("/collections", HttpMethod.GET, null, null).getBody().path("data");
    java.util.Set<String> ids = new java.util.HashSet<>();
    for (JsonNode d : data.path("destinations")) ids.add(d.path("id").asText());
    int total = 0;
    for (JsonNode d : data.path("destinations")) {
      if (d.path("kind").asText().equals("province")) total += d.path("placeCount").asInt();
      if (!d.path("parentId").isNull()) assertTrue(ids.contains(d.path("parentId").asText()));
      assertTrue(d.path("sampleOnly").asBoolean());
      assertTrue(d.path("season").isNull());
      assertTrue(d.path("recommendedDays").isNull());
      assertEquals("pending", d.path("reviewStatus").asText());
      assertEquals(d.path("placeIds").size(), d.path("placeCount").asInt());
    }
    assertEquals(4, total);
    assertEquals(6, data.path("topics").size());
    for (JsonNode t : data.path("topics"))
      if (t.path("theme").asText().equals("夜游")) assertEquals(0, t.path("placeCount").asInt());
  }

  @Test
  void multipleQueryFacetsBindAndValidate() {
    assertEquals(
        2, call("/places?types=公园,营地", HttpMethod.GET, null, null).getBody().path("data").size());
    assertEquals(
        1,
        call("/places?types=公园,营地&themes=古建,亲子", HttpMethod.GET, null, null)
            .getBody()
            .path("data")
            .size());
    assertEquals(
        0, call("/places?levels=3A,4A", HttpMethod.GET, null, null).getBody().path("data").size());
    assertEquals(
        400,
        call("/places?types=a,b,c,d,e,f,g,h,i,j,k", HttpMethod.GET, null, null)
            .getStatusCodeValue());
  }

  @Test
  void favoriteOnlyScopeUsesAuthenticatedOwnerRatherThanClientIds() {
    assertEquals(
        401, call("/places?favoriteOnly=true", HttpMethod.GET, null, null).getStatusCodeValue());
    String a = session(), b = session();
    ObjectNode s = state(a);
    ((ArrayNode) s.path("favorites"))
        .addObject()
        .put("placeId", "forest-park")
        .put("group", "仅我想去");
    assertEquals(200, call("/state", HttpMethod.PUT, a, s).getStatusCodeValue());
    JsonNode mine =
        call("/places?favoriteOnly=true", HttpMethod.GET, a, null).getBody().path("data");
    assertEquals(1, mine.size());
    assertEquals("forest-park", mine.get(0).path("id").asText());
    assertEquals(
        0,
        call("/places?favoriteOnly=true", HttpMethod.GET, b, null).getBody().path("data").size());
  }
}
