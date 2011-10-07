package com.cloudbees.jersey;

import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.view.Viewable;
import com.sun.jersey.spi.template.ViewProcessor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Paul Sandoz
 */
public class JellyViewProcessor implements ViewProcessor<RequestDispatcher> {
    private @Context HttpContext hc;

    private @Context ServletContext servletContext;

    private @Context ThreadLocal<HttpServletRequest> requestInvoker;

    private @Context ThreadLocal<HttpServletResponse> responseInvoker;

    public JellyViewProcessor() {}

    public RequestDispatcher resolve(String path) {
        Viewable v = (Viewable)hc.getResponse().getEntity();

        StaplerRequest request = (StaplerRequest)requestInvoker.get();

        try {
            return request.getView(v.getModel(), path);
        } catch (Exception e) {
            return null;
        }
    }

    public void writeTo(RequestDispatcher resolved, Viewable viewable, OutputStream out) throws IOException {
        StaplerRequest request = (StaplerRequest)requestInvoker.get();
        StaplerResponse response = (StaplerResponse)responseInvoker.get();

        try {
            resolved.forward(request, response);
        } catch (Exception e) {
            throw new ContainerException(e);
        }
    }
}
