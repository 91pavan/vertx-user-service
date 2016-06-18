package com.cisco.cmad.blogapp.vertx_user_service;

import java.util.List;
import java.util.Properties;

import com.cisco.cmad.blogapp.config.AppConfig;
import com.cisco.cmad.blogapp.utils.Base64Util;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;


public class UserServiceApp extends AbstractVerticle
{
	private MongoClient client;
	private String siteId = null;
	private String companyId = null;
	private String deptId = null;
    public static String cId = null;
    public static String sId = null;
    public static String dId = null;
    public EventBus eb = null;
	
    JsonObject userObj = null;
    Properties prop = null;
    
    AppConfig appConfig = new AppConfig();
    Base64Util base64Util = new Base64Util();
    
    
	public void start(Future<Void> startFuture) {
		
    	System.out.println("vertx-user-service verticle started!");
    	
    	// read from config.properties
    	prop = new Properties();
    	
    	String mongo_host = appConfig.getMongoHostConfig(prop);
    	String mongo_port = appConfig.getMongoPortConfig(prop);
    	
    	// construct the conf which will be used to initialize the mongoClient object
    	JsonObject conf = new JsonObject()
        .put("connection_string", "mongodb://" + mongo_host + ":" + mongo_port)
        .put("db_name", appConfig.getMongoDbConfig(prop));
    	client = MongoClient.createShared(vertx, conf);
    	
    	// initialize the eventBus
    	eb = vertx.eventBus();
    	// start the HTTP server
    	HttpServer(Integer.parseInt(appConfig.getAppPortConfig(prop)));
    	startFuture.complete();
    }
    
    public void stop(Future<Void> stopFuture) throws Exception{
    	System.out.println("vertx-user-service verticle stopped!");
    	stopFuture.complete();
    }
    
