package org.apache.geronimo.microprofile.opentracing.tck.setup;

import org.eclipse.microprofile.opentracing.Traced;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Traced
@RequestScoped
@Path("hello")
public class SimpleService {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String sayHello() throws Exception {
        return "Hello, world!";
    }


}
