package org.wiremock.webhooks.ext;

import com.github.tomakehurst.wiremock.common.ListOrSingle;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.TreeMap;

public class LoggedResponseTemplateModel {

    private final Map<String, ListOrSingle<String>> headers;
    private final String body;

    protected LoggedResponseTemplateModel(
            Map<String, ListOrSingle<String>> headers,
            String body) {
        this.headers = headers;
        this.body = body == null ? "" : body;
    }

    public static LoggedResponseTemplateModel from(final LoggedResponse response) {
        Map<String, ListOrSingle<String>> adaptedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        adaptedHeaders.putAll(
                Maps.toMap(
                        response.getHeaders().keys(),
                        input -> ListOrSingle.of(response.getHeaders().getHeader(input).values())));
        return new LoggedResponseTemplateModel(adaptedHeaders, response.getBodyAsString());
    }

    public Map<String, ListOrSingle<String>> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}