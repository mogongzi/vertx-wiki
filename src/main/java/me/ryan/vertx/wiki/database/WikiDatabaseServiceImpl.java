package me.ryan.vertx.wiki.database;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.SingleHelper;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.sql.SQLClientHelper;

import java.util.List;
import java.util.Map;

public class WikiDatabaseServiceImpl implements WikiDatabaseService {

    private final Map<SqlQuery, String> sqlQueries;
    private final JDBCClient dbClient;

    public WikiDatabaseServiceImpl(io.vertx.ext.jdbc.JDBCClient dbClient, Map<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        this.sqlQueries = sqlQueries;
        this.dbClient = new JDBCClient(dbClient);

        SQLClientHelper.usingConnectionSingle(this.dbClient, conn -> conn
                .rxExecute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE))
                .andThen(Single.just(this)))
                .subscribe(SingleHelper.toObserver(readyHandler));
    }

    @Override
    public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
        dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES))
                .flatMapPublisher(res -> {
                    List<JsonArray> results = res.getResults();
                    return Flowable.fromIterable(results);
                })
                .map(json -> json.getString(0))
                .sorted()
                .collect(JsonArray::new, JsonArray::add)
                .subscribe(SingleHelper.toObserver(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
        dbClient.rxQueryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(name))
                .map(result -> {
                    if (result.getNumRows() > 0) {
                        JsonArray row = result.getResults().get(0);
                        return new JsonObject()
                                .put("found", true)
                                .put("id", row.getInteger(0))
                                .put("rawContent", row.getString((1)));
                    } else {
                        return new JsonObject().put("found", false);
                    }
                }).subscribe(SingleHelper.toObserver(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService fetchPageById(int id, Handler<AsyncResult<JsonObject>> resultHandler) {
        Single<ResultSet> resultSet = dbClient.rxQueryWithParams(sqlQueries.get(SqlQuery.GET_PAGE_BY_ID), new JsonArray().add(id));
        resultSet.map(result -> {
            if (result.getNumRows() > 0) {
                JsonObject row = result.getRows().get(0);
                return new JsonObject()
                        .put("found", true)
                        .put("id", row.getInteger("ID"))
                        .put("name", row.getString("NAME"))
                        .put("content", row.getString("CONTENT"));
            } else {
                return new JsonObject().put("found", false);
            }
        }).subscribe(SingleHelper.toObserver(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray data = new JsonArray().add(title).add(markdown);
        dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), data)
                .ignoreElement().subscribe(CompletableHelper.toObserver(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray data = new JsonArray().add(markdown).add(id);
        dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), data)
                .ignoreElement()
                .subscribe(CompletableHelper.toObserver(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray data = new JsonArray().add(id);
        dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data)
                .ignoreElement()
                .subscribe(CompletableHelper.toObserver(resultHandler));
        return this;
    }

    @Override
    public WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES_DATA))
                .map(ResultSet::getRows)
                .subscribe(SingleHelper.toObserver(resultHandler));
        return this;
    }
}
