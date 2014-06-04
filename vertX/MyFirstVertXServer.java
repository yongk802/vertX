package vertx_testing;

import java.io.File;
import java.net.InetSocketAddress;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;
/*
 * Author: Yong Kim
 * Date: May 6, 2014
 * Description: This is a very small example of what some vertX code can do to quickly put up a fully
 * functional server.
 * To run, install vertx, drop this file into a folder called vert_testing and run: vertx run MyFirstVertXServer.java
 * 
 */

public class MyFirstVertXServer extends Verticle {

	/* not recommended, but you can run this embedded for easier debugging through an IDE, such as Eclipse
	 * by using this as your main rather than implementing start()
	public static void main(String[] args) throws Exception {
		Vertx vertx = VertxFactory.newVertx();
	 */

	public void start() {
		HttpServer server = vertx.createHttpServer();
		RouteMatcher routeMatcher = new RouteMatcher();
		final Logger logger = container.logger();
		final EventBus eb = vertx.eventBus();
		final JsonObject obj = new JsonObject();

		// add special handling for certain request URLs
		routeMatcher.get("/animals/dogs", new Handler<HttpServerRequest>() {
			public void handle(HttpServerRequest req) {
				req.response().end("You requested dogs");
			}
		});
		routeMatcher.get("/animals/cats", new Handler<HttpServerRequest>() {
			public void handle(HttpServerRequest req) {
				req.response().end("You requested cats");
			}
		});
		// otherwise build a special "search" page with an embedded RAMP video that will do some logging
		// if a query with the term containing "magic" is recognized, we should notify certain handlers and log that because it's special.
		routeMatcher.noMatch(new Handler<HttpServerRequest>() {
			public void handle(HttpServerRequest req) {
				System.out.println("Got request: " + req.uri());
				req.response().putHeader("Content-Type", "text/html; charset=UTF-8");
				req.response().end("" +
						"<html>" +
						"<head>" +
						"<style>" +
						"div.vid{margin:5px;padding: 5px;border:1px solid #0000ff;height:auto;width:auto;float:left;text-align:center;}div.search{text-align:left;font-weight:normal;width:500px;margin:5px;}" +
						"</style>" +
						"</head>" +
						"<body><h1>vert.X Test Page</h1>" +
						"<h2>(Please excuse the lack of styling)</h2>" + 
						"<div class=\"search\">" +
						"<a href=\"animals/cats\">Mystery Link One</a></br>" +
						"<a href=\"animals/dogs\">Mystery Link Two</a></br>" +
						"<form>Search: <input type=\"search\" name=\"search\"></form>" +
						"<div><p>Try searching for a string that contains \"magic\" and you will win a prize for being the first!</p></div>" +
						"</div>" +
						"<div class=\"vid\">" +
						"<h3>Patty Buehler Speaking on Business Development in Sydney</h3>" + 
						"<script src=\"//embed.ramp.com/amd.js\" data-ramp-playlist-id=\"\" data-ramp-item-id=\"92298902\" data-ramp-widget=\"636-6587\" data-ramp-api-key=\"GpoEF0GAHLr0IUicuGhcGb3cINMs5fJG\"></script>" +
						"</div>" +
						"</body>" +
						"</html>");
				// 
				if(req.query() != null && req.query().contains("magic") && obj.getString("magicTerm") == null)
				{
					System.out.println("Magical Logging!");
					vertXLogAndNotify(req.query(), logger, req.localAddress(), eb, obj);
				}
			}
		});server.requestHandler(routeMatcher).listen(1234, "localhost");
	}


	private void vertXLogAndNotify(final String query, Logger logger, final InetSocketAddress inetSocketAddress, final EventBus eb, final JsonObject obj) 
	{
		// should log every query containing "magic"
		logger.info("magic query detected: " + query);
		File jsonLog = new File("files/foo.json");
		if(jsonLog.exists())
		{
			vertx.fileSystem().deleteSync("files/foo.json", true);
		}
		vertx.fileSystem().open("files/foo.json", new AsyncResultHandler<AsyncFile>() {
			public void handle(AsyncResult<AsyncFile> ar) {
				if (ar.succeeded()) {
					final AsyncFile asyncFile = ar.result();
					// will only log the first query containing "magic"
					obj.putString("magicTerm", query.replaceAll("^search=", ""));
					obj.putString("requesterAddress", inetSocketAddress.toString().substring(1, inetSocketAddress.toString().length()));
					Buffer buff = new Buffer(obj.toString(),"UTF-8");
					System.out.println(obj.toString());
					asyncFile.write(buff, Integer.valueOf(buff.length()).longValue(), new AsyncResultHandler<Void>() {
						public void handle(AsyncResult<Void> ar) {
							if (ar.succeeded()) {
								System.out.println("Written ok! Sending a message using EventBus API!");
								@SuppressWarnings("rawtypes")
								Handler<Message> myHandler = new Handler<Message>() {
									public void handle(Message message) {
										System.out.println("I received a magical message! " + query);
									}
								};
								eb.registerHandler("my.address", myHandler, new AsyncResultHandler<Void>() {
									public void handle(AsyncResult<Void> asyncResult) {
										System.out.println("The handler has been registered across the cluster ok? " + asyncResult.succeeded());
									}
								});
							} else {
								System.out.println("Failed to write" + ar.cause());
							}
							asyncFile.close();
						}
					});
				} else {
					System.out.println("Failed to open file" + ar.cause());
				}
			}
		});
	}
}
