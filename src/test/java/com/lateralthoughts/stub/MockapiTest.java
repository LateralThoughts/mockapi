package com.lateralthoughts.stub;

import java.net.UnknownHostException;

import com.jayway.restassured.RestAssured;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class MockapiTest {

    private static MockapiServer server;

    @BeforeClass
    public static void beforeClass() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 8086;
        RestAssured.basePath = "";
        server = new MockapiServer(RestAssured.port, false);
        server.startAndWait();
    }

    @Before
    public void before() throws UnknownHostException {
        server.reset();
    }

    @Test
    public void shouldReturn201WhenPost() {
        final String user = "{\"name\":\"Jean-baptiste\"}";
        given().body(user)
                .expect()
                .statusCode(201)
                .header("location", "user/1")
                //.contentType("application/json")
                .when().post("/user");
        expect()
                .statusCode(200)
                //.contentType("application/json")
                .body(containsString(user))
                .when().get("/user");
    }

    @Test
    public void queryParams() {
        final String jb = "{\"name\":\"Jean-baptiste\"}";
        final String nicolas = "{\"name\":\"Nicolas\"}";
        given().body(jb)
                .expect()
                .statusCode(201)
                .header("location", "user/1")
                        //.contentType("application/json")
                .when().post("/user");
        given().body(nicolas)
                .expect()
                .statusCode(201)
                .header("location", "user/2")
                        //.contentType("application/json")
                .when().post("/user");

        expect()
                .statusCode(200)
                        //.contentType("application/json")
                .body(containsString("["+nicolas+"]"))
                .when().get("/user?name=Nicolas");
    }

    @Test
    public void unwrap() {
        final String lateralThoughts = "{\"name\":\"LateralThoughts\"}";
        final String vidal = "{\"name\":\"Vidal\"}";
        final String jb = "{\"name\":\"Jean-baptiste\", \"company\":\"company/2\", \"client\":\"client/1\"}";
        final String nicolas = "{\"name\":\"Nicolas\", \"company\":\"company/2\", \"client\":\"client/1\"}";
        given().body(vidal)
                .expect()
                .statusCode(201)
                .header("location", "client/1")
                        //.contentType("application/json")
                .when().post("/client");
        given().body(lateralThoughts)
                .expect()
                .statusCode(201)
                .header("location", "company/2")
                        //.contentType("application/json")
                .when().post("/company");
        given().body(jb)
                .expect()
                .statusCode(201)
                .header("location", "user/3")
                        //.contentType("application/json")
                .when().post("/user");
        given().body(nicolas)
                .expect()
                .statusCode(201)
                .header("location", "user/4")
                        //.contentType("application/json")
                .when().post("/user");

        expect()
                .statusCode(200)
                        //.contentType("application/json")
                .body(containsString("[{\"name\":\"Jean-baptiste\",\"client\":{\"name\":\"Vidal\"},\"company\":{\"name\":\"LateralThoughts\"}},{\"name\":\"Nicolas\",\"client\":{\"name\":\"Vidal\"},\"company\":{\"name\":\"LateralThoughts\"}}]"))
                .when().get("/user?unwrap=client,company");
    }

    @AfterClass
    public static void after() {
        if (null != server) {
            server.stopAndWait();
        }
    }
}
