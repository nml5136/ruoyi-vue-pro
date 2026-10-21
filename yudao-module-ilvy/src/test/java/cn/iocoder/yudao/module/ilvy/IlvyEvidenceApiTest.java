package cn.iocoder.yudao.module.ilvy;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.module.ilvy.local.IlvyLocalApplication;
import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.util.*;

@SpringBootTest(
    classes = IlvyLocalApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ilvy-evidence;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.servlet.multipart.max-file-size=3MB",
      "ilvy.evidence-purge-enabled=true",
      "spring.servlet.multipart.max-request-size=4MB",
      "logging.level.root=ERROR"
    })
@ActiveProfiles("ilvy-local")
class IlvyEvidenceApiTest {
  static final Path DIRECTORY;

  static {
    try {
      DIRECTORY = Files.createTempDirectory("ilvy-evidence-test-");
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void evidence(DynamicPropertyRegistry r) {
    r.add("ilvy.evidence-directory", () -> DIRECTORY.toString());
  }

  @Autowired TestRestTemplate rest;
  @Autowired ObjectMapper mapper;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired cn.iocoder.yudao.module.ilvy.service.IlvyEvidenceService evidenceService;

  @BeforeEach
  void client() {
    org.springframework.http.client.SimpleClientHttpRequestFactory f =
        new org.springframework.http.client.SimpleClientHttpRequestFactory();
    f.setOutputStreaming(false);
    rest.getRestTemplate().setRequestFactory(f);
  }

  String session() {
    return rest.postForObject("/app-api/ilvy/local-session", null, JsonNode.class)
        .path("data")
        .path("token")
        .asText();
  }

  HttpHeaders headers(String token) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) h.setBearerAuth(token);
    return h;
  }

  ResponseEntity<JsonNode> json(String path, HttpMethod method, String token, Object body) {
    return rest.exchange(
        "/app-api/ilvy" + path, method, new HttpEntity<>(body, headers(token)), JsonNode.class);
  }

