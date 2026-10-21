package com.bifrostorbit.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaCallerTest {
    private MockWebServer server;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private CmsOrbitClient client() {
        return CmsOrbitClient.builder("cms_pk_test")
            .baseUrl(server.url("/cms").toString())
            .defaultRegion("ireland")
            .retries(0)
            .build();
    }

    @Test
    void readsANavigationWithAFuture() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .body("{\"data\":{\"key\":\"main\",\"name\":\"Main menu\",\"items\":[]}}")
            .build());

        CmsNavigation menu = client().getAsync().getNavigation("main", new RequestOptions().withLocale("en-IE")).join();

        assertEquals("Main menu", menu.getName());
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("ireland", request.getUrl().queryParameter("region"));
        assertEquals("en-IE", request.getUrl().queryParameter("locale"));
    }

    @Test
    void listsWithOptions() {
        server.enqueue(new MockResponse.Builder()
            .body("{\"data\":{\"items\":[],\"total\":0,\"page\":1,\"hasMore\":false}}")
            .build());

        CmsContentList list = client().getAsync()
            .list(new ListOptions("article").withSort(CmsSort.PUBLISHED_AT_DESC).withPerPage(12))
            .join();

        assertEquals(0, list.getTotal());
    }

    @Test
    void failuresCarryTheCmsException() {
        server.enqueue(new MockResponse.Builder()
            .code(401)
            .body("{\"error\":{\"code\":\"unauthorized\",\"message\":\"Invalid API key\"}}")
            .build());

        CompletionException error = assertThrows(CompletionException.class, () -> client().getAsync().getRegions().join());
        assertInstanceOf(CmsUnauthorizedException.class, error.getCause());
    }
}
