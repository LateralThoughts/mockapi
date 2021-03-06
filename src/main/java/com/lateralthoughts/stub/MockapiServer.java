package com.lateralthoughts.stub;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.AbstractService;
import com.mongodb.MongoStub;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jongo.Jongo;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.transport.connect.SocketConnection;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.FluentIterable.from;


public class MockapiServer extends AbstractService implements Container {
    private static Log LOG = LogFactory.getLog(MockapiServer.class);
    public static String KEYSTORE_PROPERTY = "javax.net.ssl.keyStore";
    public static String KEYSTORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
    public static String KEYSTORE_TYPE_PROPERTY = "javax.net.ssl.keyStoreType";

    private Integer port;
    private SocketConnection socketConnection;
    private boolean ssl;
    private AtomicLong idGenerator = new AtomicLong(0);
    private Jongo jongo;
    static ObjectMapper mapper = new ObjectMapper();


    public MockapiServer(Integer port, boolean ssl) {
        this.port = port;
        this.ssl = ssl;
        try {
            this.jongo = new Jongo(new MongoStub().getDB("mockapi"));
        } catch (UnknownHostException e) {
        }
    }

    public static void main(String[] args) {
        new MockapiServer(8086, false).start();
    }

    @Override
    public void handle(Request req, Response resp) {
        try {
            handlePost(req, resp);
            handleGet(req, resp);
        } catch (IOException e) {
            LOG.error(e);
        } finally {
            closeResource(resp);
        }
    }

    private void handlePost(Request req, Response resp) throws IOException {
        if(req.getMethod().equalsIgnoreCase("POST")) {
            resp.setCode(201);
            final String path = trimSlashes(req.getPath().getPath());
            final String id = String.valueOf(idGenerator.incrementAndGet());
            resp.set("location", path + "/" + id);
            //resp.set("Content-Type", "application/json");
            Map<String, String> bodyAsMap = mapper.readValue(req.getContent(), new HashMap<String, String>().getClass());
            bodyAsMap.put("id", id);
            jongo.getCollection(path).save(bodyAsMap);
        }
    }

    private void handleGet(Request req, Response resp) throws IOException {
        if(req.getMethod().equalsIgnoreCase("GET")) {
            resp.getOutputStream().write((mapper.writeValueAsString(from(mongo(req)).transform(unwrap(req)).transform(removeMongoId()).toList()).getBytes()));
        }
    }

    private Function<Object, Map<String, Object>> unwrap(final Request req) {
        return o -> {
            Map<String, Object> res = ((Map<String, Object>) o);
            final String unwrap = req.getQuery().get("unwrap");
            if(unwrap != null) {
                Iterable<String> attrToWrapNames = Splitter.on(',').split(unwrap);
                for(String attrToWrap : attrToWrapNames) {
                    if(res.containsKey(attrToWrap)) {
                        String uri = res.get(attrToWrap).toString();
                        String collection = uri.substring(0, uri.indexOf("/"));
                        String id = uri.substring(uri.indexOf("/")+1, uri.length());
                        res.put(attrToWrap, removeMongoId().apply((Map<String, Object>) jongo.getCollection(collection).findOne("{id:\"" + id + "\"}").as(Object.class)));
                    }
                }
            }
            return res;
        };
    }

    private Function<? super Map.Entry<String, String>, String> toAttrName() {
        return stringStringEntry -> stringStringEntry.getValue();
    }

    private Predicate<? super Map.Entry<String, String>> byName(String unwrap) {
        return stringStringEntry -> stringStringEntry.getKey().equalsIgnoreCase(unwrap);
    }

    private List<Object> mongo(Request req) {
        return from(jongo.getCollection(trimSlashes(req.getPath().getPath())).find(mongoRequest(req)).as(Object.class)).toList();
    }

    private String mongoRequest(Request req) {
        return "{" + Joiner.on(',').join(from(req.getQuery().entrySet()).filter(not(byName("unwrap"))).transform(toStringMongoQuery())) + "}";
    }

    private Function<? super Map.Entry<String, String>, String> toStringMongoQuery() {
        return stringStringEntry -> stringStringEntry.getKey()+":\""+stringStringEntry.getValue()+"\"";
    }

    private Function<Map<String, Object>, Object> removeMongoId() {
        return o -> {
            o.remove("_id");
            o.remove("id");
            return o;
        };
    }

    @Override
    protected void doStart() {
        try {
            socketConnection = new SocketConnection(this);
            if(!ssl) {
                socketConnection.connect(new InetSocketAddress(port));
            } else {
                socketConnection.connect(new InetSocketAddress(port), sslContext());
            }

            notifyStarted();
            LOG.info("Server started on port : " + port);
        } catch (Exception e) {
            notifyFailed(e);
            LOG.error("Unable to start the server", e);
        }
    }

    public void pause() {
        try {
            socketConnection.close();
        } catch (IOException e) {
            notifyFailed(e);
        } finally {
            LOG.info("Server paused");
        }
    }

    public void unpause() {
        try {
            socketConnection = new SocketConnection(this);
            if(!ssl) {
                socketConnection.connect(new InetSocketAddress(port));
            } else {
                socketConnection.connect(new InetSocketAddress(port), sslContext());
            }

        } catch (Exception e) {
            notifyFailed(e);
        } finally {
            LOG.info("Server paused");
        }
    }

    private SSLContext sslContext() throws Exception {
        String keyStoreFile = System.getProperty(KEYSTORE_PROPERTY, "keystore");
        String keyStorePassword = System.getProperty(KEYSTORE_PASSWORD_PROPERTY,"hopwork1234");
        String keyStoreType = System.getProperty(KEYSTORE_TYPE_PROPERTY, KeyStore.getDefaultType());

        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        InputStream keyStoreFileInpuStream = null;
        try {
            keyStoreFileInpuStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(keyStoreFile);

            keyStore.load(keyStoreFileInpuStream, keyStorePassword.toCharArray());
        } finally {
            if (keyStoreFileInpuStream != null) {
                keyStoreFileInpuStream.close();
            }
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("SSLv3");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        return sslContext;
    }

    @Override
    protected void doStop() {
        try {
            socketConnection.close();
            notifyStopped();
        } catch (IOException e) {
            notifyFailed(e);
        } finally {
            LOG.info("Server stopped");
        }
    }

    private void closeResource(Response resp) {
        try {
            resp.close();
        } catch (IOException e) {
            LOG.error("Error", e);
        }
    }

    public static String trimSlashes(String string) {
        return string.replaceAll("(^/)|(/$)", "");
    }

    public Integer getPort() {
        return port;
    }

    public void reset() throws UnknownHostException {
        idGenerator = new AtomicLong(0);
        MongoStub.reset();
        this.jongo = new Jongo(new MongoStub().getDB("mockapi"));
    }
}