  byte[] png() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  ResponseEntity<JsonNode> upload(String token, byte[] bytes) {
    HttpHeaders h = headers(token);
    h.setContentType(MediaType.MULTIPART_FORM_DATA);
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add(
        "file",
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return "evidence.png";
          }
        });
    return rest.exchange(
        "/app-api/ilvy/feedback/evidence",
        HttpMethod.POST,
        new HttpEntity<>(form, h),
        JsonNode.class);
  }

  @Test
  void imageBytesAndMetadataRemainPrivateToOwner() throws Exception {
    String a = session(), b = session();
    byte[] image = png();
    ResponseEntity<JsonNode> uploaded = upload(a, image);
    assertEquals(200, uploaded.getStatusCodeValue());
    JsonNode info = uploaded.getBody().path("data");
    String id = info.path("id").asText();
    assertFalse(info.has("filePath"));
    assertEquals(image.length, info.path("size").asLong());
    ResponseEntity<byte[]> download =
        rest.exchange(
            "/app-api/ilvy/feedback/evidence/" + id,
            HttpMethod.GET,
            new HttpEntity<>(headers(a)),
            byte[].class);
    assertArrayEquals(image, download.getBody());
    assertEquals("no-store", download.getHeaders().getCacheControl());
    assertEquals("image/png", download.getHeaders().getContentType().toString());
    assertEquals(
        404,
        json("/feedback/evidence/" + id + "/info", HttpMethod.GET, b, null).getStatusCodeValue());
    assertEquals(
        401,
        json("/feedback/evidence/" + id + "/info", HttpMethod.GET, null, null)
            .getStatusCodeValue());
  }

  @Test
  void rejectsDisguisedFilesAndOversizeImages() throws Exception {
    String a = session();
    assertEquals(
        400,
        upload(a, "<svg><script/></svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .getStatusCodeValue());
    assertEquals(413, upload(a, new byte[2 * 1024 * 1024 + 1]).getStatusCodeValue());
  }

  @Test
  void feedbackAssociationIsTransactionalAndDeletionOnlyWorksBeforeSubmission() throws Exception {
    String a = session(), b = session();
    String id = upload(a, png()).getBody().path("data").path("id").asText();
    java.util.Map<String, Object> feedback = new java.util.LinkedHashMap<>();
    feedback.put("placeId", "forest-park");
    feedback.put("category", "设施");
    feedback.put("description", "带私有证据的反馈");
    feedback.put("evidenceIds", java.util.Collections.singletonList(id));
    assertEquals(400, json("/feedback", HttpMethod.POST, b, feedback).getStatusCodeValue());
    assertEquals(0, json("/feedback", HttpMethod.GET, b, null).getBody().path("data").size());
    assertEquals(200, json("/feedback", HttpMethod.POST, a, feedback).getStatusCodeValue());
    assertEquals(
        id,
        json("/feedback", HttpMethod.GET, a, null)
            .getBody()
            .path("data")
            .get(0)
            .path("evidence_ids")
            .get(0)
            .asText());
    assertEquals(
        409, json("/feedback/evidence/" + id, HttpMethod.DELETE, a, null).getStatusCodeValue());
    assertEquals(400, json("/feedback", HttpMethod.POST, a, feedback).getStatusCodeValue());
    assertEquals(1, json("/feedback", HttpMethod.GET, a, null).getBody().path("data").size());
    String unused = upload(a, png()).getBody().path("data").path("id").asText();
    assertEquals(
        200, json("/feedback/evidence/" + unused, HttpMethod.DELETE, a, null).getStatusCodeValue());
    assertEquals(
        404,
        json("/feedback/evidence/" + unused + "/info", HttpMethod.GET, a, null)
            .getStatusCodeValue());
    try (java.util.stream.Stream<Path> paths = Files.walk(DIRECTORY)) {
      assertTrue(paths.anyMatch(p -> p.getFileName().toString().startsWith(unused)));
    }
  }

  @Test
  void meUsesAuthenticatedTenantAndMemberContext() {
    String a = session(), b = session();
    assertEquals(401, json("/me", HttpMethod.GET, null, null).getStatusCodeValue());
    String first =
        json("/me", HttpMethod.GET, a, null).getBody().path("data").path("ownerKey").asText();
    String second =
        json("/me", HttpMethod.GET, b, null).getBody().path("data").path("ownerKey").asText();
    assertTrue(first.startsWith("0:member:"));
    assertNotEquals(first, second);
  }

  String image(String token) throws Exception {
    return upload(token, png()).getBody().path("data").path("id").asText();
  }

  void age(String id, String column) {
    jdbc.update(
        "UPDATE ilvy_feedback_evidence SET " + column + "=? WHERE id=?",
        java.sql.Timestamp.from(
            java.time.Instant.now().minus(8, java.time.temporal.ChronoUnit.DAYS)),
        id);
  }

  Path imagePath(String id) {
    return Paths.get(
        jdbc.queryForObject(
            "SELECT file_path FROM ilvy_feedback_evidence WHERE id=?", String.class, id));
  }

  @Test
  void expiredPreviewAndDiscardAreOwnedAndIdempotent() throws Exception {
    String a = session(), b = session(), id = image(a), fresh = image(a);
    age(id, "created_at");
    assertEquals(
        1,
        json("/feedback/evidence/expired", HttpMethod.GET, a, null).getBody().path("data").size());
    assertEquals(
        0,
        json("/feedback/evidence/expired", HttpMethod.GET, b, null).getBody().path("data").size());
    assertEquals(
        404,
        json("/feedback/evidence/expired/discard", HttpMethod.POST, b, java.util.Arrays.asList(id))
            .getStatusCodeValue());
    assertEquals(
        409,
        json(
                "/feedback/evidence/expired/discard",
                HttpMethod.POST,
                a,
                java.util.Arrays.asList(fresh))
            .getStatusCodeValue());
    assertEquals(
        200,
        json("/feedback/evidence/expired/discard", HttpMethod.POST, a, java.util.Arrays.asList(id))
            .getStatusCodeValue());
    assertEquals(
        200,
        json("/feedback/evidence/expired/discard", HttpMethod.POST, a, java.util.Arrays.asList(id))
            .getStatusCodeValue());
    assertTrue(Files.exists(imagePath(id)));
    assertEquals(
        404,
        json("/feedback/evidence/" + id + "/info", HttpMethod.GET, a, null).getStatusCodeValue());
    assertEquals(
        200,
        json("/feedback/evidence/" + fresh + "/info", HttpMethod.GET, a, null)
            .getStatusCodeValue());
  }

  @Test
  void referenceDetachIsPrivateAndRetryableWithoutDeletingFeedback() throws Exception {
    String a = session(), b = session(), id = image(a);
    java.util.Map<String, Object> body = new java.util.HashMap<>();
    body.put("placeId", "forest-park");
    body.put("category", "设施");
    body.put("description", "保留文字的附件删除");
    body.put("evidenceIds", java.util.Arrays.asList(id));
    json("/feedback", HttpMethod.POST, a, body);
    String feedback =
        json("/feedback", HttpMethod.GET, a, null)
            .getBody()
            .path("data")
            .get(0)
            .path("id")
            .asText();
    String path = "/feedback/evidence/" + id + "/feedback/" + feedback;
    assertEquals(404, json(path, HttpMethod.DELETE, b, null).getStatusCodeValue());
    assertEquals(200, json(path, HttpMethod.DELETE, a, null).getStatusCodeValue());
    assertEquals(200, json(path, HttpMethod.DELETE, a, null).getStatusCodeValue());
    JsonNode row = json("/feedback", HttpMethod.GET, a, null).getBody().path("data").get(0);
    assertEquals("保留文字的附件删除", row.path("description").asText());
    assertEquals(0, row.path("evidence_ids").size());
    assertTrue(Files.exists(imagePath(id)));
    assertEquals(400, json("/feedback", HttpMethod.POST, a, body).getStatusCodeValue());
  }

  @Test
  void retentionIsPreviewFirstAndHonorsSevenDayDelay() throws Exception {
    String a = session(), id = image(a);
    Path path = imagePath(id);
    json("/feedback/evidence/" + id, HttpMethod.DELETE, a, null);
    assertTrue(
        evidenceService.retention(false, 50).stream().noneMatch(r -> r.get("id").equals(id)));
    age(id, "deleted_at");
    assertTrue(evidenceService.retention(false, 50).stream().anyMatch(r -> r.get("id").equals(id)));
    assertTrue(Files.exists(path));
    org.springframework.test.util.ReflectionTestUtils.setField(
        evidenceService, "purgeEnabled", false);
    try {
      assertThrows(
          org.springframework.web.server.ResponseStatusException.class,
          () -> evidenceService.retention(true, 50));
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          evidenceService, "purgeEnabled", true);
    }
    assertTrue(
        evidenceService.retention(true, 50).stream()
            .anyMatch(r -> r.get("id").equals(id) && r.get("status").equals("purged")));
    assertFalse(Files.exists(path));
    assertTrue(evidenceService.retention(true, 50).stream().noneMatch(r -> r.get("id").equals(id)));
  }

  @Test
  void failedPhysicalRemovalRemainsRetryable() throws Exception {
    String a = session(), id = image(a);
    Path path = imagePath(id);
    json("/feedback/evidence/" + id, HttpMethod.DELETE, a, null);
    age(id, "deleted_at");
    Files.delete(path);
    Files.createDirectory(path);
    Path child = path.resolve("test-only-blocker");
    Files.write(child, new byte[] {1});
    assertTrue(
        evidenceService.retention(true, 50).stream()
            .anyMatch(r -> r.get("id").equals(id) && r.get("status").equals("retry")));
    assertTrue(Files.exists(child));
    assertNull(
        jdbc.queryForObject(
            "SELECT purged_at FROM ilvy_feedback_evidence WHERE id=?", Object.class, id));
    Files.delete(child);
    Files.delete(path);
    Files.write(path, png());
    assertTrue(
        evidenceService.retention(true, 50).stream()
            .anyMatch(r -> r.get("id").equals(id) && r.get("status").equals("purged")));
    assertFalse(Files.exists(path));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT purge_attempts FROM ilvy_feedback_evidence WHERE id=?", Integer.class, id));
  }
}
