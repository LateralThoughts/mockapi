package com.lateralthoughts.stub;

import com.google.common.util.concurrent.AbstractService;
import com.jayway.restassured.RestAssured;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class MockapiTest {

    private static AbstractService server;

    @BeforeClass
    public static void before() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 8086;
        RestAssured.basePath = "";
        server = new MockapiServer(RestAssured.port, false);
        server.startAndWait();
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

    @AfterClass
    public static void after() {
        if (null != server) {
            server.stopAndWait();
        }
    }
}
