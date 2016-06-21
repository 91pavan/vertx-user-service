package com.cisco.cmad.blogapp.vertx_user_service;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

public class CompanyHandler {
	
	private MongoClient client;
	private String siteId = null;
	private String companyId = null;
	private String deptId = null;

	public CompanyHandler(MongoClient client) {
		this.client = client;
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
    
}
