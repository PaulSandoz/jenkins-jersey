package com.cloudbees.jersey;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import org.jvnet.hudson.test.HudsonTestCase;

import javax.ws.rs.core.UriBuilder;

/**
 * @author Paul Sandoz
 */
public abstract class JerseyJenkinsTestCase extends HudsonTestCase {

    protected Client client;

    protected WebResource baseResource;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        client = createClient();
        baseResource = client.resource(UriBuilder.fromUri(getURL().toURI()).build());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        client.destroy();
    }

    protected Client createClient() {
        return Client.create();
    }
}
