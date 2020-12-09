package com.aisec.sa.io;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class FeatureReader {

	private Map<String, RealVector> featureMap;

	public FeatureReader(String path, int embeddingDimension) throws Exception {
		if (path == null)
			throw new Exception();

		JSONParser parser = new JSONParser();

		try {
			Object obj = parser.parse(new FileReader(path));
			JSONObject jsonObject = (JSONObject) obj;
			Set<String> featureNameSet = jsonObject.keySet();
			if (featureNameSet == null)
				throw new Exception("Could not retreive features from file");

			this.featureMap = new HashMap<String, RealVector>();
			for (String feature : featureNameSet) {
				JSONArray featureEmbedding = (JSONArray) jsonObject.get(feature);
				if (featureEmbedding == null)
					throw new Exception("Could not retreive embedding for feature");
				if (featureEmbedding.size() != embeddingDimension)
					throw new Exception("Incorrect feature embedding dimension");
				RealVector embedding = new ArrayRealVector(embeddingDimension);
				for (int i = 0; i < featureEmbedding.size(); i++ ) {
					embedding.setEntry(i, (Double) featureEmbedding.get(i));
				}
				if (featureMap.containsKey(feature))
					throw new Exception("Feature already contained in map");
				featureMap.put(feature, embedding);
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	public Map<String, RealVector> getFeatureMap() {
		return featureMap;
	}
}
