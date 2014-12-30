package io.rwilinski.tracely;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;

/**
 * Created by Rafal on 27.12.14.
 */
public class TracelyConnection {
    public HttpPost post;
    public TracelyMethod method;
    public HttpResponse response;

    public TracelyConnection(HttpPost post, TracelyMethod method) {
        this.post = post;
        this.method = method;
    }
}
