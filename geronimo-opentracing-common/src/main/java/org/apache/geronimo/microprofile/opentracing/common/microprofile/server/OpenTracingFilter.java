package org.apache.geronimo.microprofile.opentracing.common.microprofile.server;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.impl.ScopeManagerImpl;
import org.apache.geronimo.microprofile.opentracing.common.impl.ServletHeaderTextMap;
import org.apache.geronimo.microprofile.opentracing.common.spi.Container;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

public class OpenTracingFilter implements Filter {
    private Tracer tracer;
    private GeronimoOpenTracingConfig config;
    private ScopeManagerImpl manager;
    private Container container;

    private Collection<Predicate<String>> forcedUrls;

    private List<Predicate<String>> skipUrls;

    private boolean skipDefaultTags;

    public void setTracer(final Tracer tracer) {
        this.tracer = tracer;
    }

    public void setConfig(final GeronimoOpenTracingConfig config) {
        this.config = config;
    }

    public void setManager(final ScopeManagerImpl manager) {
        this.manager = manager;
    }

    public void setContainer(final Container container) {
        this.container = container;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        if (container == null) {
            container = Container.get();
        }
        if (tracer == null) {
            tracer = container.lookup(Tracer.class);
        }
        if (manager == null) {
            manager = container.lookup(ScopeManagerImpl.class);
        }
        if (config == null) {
            config = container.lookup(GeronimoOpenTracingConfig.class);
        }
        skipDefaultTags = Boolean.parseBoolean(config.read("filter.forcedTracing.skipDefaultTags", "false"));
        forcedUrls = ofNullable(config.read("filter.forcedTracing.urls", null))
                .map(String::trim).filter(v -> !v.isEmpty())
                .map(v -> toMatchingPredicates(v, "forcedTracing"))
                .orElse(null);
        skipUrls = ofNullable(config.read("filter.skippedTracing.urls", null))
                .map(String::trim).filter(v -> !v.isEmpty())
                .map(v -> toMatchingPredicates(v, "skippedTracing"))
                .orElse(null);
    }

    private List<Predicate<String>> toMatchingPredicates(final String v, final String keyMarker) {
        final String matchingType = config.read("filter." + keyMarker + ".matcherType", "prefix");
        final Function<String, Predicate<String>> matcherFactory;
        switch (matchingType) {
        case "regex":
            matcherFactory = from -> {
                final Pattern compiled = Pattern.compile(from);
                return url -> compiled.matcher(url).matches();
            };
            break;
        case "prefix":
        default:
            matcherFactory = from -> url -> url.startsWith(from);
        }
        return Stream.of(v.split(",")).map(String::trim).filter(it -> !it.isEmpty()).map(matcherFactory)
                     .collect(toList());
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (!HttpServletRequest.class.isInstance(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (forcedUrls != null && !forcedUrls.isEmpty()) {
            final HttpServletRequest req = HttpServletRequest.class.cast(request);
            final String matching = req.getRequestURI().substring(req.getContextPath().length());
            if (forcedUrls.stream().anyMatch(p -> p.test(matching))) {
                final Tracer.SpanBuilder builder = tracer.buildSpan(buildServletOperationName(req));
                builder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
                builder.withTag("component", "servlet");

                ofNullable(ofNullable(tracer.activeSpan()).map(Span::context)
                        .orElseGet(() -> tracer.extract(Format.Builtin.HTTP_HEADERS,
                                new ServletHeaderTextMap(req, HttpServletResponse.class.cast(response)))))
                                        .ifPresent(builder::asChildOf);

                final Scope scope = builder.startActive(true);
                final Span span = scope.span();

                if (!skipDefaultTags) {
                    Tags.HTTP_METHOD.set(span, req.getMethod());
                    Tags.HTTP_URL.set(span, req.getRequestURL().toString());
                }

                request.setAttribute(OpenTracingFilter.class.getName(), scope);
            }
        }
        if (skipUrls != null && !skipUrls.isEmpty()) {
            final HttpServletRequest req = HttpServletRequest.class.cast(request);
            final String matching = req.getRequestURI().substring(req.getContextPath().length());
            if (forcedUrls.stream().anyMatch(p -> p.test(matching))) {
                chain.doFilter(request, response);
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } catch (final Exception ex) {
            getCurrentScope(request).ifPresent(scope -> {
                        final int status = HttpServletResponse.class.cast(response).getStatus();
                        final Span span = scope.span();
                        Tags.HTTP_STATUS.set(span,
                                status == HttpServletResponse.SC_OK ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : status);
                        Tags.ERROR.set(span, true);
                        span.log(new HashMap<String, Object>() {

                            {
                                put("event", Tags.ERROR.getKey());
                                put("event.object", ex);
                            }
                        });
                    });
            throw ex;
        } finally {
            getCurrentScope(request).ifPresent(scope -> {
                if (request.isAsyncStarted()) {
                    request.getAsyncContext().addListener(new AsyncListener() {

                        @Override
                        public void onComplete(final AsyncEvent event) {
                            scope.close();
                        }

                        @Override
                        public void onTimeout(final AsyncEvent event) {
                            // no-op
                        }

                        @Override
                        public void onError(final AsyncEvent event) {
                            // no-op
                        }

                        @Override
                        public void onStartAsync(final AsyncEvent event) {
                            // no-op
                        }
                    });
                    manager.clear();
                } else {
                    scope.close();
                }
            });
        }
    }

    private Optional<Scope> getCurrentScope(final ServletRequest request) {
        return ofNullable(ofNullable(request.getAttribute(OpenTracingFilter.class.getName()))
                .orElseGet(() -> tracer.scopeManager().active())).map(Scope.class::cast);
    }

    protected String buildServletOperationName(final HttpServletRequest req) {
        return req.getMethod() + ":" + req.getRequestURL();
    }
}
