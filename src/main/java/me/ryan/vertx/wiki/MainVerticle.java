package me.ryan.vertx.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (id INT IDENTITY PRIMARY KEY, name VARCHAR(255), content clob)";
  private static final String SQL_GET_PAGE = "select id, content from Pages where name=?";
  private static final String SQL_CREATE_PAGE = "insert into Pages (name, content) values (?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set content=? where id=?";
  private static final String SQL_ALL_PAGES = "select name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where id=?";

  private JDBCClient dbClient;
  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  private FreeMarkerTemplateEngine templateEngine;


  private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n\nFeel-free to write in Markdown!\n";

  @Override
  public void start(Promise<Void> promise) {
//    Future<Void> steps = prepareDatabase().compose( v -> startHttpServer());
//    steps.setHandler(ar -> {
//      if (ar.succeeded()) {
//        promise.complete();
//      } else {
//        promise.fail(ar.cause());
//      }
//    });

    Promise<String> dbVerticleDeployment  = Promise.promise();
    vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment);

    dbVerticleDeployment.future().compose(id -> {
      Promise<String> httpVerticleDeployment = Promise.promise();
      vertx.deployVerticle("me.ryan.vertx.wiki.HttpServerVerticle", new DeploymentOptions().setInstances(2), httpVerticleDeployment);

      return httpVerticleDeployment.future();
    }).setHandler(ar -> {
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
  }

  private Future<Void> prepareDatabase() {
    Promise<Void> promise = Promise.promise();

    dbClient = JDBCClient.createShared(vertx, new JsonObject()
    .put("url", "jdbc:hsqldb:file:db/wiki")
    .put("driver_class", "org.hsqldb.jdbcDriver")
    .put("max_pool_size", 30));

    dbClient.getConnection(ar -> {
      if (ar.failed()) {
        LOGGER.error("Could not open a database connection", ar.cause());
        promise.fail(ar.cause());
      } else {
        SQLConnection connection = ar.result();
        connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
          connection.close();
          if (create.failed()) {
            LOGGER.error("Database preparation error", create.cause());
            promise.fail(create.cause());
          } else {
            promise.complete();
          }
        });
      }
    });

    return promise.future();
  }

  private Future<Void> startHttpServer() {
    Promise<Void> promise = Promise.promise();

    HttpServer server = vertx.createHttpServer();

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    server.requestHandler(router)
            .listen(8080, ar -> {
              if (ar.succeeded()) {
                LOGGER.info("HTTP Server running on port 8080");
                promise.complete();
              } else {
                LOGGER.error("Could not start a HTTP server", ar.cause());
                promise.fail(ar.cause());
              }
            });

    return promise.future();
  }

  private void pageDeletionHandler(RoutingContext context) {
    String id = context.request().getParam("id");
    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        connection.updateWithParams(SQL_DELETE_PAGE, new JsonArray().add(id), res -> {
          connection.close();
          if (res.succeeded()) {
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/");
            context.response().end();
          } else {
            context.fail(res.cause());
          }
        });
      } else {
        context.fail(car.cause());
      }
    });
  }

  private void pageUpdateHandler(RoutingContext context) {
    String id = context.request().getParam("id");
    String title = context.request().getParam("title");
    String markdown = context.request().getParam("markdown");
    boolean newPage = "yes".equals(context.request().getParam("newPage"));

    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        String sql = newPage ? SQL_CREATE_PAGE : SQL_SAVE_PAGE;
        JsonArray params = new JsonArray();
        if (newPage) {
          params.add(title).add(markdown);
        } else {
          params.add(markdown).add(id);
        }
        connection.updateWithParams(sql, params, res -> {
          connection.close();
          if (res.succeeded()) {
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/wiki/" + title);
            context.response().end();
          } else {
            context.fail(res.cause());
            res.cause().printStackTrace();
          }
        });
      } else {
        context.fail(car.cause());
        car.cause().printStackTrace();
      }
    });
  }

  private void pageCreateHandler(RoutingContext context) {
    String pageName = context.request().getParam("name");
    String location = "/wiki/" + pageName;
    if (pageName == null || pageName.isEmpty()) {
      location = "/";
    }
    context.response().setStatusCode(303);
    context.response().putHeader("Location", location);
    context.response().end();
  }

  private void pageRenderingHandler(RoutingContext context) {
    String page = context.request().getParam("page");

    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        connection.queryWithParams(SQL_GET_PAGE, new JsonArray().add(page), fetch -> {
          connection.close();
          if (fetch.succeeded()) {
            JsonArray row = fetch.result().getResults()
                    .stream()
                    .findFirst()
                    .orElseGet(() -> new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN));
            Integer id = row.getInteger(0);
            String rawContent = row.getString(1);
            context.put("title", page);
            context.put("id", id);
            context.put("newPage", fetch.result().getResults().size() == 0 ? "yes" : "no");
            context.put("rawContent", rawContent);
            context.put("content", Processor.process(rawContent));
            context.put("timestamp", new Date().toString());

            templateEngine.render(context.data(), "templates/page.ftl", ar -> {
              if (ar.succeeded()) {
                context.response().putHeader("Content-Type", "text/html");
                context.response().end(ar.result());
              } else {
                context.fail(ar.cause());
              }
            });
          } else {
            context.fail(fetch.cause());
          }
        });
      } else {
        context.fail(car.cause());
      }
    });
  }

  private void indexHandler(RoutingContext context) {
    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        connection.query(SQL_ALL_PAGES, res -> {
          connection.close();
          if (res.succeeded()) {
            List<String> pages = res.result().getResults()
                    .stream()
                    .map(json -> json.getString(0))
                    .sorted()
                    .collect(Collectors.toList());

            context.put("title", "Wiki home");
            context.put("pages", pages);
            templateEngine.render(context.data(), "templates/index.ftl", ar -> {
                if (ar.succeeded()) {
                  context.response().putHeader("Content-Type", "text/html");
                  context.response().end(ar.result());
                } else {
                  context.fail(ar.cause());
                  LOGGER.error(ar.cause().getMessage());
                }
            });
          } else {
            context.fail(res.cause());
            LOGGER.error(res.cause().getMessage());
          }
        });
      } else {
        context.fail(car.cause());
        LOGGER.error(car.cause().getMessage());
      }
    });
  }

}
