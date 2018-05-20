package org.apache.geronimo.microprofile.opentracing.microprofile.server;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.util.HashMap;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

@Dependent
@WebFilter(asyncSupported = true, urlPatterns = "/*") // todo: move to initializer
public class OpenTracingFilter implements Filter {

    @Inject
    private Tracer tracer;

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (!HttpServletRequest.class.isInstance(request)) {
            chain.doFilter(request, response);
            return;
        }
        // todo: implicit start for matching urls
        try {
            chain.doFilter(request, response);
        } catch (final Exception ex) {
            ofNullable(request.getAttribute(OpenTracingFilter.class.getName())).map(Span.class::cast).ifPresent(span -> {
                Tags.HTTP_STATUS.set(span, HttpServletResponse.class.cast(response).getStatus());
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
            ofNullable(request.getAttribute(OpenTracingFilter.class.getName())).map(Span.class::cast).ifPresent(span -> {
                if (request.isAsyncStarted()) {
                    request.getAsyncContext().addListener(new AsyncListener() {

                        @Override
                        public void onComplete(final AsyncEvent event) {
                            span.finish();
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
                } else {
                    span.finish();
                }
            });
        }
    }
}
