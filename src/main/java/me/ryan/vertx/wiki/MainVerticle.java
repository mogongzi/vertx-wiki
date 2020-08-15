package me.ryan.vertx.wiki;

import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.reactivex.core.AbstractVerticle;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> promise) {

        Single<String> dbVerticleDeployment = vertx.rxDeployVerticle("me.ryan.vertx.wiki.database.WikiDatabaseVerticle");

        dbVerticleDeployment
                .flatMap(id -> vertx.rxDeployVerticle("me.ryan.vertx.wiki.http.HttpServerVerticle", new DeploymentOptions().setInstances(2)))
                .flatMap(id -> vertx.rxDeployVerticle("me.ryan.vertx.wiki.http.AuthInitializerVerticle"))
                .subscribe(id -> promise.complete(), promise::fail);
    }
}