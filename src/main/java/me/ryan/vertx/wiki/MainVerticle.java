package me.ryan.vertx.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import me.ryan.vertx.wiki.database.WikiDatabaseVerticle;
import me.ryan.vertx.wiki.http.AuthInitializerVerticle;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> promise) {

        Promise<String> dbDeploymentPromise = Promise.promise();
        vertx.deployVerticle(new WikiDatabaseVerticle(), dbDeploymentPromise);

        Future<String> authDeploymentFuture = dbDeploymentPromise.future().compose(id -> {
           Promise<String> deployPromise = Promise.promise();
           vertx.deployVerticle(new AuthInitializerVerticle(), deployPromise);
           return deployPromise.future();
        });

        Future<String> deployHttpFuture = authDeploymentFuture.compose(id -> {
           Promise<String> deployPromise = Promise.promise();
            vertx.deployVerticle("me.ryan.vertx.wiki.http.HttpServerVerticle", new DeploymentOptions().setInstances(2), deployPromise);
            return deployPromise.future();
        });

        deployHttpFuture.setHandler(ar -> {
            if (ar.succeeded()) {
                promise.complete();
            } else {
                promise.fail(ar.cause());
            }
        });
    }

}