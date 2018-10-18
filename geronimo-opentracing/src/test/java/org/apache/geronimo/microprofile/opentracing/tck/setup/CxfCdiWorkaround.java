package org.apache.geronimo.microprofile.opentracing.tck.setup;

import static java.util.stream.Collectors.toMap;

import java.lang.reflect.Field;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.apache.cxf.cdi.extension.JAXRSServerFactoryCustomizationExtension;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.message.Message;

@ApplicationScoped
public class CxfCdiWorkaround implements JAXRSServerFactoryCustomizationExtension {
    @Override
    public void customize(final JAXRSServerFactoryBean bean) {
        final Map<Class<?>, ResourceProvider> providers = getProviders(bean);
        final Map<Class<?>, ResourceProvider> workaround = providers.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> new WorkaroundProvider(e.getValue())));
        providers.clear();
        providers.putAll(workaround);
    }

    private Map<Class<?>, ResourceProvider> getProviders(final JAXRSServerFactoryBean bean) {
        try {
            final Field f = JAXRSServerFactoryBean.class.getDeclaredField("resourceProviders");
            f.setAccessible(true);
            return (Map<Class<?>, ResourceProvider>) f.get(bean);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static class WorkaroundProvider implements ResourceProvider {
        private final ResourceProvider delegate;

        private WorkaroundProvider(final ResourceProvider value) {
            this.delegate = value;
        }

        @Override
        public Object getInstance(final Message m) {
            return delegate.getInstance(m);
        }

        @Override
        public void releaseInstance(final Message m, final Object o) {
            // no-op: don't release otherwise we are not thread safe for @Dependent, this leaks but ok in *this* context
        }

        @Override
        public Class<?> getResourceClass() {
            return delegate.getResourceClass();
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }
}
