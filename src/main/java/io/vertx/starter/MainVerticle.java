package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {

  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content blob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name=?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content=? where Id=?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id=?";

  @Override
  public void start(Promise<Void> promise) {
    Future<Void> steps = prepareDatabase().compose( v -> startHttpServer());
    steps.setHandler(ar -> {
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
  }

  private Future<Void> prepareDatabase() {
    Promise<Void> promise = Promise.promise();
    return promise.future();
  }

  private Future<Void> startHttpServer() {
    Promise<Void> promise = Promise.promise();
    return promise.future();
  }

}