    private void HttpServer(int port) {
    	
    	HttpServer server = vertx.createHttpServer();
    	Router router = Router.router(vertx);
    	router.route().handler(BodyHandler.create());
    	    	    	
    	// handle cors issue
    	router.route().handler(CorsHandler.create("*")
    		      .allowedMethod(HttpMethod.GET)
    		      .allowedMethod(HttpMethod.POST)
    		      .allowedMethod(HttpMethod.PUT)
    		      .allowedMethod(HttpMethod.DELETE)
    		      .allowedMethod(HttpMethod.OPTIONS)
    		      .allowedHeader("X-PINGARUNER")
    		      .allowedHeader("*")
    		      .allowedHeader("Content-Type")
    		      .allowedHeader("Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept, Authorization"));

        
    	//login auth API
    	loginUser(router);
    	
    	//register API
    	registerUser(router);
    	 
    	getSessionDetails(router);
        	
    	getCompany(router);
    	
    	getSites(router);
    	
    	getDept(router);
    	System.out.println(port);
    	    	 	
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
			    			    
			    ctx.addCookie(Cookie.cookie("userName", userDetails.getString("userName")));
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
    
    public String insertSites(String companyId, String deptName, RoutingContext ctx) {
    	
    	String defaultSiteName = "London";
    	    	
    	JsonObject sitesObj = new JsonObject().put("companyId", companyId).put("siteName", defaultSiteName);
  	  	client.findOne("site", sitesObj, null, lookup -> {
            // error handling
            if (lookup.failed()) {
              ctx.fail(500);
              return;
            }
            System.out.println("Site Lookup result : " + lookup.result());
            if(lookup.result() != null) {
                // already exists
                // do nothing
            } else {
            	
            	JsonObject site = new JsonObject().put("companyId", companyId).put("siteName", defaultSiteName);
            	client.insert("site", site, insert -> {
                    // error handling
                    if (insert.failed()) {
                      ctx.fail(500);
                      return;
                    }
                    System.out.println("Site insert result : " + insert.result());
                    // add the generated id to the dept object
    	            site.put("_id", insert.result());
    	            System.out.println(site.encode());
    	            siteId = insert.result();
                    this.insertDept(companyId, siteId, deptName, ctx);
                  });
            }
          });
  	  	
  	  	return siteId;
    }
    
    public String insertDept(String companyId, String siteId, String deptName, RoutingContext ctx) {
    	
    	
    	JsonObject deptObj = new JsonObject().put("companyId", companyId).put("siteId", siteId);
  	  	client.findOne("dept", deptObj, null, lookup -> {
            // error handling
            if (lookup.failed()) {
              ctx.fail(500);
              return;
            }
            System.out.println("Dept Lookup result : " + lookup.result());
            if(lookup.result() != null) {
                // already exists
                // do nothing
            } else {
            	JsonObject dept = new JsonObject().put("companyId", companyId).put("siteId", siteId).put("deptName", deptName);
            	client.insert("dept", dept, insert -> {
                    // error handling
                    if (insert.failed()) {
                      ctx.fail(500);
                      return;
                    }
                    System.out.println("Dept insert result : " + insert.result());
                    // add the generated id to the dept object
    	            dept.put("_id", insert.result());
    	            System.out.println(dept.encode());
    	            deptId = insert.result();
                    
                  });
            }
          });
  	  	  	  	
  	  	return deptId;
    }
    
    public void insertCompany(JsonObject userDetails, String deptName, RoutingContext ctx) {
    	
    	JsonObject companyObj = new JsonObject().put("companyName", userDetails.getString("companyName")).put("subdomain", userDetails.getString("subdomain"));
  	  	client.findOne("company", companyObj, null, lookup -> {
            // error handling
            if (lookup.failed()) {
              ctx.fail(500);
              return;
            }
            
            System.out.println("Company Lookup result : " + lookup.result());
            
            if(lookup.result() != null) {
            	System.out.println(lookup.result());
                // already exists
                // ctx.fail(500);
            } else {
            	JsonObject company = new JsonObject().put("companyName", userDetails.getString("companyName")).put("subdomain", userDetails.getString("subdomain"));
            	client.insert("company", company, insert -> {
                    // error handling
                    if (insert.failed()) {
                      ctx.fail(500);
                      return;
                    }
                    System.out.println("Company insert result : " + insert.result());
                    // add the generated id to the dept object
    	            company.put("_id", insert.result());
    	            companyId = insert.result();
    	            this.insertSites(companyId, deptName, ctx);
                  });
            }
          });
    }
    
    public void registerUser(Router router) {
    	router.post("/Services/rest/user/register").handler(ctx -> {

		      JsonObject userDetails = ctx.getBodyAsJson();
		      Boolean isCompany = userDetails.getBoolean("isCompany"); 		      
		      if( userDetails.containsKey("isCompany") && isCompany) {  
		    	  vertx.executeBlocking(future -> {
		    		  // Call some blocking API that takes a significant amount of time to return
		    		  this.insertCompany(userDetails, userDetails.getString("deptName"), ctx);
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
    

	public void getCompany(Router router) {
    	router.get("/Services/rest/company").handler(ctx -> {

    		client.find("company", new JsonObject(), lookup -> {
    	        // error handling
    	        if (lookup.failed()) {
    	          ctx.fail(500);
    	          return;
    	        }

    	        List<JsonObject> company = lookup.result();

    	        if (company == null || company.size() == 0) {
    	          ctx.fail(404);
    	          
    	        } else {
    	        	    	        	
    	        	ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		            ctx.response().end(company.toString());
    	        }
    	      });
    	});
    }
    
    public void getSites(Router router) {
    	router.get("/Services/rest/company/:id/sites").handler(ctx -> {
    		
    		client.find("site", new JsonObject().put("companyId", ctx.request().getParam("id")), lookup -> {
    	        // error handling
    	        if (lookup.failed()) {
    	          ctx.fail(500);
    	          return;
    	        }

    	        List<JsonObject> site = lookup.result();

    	        if (site == null || site.size() == 0) {
    	          ctx.fail(404);
    	        } else {
    	        	
    	        	ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		            ctx.response().end(site.toString());
    	        }
    	      });
    	});
    }
    
    
    public void getDept(Router router) {
    	router.get("/Services/rest/company/:id/sites/:siteid/departments").handler(ctx -> {
    		
    		client.find("dept", new JsonObject().put("siteId", ctx.request().getParam("siteid")), lookup -> {
    	        // error handling
    	        if (lookup.failed()) {
    	          ctx.fail(500);
    	          return;
    	        }

    	        List<JsonObject> dept = lookup.result();

    	        if (dept == null || dept.size() == 0) {
    	          ctx.fail(404);
    	        } else {
    	        	
    	        	ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		            ctx.response().end(dept.toString());
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
