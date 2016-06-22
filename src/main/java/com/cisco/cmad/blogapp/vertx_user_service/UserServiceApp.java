package com.cisco.cmad.blogapp.vertx_user_service;

import java.util.Properties;

import com.cisco.cmad.blogapp.config.AppConfig;
import com.cisco.cmad.blogapp.config.ZooConfig;
import com.cisco.cmad.blogapp.utils.Base64Util;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;


public class UserServiceApp extends AbstractVerticle
{
	private MongoClient client;
	
    public static String cId = null;
    public static String sId = null;
    public static String dId = null;
    public EventBus eb = null;
	
    JsonObject userObj = null;
    Properties prop = null;
    
    AppConfig appConfig = new AppConfig();
    Base64Util base64Util = new Base64Util();
    HTTPCorsHandler corsHandler = new HTTPCorsHandler();
    ZooConfig zooConfig = new ZooConfig("localhost");
    
    EventBusConsumer eventBusConsumer = new EventBusConsumer();
    
    CompanyHandler companyHandler = null;
    
    public MongoClient getClient(JsonObject conf) {
    	return MongoClient.createShared(vertx, conf);
    }
    
	public void start(Future<Void> startFuture) {
		
    	System.out.println("vertx-user-service verticle started!");
    	
    	// construct the conf which will be used to initialize the mongoClient object
    	JsonObject conf = new JsonObject()
        .put("connection_string", "mongodb://" + zooConfig.readDbHostConfig() + ":" + zooConfig.readDbPortConfig())
        .put("db_name", zooConfig.readDbNameConfig());
    	
    	// get mongo client
    	client = getClient(conf);
    	
    	companyHandler = new CompanyHandler(client);
    	
    	// initialize the eventBus. to be used to publish messages
    	eb = vertx.eventBus();
    	
    	eventBusConsumer.consumer(eb, client);
    	
    	// start the HTTP server
    	HttpServer(Integer.parseInt(zooConfig.readAppPortConfig()));
    	
    	startFuture.complete();
    }
    
    public void stop(Future<Void> stopFuture) throws Exception{
    	System.out.println("vertx-user-service verticle stopped!");
    	zooConfig.close();
    	stopFuture.complete();
    }
    
    private void HttpServer(int port) {
    	
    	HttpServer server = vertx.createHttpServer();
    	Router router = Router.router(vertx);
    	router.route().handler(BodyHandler.create());
    	    	    	
    	// handle cors issue
    	router.route().handler(corsHandler.getAllowedMethodsAndHeaders());

    	//login auth API
    	loginUser(router);
    	
    	//register API
    	registerUser(router);
    	 
    	getSessionDetails(router);
        	
    	companyHandler.getCompany(router);
    	
    	companyHandler.getSites(router);
    	
    	companyHandler.getDept(router);
    	    	 	
    	server.requestHandler(router::accept).listen(port);

    }
    
    public void getSessionDetails(Router router) {
    	    	
    	router.get("/Services/rest/user").handler(ctx -> {
    		  
    		String authHeader = ctx.request().headers().get("Authorization");
    		
    		String decodedString = base64Util.decode(authHeader);
    		String userName = base64Util.getUserName(decodedString);
    		String password = base64Util.getPassword(decodedString);  
    		
	    	  client.findOne("users", new JsonObject().put("userName", userName).put("password", password), null, lookup -> {
	    	        // error handling
	    	        if (lookup.failed()) {
	    	          ctx.fail(500);
	    	          return;
	    	        }

	    	        JsonObject user = lookup.result();

	    	        if (user == null || user.size() == 0) {
	    	          ctx.fail(404);
	    	        } else {
	    	          ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
	    	          ctx.response().end(user.encode());
	    	        }
	    	      });
    		});
    	
    }
   
    public void loginUser(Router router) {
    	router.post("/Services/rest/user/auth").handler(ctx -> {    	         	      
		      JsonObject userDetails = ctx.getBodyAsJson();
		      
		      client.findOne("users", new JsonObject().put("userName", userDetails.getString("userName"))
		      .put("password", userDetails.getString("password")), null, lookup -> {
		    		    	 
		      // error handling
		      if (lookup.failed()) {
		        ctx.fail(500);
		        return;
		      }
		      JsonObject user = lookup.result();
		
		      if (user != null && user.size() > 0) {
			    			    
		    	ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
	            ctx.response().setStatusCode(200);
	            ctx.response().end(userDetails.encode());
	            
		      } else {
		        ctx.fail(404);
		        return;
		      }
		      
		    });
    	});
    }
    
    public void registerUser(Router router) {
    	router.post("/Services/rest/user/register").handler(ctx -> {

		      JsonObject userDetails = ctx.getBodyAsJson();
		      Boolean isCompany = userDetails.getBoolean("isCompany"); 		      
		      if( userDetails.containsKey("isCompany") && isCompany) {
		    	  
		    	  vertx.executeBlocking(future -> {
		    		  // Call some blocking API that takes a significant amount of time to return
		    		  companyHandler.insertCompany(userDetails, userDetails.getString("deptName"), ctx);
		    		  future.complete();
		    		}, res -> {
		    		  System.out.println("The result is: " + res.result());
		    		});

		      }
		      
		      client.findOne("users", new JsonObject().put("userName", userDetails.getString("userName"))
		      .put("email", userDetails.getString("email")), null, lookup -> {
		    		    	 
		      // error handling
		      if (lookup.failed()) {
		        ctx.fail(500);
		        return;
		      }

		      JsonObject user = lookup.result();
		
		      if (user != null) {
		    	  // already exists
		    	  ctx.fail(500);
		      } else {
		    	  if( userDetails.containsKey("isCompany") && isCompany){
		    	  userObj = new JsonObject().put("userName", userDetails.getString("userName")).put("password", userDetails.getString("password"))
		    			  .put("email", userDetails.getString("email")).put("first", userDetails.getString("first"))
		    			  .put("last", userDetails.getString("last")).put("companyId", cId).put("siteId", sId)
		    			  .put("deptId", dId);
		    	  } else {
		    		  
		    		  userObj = new JsonObject().put("userName", userDetails.getString("userName")).put("password", userDetails.getString("password"))
			    			  .put("email", userDetails.getString("email")).put("first", userDetails.getString("first"))
			    			  .put("last", userDetails.getString("last")).put("companyId", userDetails.getString("companyId")).put("siteId", userDetails.getString("siteId"))
			    			  .put("deptId", userDetails.getString("deptId"));
		    	  }
		    	  client.insert("users", userObj, insert -> {
		              // error handling
		              if (insert.failed()) {
		                ctx.fail(500);
		                return;
		              }
		              // add the generated id to the user object
		              userObj.put("id", insert.result());
		              
		              // publish to event bus
		              eb.publish("user.creation", userObj);
		              
		              ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		              ctx.response().end(userObj.encode());
		            });

		        return;
		      }
    	      
    	    });
    	});
    }
   
    
    public static void main( String[] args )
    {	
    	ClusterManager mgr = new HazelcastClusterManager();
		VertxOptions options = new VertxOptions().setWorkerPoolSize(10).setClusterManager(mgr);
		//Vertx vertx = Vertx.factory.vertx(options);
		
		Vertx.clusteredVertx(options, res -> {
		  if (res.succeeded()) {
		    Vertx vertx = res.result();
		    vertx.deployVerticle("com.cisco.cmad.blogapp.vertx_user_service.UserServiceApp");
		  } else {
		    // failed!
		  }
		});
    	
    }
}
