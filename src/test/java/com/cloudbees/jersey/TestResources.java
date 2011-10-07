package com.cloudbees.jersey;

import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.view.Viewable;

import javax.ws.rs.*;

/**
 * @author Paul Sandoz
 */
public class TestResources extends JerseyJenkinsTestCase {

    private final static String BASE_PATH = "jersey";

    @Path("/")
    @Produces("text/plain")
    public static class BasicResource {
        @GET
        public String get() {
            return "RESOURCE";
        }

        @Path("b")
        @GET
        public String getB(@QueryParam("s") String s) {
            return s;
        }

    }

    public void testBasicResource() throws Exception {
        hudson.getActions().add(new JerseyRootAction(BASE_PATH, BasicResource.class));

        WebResource r = baseResource.path(BASE_PATH);

        assertEquals("RESOURCE", r.get(String.class));

        assertEquals("foo", r.path("b").queryParam("s", "foo").get(String.class));
    }

    @Path("/")
    @Produces("text/html")
    public static class JellyResource {
        @GET
        public Viewable get() {
            return new Viewable("index", this);
        }

        public String getVal() {
            return "val";
        }
    }

    public void testJellyResource() throws Exception {
        hudson.getActions().add(new JerseyRootAction(BASE_PATH, JellyResource.class));

        String response = baseResource.path(BASE_PATH).get(String.class);
        assertTrue(response.contains("val"));
    }
}
