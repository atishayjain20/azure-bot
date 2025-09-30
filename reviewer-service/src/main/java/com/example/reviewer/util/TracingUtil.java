package com.example.reviewer.util;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.util.Map;

public final class TracingUtil {
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("azure-pr-reviewer");

    private TracingUtil() {}

    public static Span startChildSpan(String name) {
        return tracer.spanBuilder(name).setSpanKind(SpanKind.INTERNAL).startSpan();
    }

    public static Span startClientSpan(String name) {
        return tracer.spanBuilder(name).setSpanKind(SpanKind.CLIENT).startSpan();
    }

    public static void addAttributes(Span span, Map<String, String> attributes) {
        if (span == null || !span.getSpanContext().isValid() || attributes == null) return;
        attributes.forEach((k, v) -> {
            if (v != null) span.setAttribute(AttributeKey.stringKey(k), v);
        });
    }

    public static void addEvent(Span span, String name, Map<String, String> attributes) {
        if (span == null || !span.getSpanContext().isValid()) return;
        if (attributes == null || attributes.isEmpty()) {
            span.addEvent(name);
        } else {
            AttributesBuilder builder = Attributes.builder();
            attributes.forEach((k, v) -> { if (v != null) builder.put(AttributeKey.stringKey(k), v); });
            span.addEvent(name, builder.build());
        }
    }

    public static void recordException(Span span, Throwable t) {
        if (span == null || !span.getSpanContext().isValid() || t == null) return;
        span.recordException(t);
        span.setStatus(StatusCode.ERROR, t.getMessage() == null ? "error" : t.getMessage());
    }
}
