package com.cisco.cmad.blogapp.utils;

import java.util.Base64;

public class Base64Util {
	
	public String decode(String s) {
		s = s.split(" ")[1];
		String val = null;
		if(s != null) {
			val = new String(Base64.getDecoder().decode(s));
		}
		return val;
	}
	
	public String getUserName(String decodedString) {
		return decodedString.split(":")[0];
	}
	
	public String getPassword(String decodedString) {
		return decodedString.split(":")[1];
	}
	
}
