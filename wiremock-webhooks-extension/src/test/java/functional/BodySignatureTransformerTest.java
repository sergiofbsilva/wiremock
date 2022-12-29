/*
 * Copyright (C) 2021 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package functional;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.webhooks.Webhooks;
import org.wiremock.webhooks.ext.BodySignatureCustomHeaderTransformer;
import testsupport.CompositeNotifier;
import testsupport.TestNotifier;
import testsupport.WireMockTestClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.http.RequestMethod.POST;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wiremock.webhooks.Webhooks.webhook;

public class BodySignatureTransformerTest {

    CountDownLatch latch;

    @RegisterExtension
    public WireMockExtension targetServer =
            WireMockExtension.newInstance()
                    .options(
                            options()
                                    .dynamicPort()
                                    .extensions(
                                            new PostServeAction() {
                                                @Override
                                                public void doGlobalAction(ServeEvent serveEvent, Admin admin) {
                                                    if (serveEvent.getRequest().getUrl().startsWith("/callback")) {
                                                        latch.countDown();
                                                    }
                                                }

                                                @Override
                                                public String getName() {
                                                    return "test-latch";
                                                }
                                            })
                                    .notifier(new ConsoleNotifier("Target", true)))
                    .build();

    TestNotifier testNotifier = new TestNotifier();
    CompositeNotifier notifier =
            new CompositeNotifier(testNotifier, new ConsoleNotifier("Main", true));
    WireMockTestClient client;

    private final Map<String,String> environmentVariables = new HashMap<>();

    @RegisterExtension
    public WireMockExtension rule =
            WireMockExtension.newInstance()
                    .options(options().dynamicPort().notifier(notifier).extensions(new Webhooks(Collections.emptyList(), Collections.singletonList(new BodySignatureCustomHeaderTransformer(environmentVariables::get)))))
                    .configureStaticDsl(true)
                    .build();

    @BeforeEach
    public void init() {
        environmentVariables.clear();
        testNotifier.reset();
        targetServer.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        latch = new CountDownLatch(1);
        client = new WireMockTestClient(rule.getPort());
        WireMock.configureFor(targetServer.getPort());

        System.out.println("Target server port: " + targetServer.getPort());
        System.out.println("Under test server port: " + rule.getPort());
    }

    @Test
    public void appliesBodySignatureWhenParametersExist() throws Exception {
        environmentVariables.put("SIGNATURE_SECRET", "f318bc42e9dd9ec6deb778adbe188d7e");
        client.postJson(
                "/__admin/mappings",
                "{\n"
                        + "  \"id\" : \"8a58e190-4a83-4244-a064-265fcca46884\",\n"
                        + "  \"request\" : {\n"
                        + "    \"urlPath\" : \"/templating\",\n"
                        + "    \"method\" : \"POST\"\n"
                        + "  },\n"
                        + "  \"response\" : {\n"
                        + "    \"status\" : 200\n"
                        + "  },\n"
                        + "  \"uuid\" : \"8a58e190-4a83-4244-a064-265fcca46884\",\n"
                        + "  \"postServeActions\" : [{\n"
                        + "    \"name\" : \"webhook\",\n"
                        + "    \"parameters\" : {\n"
                        + "      \"method\" : \"{{jsonPath originalRequest.body '$.method'}}\",\n"
                        + "      \"url\" : \""
                        + targetServer.baseUrl()
                        + "{{{jsonPath originalRequest.body '$.callbackPath'}}}\",\n"
                        + "      \"headers\" : {\n"
                        + "        \"X-Single\" : \"{{math 1 '+' 2}}\",\n"
                        + "        \"X-Multi\" : [ \"{{math 3 'x' 2}}\", \"{{parameters.one}}\" ]\n"
                        + "      },\n"
                        + "      \"body\" : \"{{jsonPath originalRequest.body '$.name'}}\",\n"
                        + "      \"one\" : \"param-one-value\",\n"
                        + "      \"bodySignature\" : {\"headerName\": \"X-Custom-Signature\", \"secretEnvVarName\" : \"SIGNATURE_SECRET\"}\n"
                        + "    }\n"
                        + "  }]\n"
                        + "}\n");

        verify(0, postRequestedFor(anyUrl()));

        client.postJson(
                "/templating",
                "{\n"
                        + "  \"callbackPath\": \"/callback/123\",\n"
                        + "  \"method\": \"POST\",\n"
                        + "  \"name\": \"Tom\"\n"
                        + "}");

        waitForRequestToTargetServer();

        LoggedRequest request =
                targetServer.findAll(postRequestedFor(urlEqualTo("/callback/123"))).get(0);

        assertThat(request.header("X-Custom-Signature").firstValue(), is("16d5833bc25479814109a5f007c4c28148425c48e5e0bea38f43c20428e48869"));
    }

    private void waitForRequestToTargetServer() throws Exception {
        assertTrue(
                latch.await(20, SECONDS), "Timed out waiting for target server to receive a request");
    }
}
