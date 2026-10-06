package com.github.cuixudong74.exthost;

import com.github.cuixudong74.exthost.model.ContributionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** REST 接口端到端：提交清单、查询生效状态、解释取值、非法清单被拒绝。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"host.api-version=1.2.0",
                "host.capabilities=commands,settings",
                "host.mismatch-policy=REJECT"})
class RestApiTest {

    @Autowired
    private TestRestTemplate rest;

    private Map<String, Object> manifest(String id, int priority, String value) {
        return Map.of(
                "id", id,
                "version", "1.0.0",
                "apiVersionRange", "[1.0,2.0)",
                "requiredCapabilities", List.of("settings"),
                "priority", priority,
                "contributions", List.of(Map.of(
                        "type", "SETTING", "id", "editor.fontSize",
                        "payload", Map.of("value", value))));
    }

    @Test
    void endToEndSubmitQueryExplain() {
        ResponseEntity<Map> put = rest.exchange("/api/extensions", HttpMethod.PUT,
                new HttpEntity<>(List.of(manifest("ext.a", 1, "small"), manifest("ext.b", 9, "large"))),
                Map.class);
        assertEquals(HttpStatus.OK, put.getStatusCode());
        assertEquals(Boolean.TRUE, put.getBody().get("committed"));

        Map<?, ?> contributions = rest.getForObject("/api/contributions", Map.class);
        assertTrue(contributions.containsKey("SETTING:editor.fontSize"));

        Map<?, ?> explain = rest.getForObject("/api/explain/SETTING/editor.fontSize", Map.class);
        assertEquals("ext.b", explain.get("winnerExtensionId"));
        assertEquals(List.of("ext.b", "ext.a"), explain.get("overrideChain"));
        assertEquals("large", ((Map<?, ?>) explain.get("effective")).get("payload")
                .toString().contains("large") ? "large" : null);
    }

    @Test
    void invalidManifestIsRejectedWith422AndOldSetSurvives() {
        rest.exchange("/api/extensions", HttpMethod.PUT,
                new HttpEntity<>(List.of(manifest("ext.a", 1, "v"))), Map.class);
        Map<String, Object> bad = Map.of("id", "ext.bad", "version", "1.0.0",
                "apiVersionRange", "[1.0,2.0)",
                "requiredCapabilities", List.of("ghost-cap"));
        ResponseEntity<Map> resp = rest.exchange("/api/extensions", HttpMethod.PUT,
                new HttpEntity<>(List.of(bad)), Map.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
        assertEquals(Boolean.FALSE, resp.getBody().get("committed"));
        assertFalse(((List<?>) resp.getBody().get("errors")).isEmpty());
        // 旧集合仍在服务
        Map<?, ?> current = rest.getForObject("/api/extensions", Map.class);
        assertEquals(1, ((List<?>) current.get("extensions")).size());
    }

    @Test
    void disableViaDelete() {
        rest.exchange("/api/extensions", HttpMethod.PUT,
                new HttpEntity<>(List.of(manifest("ext.a", 1, "v"))), Map.class);
        rest.delete("/api/extensions/ext.a");
        Map<?, ?> current = rest.getForObject("/api/extensions", Map.class);
        assertEquals(0, ((List<?>) current.get("extensions")).size());
    }
}
