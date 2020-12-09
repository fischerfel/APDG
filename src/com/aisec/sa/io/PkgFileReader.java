package com.aisec.sa.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class PkgFileReader {
	private Set<String> pkgNameSet;
	
	public PkgFileReader(String absPath) throws FileNotFoundException, IOException, ParseException {
		JSONParser parser = new JSONParser();
		Object obj = parser.parse(new FileReader(absPath));
		JSONObject pkgJson = (JSONObject) obj;
		JSONArray pkgNames = (JSONArray) pkgJson.get("packages");
		
		if (pkgNames == null) {
			return;
		}
		
		this.pkgNameSet = new HashSet<String>();
		for (Object pkgNameObj : pkgNames) {
			String pkgName = (String) pkgNameObj;
			if (pkgName.length() > 0) {
				pkgName = pkgName.replaceAll("\\.",  Matcher.quoteReplacement(File.separator));
				this.pkgNameSet.add(pkgName);
			}
		}
	}
	
	public Set<String> getPkgNameList() {
		return this.pkgNameSet;
	}

}
