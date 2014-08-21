/*******************************************************************************
 * Copyright 2014 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/
package edu.isi.karma.kr2rml;

import java.io.PrintWriter;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isi.karma.modeling.Uris;

public class JSONKR2RMLRDFWriter implements KR2RMLRDFWriter{

	protected boolean firstObject = true;
	protected PrintWriter outWriter;
	protected ConcurrentHashMap<String, ConcurrentHashMap<String, JSONObject>> generatedObjectsByTriplesMapId;
	protected ConcurrentHashMap<String, JSONObject> generatedObjectsWithoutTriplesMap;
	protected ConcurrentHashMap<String, JSONObject> rootObjects = new ConcurrentHashMap<String, JSONObject>();
	protected ShortHandURIGenerator shortHandURIGenerator = new ShortHandURIGenerator();
	protected String rootTriplesMapId; 
	protected Set<String> rootTriplesMapIds;
	private static Set<String> numericLiteralTypes = new HashSet<String>();
	static {
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#decimal");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#integer");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#nonPositiveInteger");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#negativeInteger");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#long");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#int");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#short");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#byte");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#nonNegativeInteger");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#unsignedLong");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#unsignedInt");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#unsignedShort");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#unsignedInt");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#unsignedByte");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#positiveInteger");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#float");
		numericLiteralTypes.add("http://www.w3.org/2001/XMLSchema#double");
	}
	public JSONKR2RMLRDFWriter (PrintWriter outWriter) {
		this.outWriter = outWriter;
		generatedObjectsWithoutTriplesMap = new ConcurrentHashMap<String, JSONObject>();
		generatedObjectsByTriplesMapId = new ConcurrentHashMap<String, ConcurrentHashMap<String, JSONObject>>();
		rootTriplesMapIds = new HashSet<String>();
		outWriter.println("[");
	}

	@Override
	public void outputTripleWithURIObject(String subjUri, String predicateUri,
			String objectUri) {
		JSONObject subject = checkAndAddsubjUri(null, generatedObjectsWithoutTriplesMap, subjUri);
		JSONObject object = getGeneratedObject(generatedObjectsWithoutTriplesMap, objectUri);
		addValue(subject, predicateUri, object !=null? object : objectUri);
		rootObjects.remove(objectUri);
	}

	@Override
	public void outputTripleWithLiteralObject(String subjUri,
			String predicateUri, String value, String literalType) {
		JSONObject subject = checkAndAddsubjUri(null, generatedObjectsWithoutTriplesMap, subjUri);
		addValue(subject, predicateUri, convertValueWithLiteralType(literalType, value));
	}

	@Override
	public void outputQuadWithLiteralObject(String subjUri,
			String predicateUri, String value, String literalType, String graph) {
		outputTripleWithLiteralObject(subjUri, predicateUri, value, literalType);
	}

	private JSONObject checkAndAddSubjUri(String triplesMapId, String subjUri)
	{
		ConcurrentHashMap<String, JSONObject> generatedObjects = generatedObjectsByTriplesMapId.get(triplesMapId);
		if(null == generatedObjects)
		{
			generatedObjectsByTriplesMapId.putIfAbsent(triplesMapId, new ConcurrentHashMap<String, JSONObject>());
			generatedObjects = generatedObjectsByTriplesMapId.get(triplesMapId);
		}
		return checkAndAddsubjUri(triplesMapId, generatedObjects, subjUri);
	}
	private JSONObject checkAndAddsubjUri(String triplesMapId, ConcurrentHashMap<String, JSONObject> generatedObjects, String subjUri) {
		if (!generatedObjects.containsKey(subjUri)) {
			JSONObject object = new JSONObject();
			object.put("@id", subjUri);
			generatedObjects.putIfAbsent(subjUri, object);
			object = generatedObjects.get(subjUri);
			if(triplesMapId == null || rootTriplesMapIds.isEmpty() || rootTriplesMapIds.contains(triplesMapId))
			{
				rootObjects.put(subjUri, object);
			}
			return object;
		}
		return generatedObjects.get(subjUri);
	}

	private void addURIObject(PredicateObjectMap pom, String subjUri,  String predicateUri, String objectUri)
	{
		JSONObject subject = checkAndAddSubjUri(pom.getTriplesMap().getId(), subjUri);
		if(pom.getObject().getRefObjectMap() == null)
		{
			addValue(subject, predicateUri, objectUri);
			return;
		}
		String parentTriplesMapId = pom.getObject().getRefObjectMap().getParentTriplesMap().getId();		
		JSONObject object = getGeneratedObject(parentTriplesMapId, objectUri);
		if(object == null)
		{
			addValue(subject, predicateUri, objectUri);
			return;
		}

		addValue(subject, predicateUri, object);
		if(rootTriplesMapIds.isEmpty() || !rootTriplesMapIds.contains(pom.getTriplesMap().getId()))
		{
			rootObjects.remove(objectUri);
		}

	}

	private void addValue(JSONObject subject, String predicateUri, Object object) {
		if (subject.has(shortHandURIGenerator.getShortHand(predicateUri).toString()) || predicateUri.contains(Uris.RDF_TYPE_URI)) {
			String shortHandPredicateURI = shortHandURIGenerator.getShortHand(predicateUri).toString();
			JSONArray array = addValueToArray(subject, object,
					shortHandPredicateURI);
			subject.put(!shortHandPredicateURI.equalsIgnoreCase("rdf:type")?shortHandPredicateURI: "@type", array);
		}
		else
		{
			subject.put(shortHandURIGenerator.getShortHand(predicateUri).toString(), object);
		}
	}

	private JSONArray addValueToArray(JSONObject subject, Object object,
			String shortHandPredicateURI) {
		JSONArray array = null;
		if(subject.has(shortHandPredicateURI))
		{
			Object obj = subject.get(shortHandPredicateURI);
			if(obj != null)
			{
				if (obj instanceof JSONArray) {
					array = (JSONArray) obj;
				}
				else{
					array = new JSONArray();
					array.put(obj);
				}
			}
			else
			{
				array = new JSONArray();
			}
		}
		else
		{
			array = new JSONArray();
		}
		array.put(object);
		return array;
	}

	@Override
	public void finishRow() {

	}

	@Override
	public void flush() {
		//		finishRow();
		outWriter.flush();
	}

	@Override
	public void close() {
		for(JSONObject value : rootObjects.values())
		{
			collapseSameType(value);
			if (!firstObject) {
				outWriter.println(",");
			}
			firstObject = false;
			outWriter.print(value.toString(4));
		}
		outWriter.println("");
		outWriter.println("]");
		outWriter.close();
	}

	@Override
	public void outputTripleWithURIObject(PredicateObjectMap predicateObjectMap,
			String subjUri, String predicateUri,
			String objectUri) {

		addURIObject(predicateObjectMap, subjUri, predicateUri, objectUri);
	}


	@Override
	public void outputTripleWithLiteralObject( PredicateObjectMap predicateObjectMap, 
			String subjUri, String predicateUri, String value,
			String literalType) {
		JSONObject subject = checkAndAddSubjUri(predicateObjectMap.getTriplesMap().getId(), subjUri);
		//TODO should literal type be ignored?
		addValue(subject, predicateUri, convertValueWithLiteralType(literalType, value));
	}

	@Override
	public void outputQuadWithLiteralObject( PredicateObjectMap predicateObjectMap, 
			String subjUri, String predicateUri, String value,
			String literalType, String graph) {

		JSONObject subject = checkAndAddSubjUri(predicateObjectMap.getTriplesMap().getId(), subjUri);
		//TODO should literal type be ignored?
		//TODO should graph be ignored?
		addValue(subject, predicateUri, convertValueWithLiteralType(literalType, value));

	}

	public void addPrefixes(Collection<Prefix> prefixes) {
		shortHandURIGenerator.addPrefixes(prefixes);
	}

	public JSONObject getGeneratedObject(String triplesMapId, String generatedObjectUri)
	{
		ConcurrentHashMap<String, JSONObject> generatedObjects = this.generatedObjectsByTriplesMapId.get(triplesMapId);
		return getGeneratedObject(generatedObjects, generatedObjectUri);
	}

	private JSONObject getGeneratedObject(
			ConcurrentHashMap<String, JSONObject> generatedObjects, String generatedObjectUri) {
		if(null == generatedObjects)
		{
			return null;
		}
		return generatedObjects.get(generatedObjectUri);
	}

	public void addRootTriplesMapId(String rootTriplesMapId) {
		rootTriplesMapIds.add(rootTriplesMapId);
	}
	public void addRootTriplesMapIds(Collection<String> rootTriplesMapIds) {
		this.rootTriplesMapIds.addAll(rootTriplesMapIds);
	}

	private void collapseSameType(JSONObject obj) {
		if (obj.has("@type")) {
			JSONArray array = obj.getJSONArray("@type");
			Set<String> types = new HashSet<String>();
			for (int i = 0; i < array.length(); i++) {
				types.add(array.getString(0));
				array.remove(0);
			}
			for (String type : types)
				array.put(type);
		}
		for (Object key : obj.keySet()) {
			Object value = obj.get(key.toString());
			if (value instanceof JSONObject)
				collapseSameType((JSONObject)value);
			if (value instanceof JSONArray) {
				JSONArray array = (JSONArray)value;
				for (int i = 0; i < array.length(); i++) {
					Object o = array.get(i);
					if (o instanceof JSONObject)
						collapseSameType((JSONObject) o);
				}
			}
		}
	}

	private Object convertValueWithLiteralType(String literalType, String value) {
		if (numericLiteralTypes.contains(literalType)) {
			ParsePosition parsePosition = new ParsePosition(0);
			Number n = NumberFormat.getNumberInstance(Locale.US).parse(value, parsePosition);
			if (parsePosition.getErrorIndex() != -1 || parsePosition.getIndex() < value.length())
				return value;
			else
				return n;
		}

		if (literalType != null && literalType.equals("http://www.w3.org/2001/XMLSchema#boolean")) {
			if (value.trim().equalsIgnoreCase("false"))
				return false;
			else if (value.trim().equalsIgnoreCase("true"))
				return true;
		}
		return value;
	}

}