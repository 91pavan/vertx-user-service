package com.cisco.cmad.blogapp.vertx_user_service;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class EventBusConsumer {
	
	
	// duplicating event bus consumer in UserServiceApp as multicast doesn't work on cloud
	
	public void consumer(EventBus eb, MongoClient client) {
    	
    	eb.consumer("user.creation", message -> {
    		
  		Object obj = message.body();
  		  
  		JsonObject obj1 = (JsonObject) obj;
  		  
  		String username = obj1.getString("userName");
  		String password = obj1.getString("password");
  		  
  		JsonObject blogUsers = new JsonObject().put("userName", username).put("password", password);
  		  
  		client.findOne("blog_users", blogUsers, null, lookup -> {
            // error handling
            if (lookup.failed()) {
              return;
            }
            if(lookup.result() != null) {
                // already exists
                // do nothing
            } else {
  		  
	  		  client.save("blog_users", blogUsers, insert -> {
		          // error handling
	              if (insert.failed()) {
	                return;
	              }
	             
	              System.out.println("User object inserted to blog_users table");
	              blogUsers.put("_id", insert.result());
	              
	            });
	  		  
            }
            });
    	});
    	return;
    }
}
