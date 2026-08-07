package com.hope.enterpriserag.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 HTTP 安全边界的状态码契约，确保前端能够区分未认证与权限不足。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.sql.init.mode=never"
})
class SecurityHttpStatusTests {

    @LocalServerPort
    private int port;

    @Test
    void returnsUnauthorizedWhenAccessTokenIsMissing() throws Exception {
        HttpResponse<Void> response = getDocuments(null);

        assertEquals(401, response.statusCode());
    }

    @Test
    void returnsUnauthorizedWhenAccessTokenIsInvalid() throws Exception {
        HttpResponse<Void> response = getDocuments("invalid-access-token");

        assertEquals(401, response.statusCode());
    }

    private HttpResponse<Void> getDocuments(String accessToken) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/documents?page=1&pageSize=10"))
                .GET();
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding());
    }
}
