/*
 * Copyright (C) 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.google.marvin.whereabout;

import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This utility class implements methods to get street address from lat-lon
 * using reverse geocoding API through HTTP.
 * TODO(chaitanyag): Add code to replace state abbreviations.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class StreetLocator {

  private static final HashMap<String, String> roadTypeMap =
      new HashMap<String, String>();

  private static final String ENCODING = "UTF-8";

  // URL for obtaining navigation directions
  private static final String URL_NAV_STRING =
    "http://maps.google.com/maps/nav?";
  //URL for obtaining reverse geocoded location
  private static final String URL_GEO_STRING =
    "http://maps.google.com/maps/geo?";
  
  /** Private Constructor for this utility class */
  private StreetLocator() {
  }
  
  /**
   * Queries the map server and obtains the street names at the specified
   * location. This is done by obtaining street name at specified location,
   * and at locations X meters to the N, S, E, and W of the specified location.
   * @param lat The latitude in degrees
   * @param lon The longitude in degrees
   * @return Returns the string array containing street names 
   */
  public static String[] getStreetIntersection(double lat, double lon) {
    HashSet<String> streets = new HashSet<String>();
    try {
      for (int i = 0; i < 5; i++) {
        // Find street address at lat-lon x meters to the N, S, E and W of
        // the given lat-lon
        String street =
            parseStreetName(getResult(makeNavURL(lat, lon, lat, lon)));
        if (street != null) {
          streets.add(street);
        }
        // get points 150m away, towards N, E, S, and W
        if (i < 4) {
          Location nextLoc = endLocation(lat, lon, i * 90, 15);
          lat = nextLoc.getLatitude();
          lon = nextLoc.getLongitude();
        }
      }
    } catch (MalformedURLException mue) {
      Log.d("Locator", "Malformed URL: " + mue.getMessage());
    } catch (IOException e) {
      Log.d("Locator", "Error reading from Map server: " + e.toString());
    } catch (JSONException e) {
      Log.d("Locator", "Error in JSON response: " + e.getMessage());
    }
    String[] st = new String[streets.size()];
    int i = 0;
    for (String s : streets) {
      st[i++] = s;
    }
    return st;
  }
  
  /**
   * Queries the map server and obtains the reverse geocoded address of the
   * specified location.
   * @param lat The latitude in degrees
   * @param lon The longitude in degrees
   * @return Returns the reverse geocoded address
   */
  public static String getAddress(double lat, double lon) {
    try {
      String resp = getResult(makeGeoURL(lat, lon));
      JSONObject jsonObj = new JSONObject(resp);
      int code = jsonObj.getJSONObject("Status").getInt("code");
      if (code == 200) {
        return extendShorts(jsonObj.getJSONArray("Placemark")
            .getJSONObject(0).getString("address"));
      }
    } catch (MalformedURLException mue) {
      Log.d("Locator", "Malformed URL: " + mue.getMessage());
    } catch (IOException e) {
      Log.d("Locator", "Error reading from Map server: " + e.toString());
    } catch (JSONException e) {
      Log.d("Locator", "Error in JSON response: " + e.getMessage());
    }
    return null;
  }

  /**
   * Parses the JSON response to extract the street name.
   * @param resp The String representation of the JSON response
   * @return Returns the street name
   * @throws JSONException
   */
  private static String parseStreetName(String resp) throws JSONException {
    JSONObject jsonObj = new JSONObject(resp);
    int code = jsonObj.getJSONObject("Status").getInt("code");
    if (code == 200) {
      return extendShorts(jsonObj.getJSONArray("Placemark")
          .getJSONObject(0).getString("address"));
    }
    return null;
  }
  
  /**
   * Sends a request to the specified URL and obtains the result from
   * the sever. 
   * @param url The URL to connect to
   * @return the server response
   * @throws IOException
   */
  private static String getResult(URL url) throws IOException {
    Log.d("Locator", url.toString());
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setDoInput(true);
    conn.setDoOutput(true);
    InputStream is = conn.getInputStream();
    String result = toString(is);
    return result;
  }
  
  /**
   * Prepares the URL to connect to navigation server, from the specified
   * start and end location coordinates
   * @param lat1 Start location latitude in degrees
   * @param lon1 Start location longitude in degrees
   * @param lat2 End location latitude in degrees
   * @param lon2 End location longitude in degrees
   * @return a well-formed URL
   * @throws MalformedURLException
   */
  private static URL makeNavURL(double lat1, double lon1,
                         double lat2, double lon2)
      throws MalformedURLException {
    StringBuilder url = new StringBuilder();
    url.append(URL_NAV_STRING).append("hl=").append(R.string.English)
        .append("&gl=").append(R.string.English)
        .append("&output=js&oe=utf8&q=from%3A").append(lat1).append(",")
        .append(lon1).append("+to%3A").append(lat2).append(",").append(lon2);
    return new URL(url.toString());
  }

  /**
   * Prepares the URL to connect to the reverse geocoding server from the
   * specified location coordinates.
   * @param lat latitude in degrees of the location to reverse geocode
   * @param lon longitude in degrees of the location to reverse geocode
   * @return
   * @throws MalformedURLException
   */
  private static URL makeGeoURL(double lat, double lon)
      throws MalformedURLException {
    StringBuilder url = new StringBuilder();    
    url.append(URL_GEO_STRING).append("q=").append(lat).append(",").append(lon);
    return new URL(url.toString());
  }

  /**
   * Reads an InputStream and returns its contents as a String.
   * @param inputStream The InputStream to read from.
   * @return The contents of the InputStream as a String.
   * @throws Exception
   */
  private static String toString(InputStream inputStream) throws IOException {
    StringBuilder outputBuilder = new StringBuilder();
    String string;
    if (inputStream != null) {
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(inputStream, ENCODING));
      while (null != (string = reader.readLine())) {
          outputBuilder.append(string).append('\n');
      }
    }
    return outputBuilder.toString();
  }
  
  /**
   * Replaces the short forms in the address by their longer forms, so that TTS
   * speaks the addresses properly. The short forms are replaced only if they
   * are followed by a non-letter and a non-space character, or if it is at the
   * end of the string.
   * @param addr The address from which to replace short forms
   * @return the modified address string
   */
  private static String extendShorts(String addr) {
    if (roadTypeMap.size() == 0) {
      populateRoadTypeMap();
    }
    Set<String> abbrevs = roadTypeMap.keySet();
    for (String abbrev : abbrevs) {
      addr = replaceAll(abbrev + "[\\W&&\\S]|" + abbrev + "\\s*$",
          addr, roadTypeMap.get(abbrev));
    }
    return addr;
  }

  /**
   * Replaces every occurrence of the pattern in the parent string by the
   * specified replacement string.  
   * @param regex The regular expression for the pattern to replace
   * @param str The parent string
   * @param repl The replacement string
   * @return
   */
  private static String replaceAll(String regex, String str, String repl) {
    Pattern p = Pattern.compile(regex);
    Matcher m = p.matcher(str);
    return m.replaceAll(repl);
  }
  
  /**
   * Computes the new location at a particular direction and distance from the
   * specified location using the inverse Vincenti formula. 
   * @param lat1 latitude of source location in degrees
   * @param lon1 longitude of source location in degrees
   * @param brng Direction in degrees wrt source location
   * @param dist Distance from the source location
   * @return the new location
   */
  private static Location endLocation(double lat1, double lon1,
      double brng, double dist) {
    double a = 6378137, b = 6356752.3142,  f = 1 / 298.257223563;
    double s = dist;
    double alpha1 = Math.toRadians(brng);
    double sinAlpha1 = Math.sin(alpha1), cosAlpha1 = Math.cos(alpha1);
    
    double tanU1 = (1 - f) * Math.tan(Math.toRadians(lat1));
    double cosU1 = 1 / Math.sqrt((1 + tanU1 * tanU1)), sinU1 = tanU1 * cosU1;
    double sigma1 = Math.atan2(tanU1, cosAlpha1);
    double sinAlpha = cosU1 * sinAlpha1;
    double cosSqAlpha = 1 - sinAlpha * sinAlpha;
    double uSq = cosSqAlpha * (a * a - b * b) / (b * b);
    double aa = 1 + uSq / 16384 * (4096 + uSq *
        (-768 + uSq * (320 - 175 * uSq)));
    double bb = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
    
    double sigma = s / (b * aa), sigmaP = 2 * Math.PI;
    double cos2SigmaM = 0, sinSigma = 0, deltaSigma = 0, cosSigma = 0;
    while (Math.abs(sigma - sigmaP) > 1e-12) {
      cos2SigmaM = Math.cos(2 * sigma1 + sigma);
      sinSigma = Math.sin(sigma);
      cosSigma = Math.cos(sigma);
      deltaSigma = bb * sinSigma * (cos2SigmaM + bb / 4 * (cosSigma *
          (-1 + 2 * cos2SigmaM * cos2SigmaM) -
        bb / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) *
        (-3 + 4 * cos2SigmaM * cos2SigmaM)));
      sigmaP = sigma;
      sigma = s / (b * aa) + deltaSigma;
    }
    double tmp = sinU1 * sinSigma - cosU1 * cosSigma * cosAlpha1;
    double lat2 = Math.atan2(sinU1 * cosSigma + cosU1 * sinSigma * cosAlpha1, 
        (1 - f) * Math.sqrt(sinAlpha * sinAlpha + tmp * tmp));
    double lambda = Math.atan2(sinSigma * sinAlpha1,
        cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1);
    double cc = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha));
    double ll = lambda - (1 - cc) * f * sinAlpha *
        (sigma + cc * sinSigma * (cos2SigmaM + cc * cosSigma *
            (-1 + 2 * cos2SigmaM * cos2SigmaM)));
    Location l = new Location(LocationManager.GPS_PROVIDER);
    l.setLatitude(Math.toDegrees(lat2));
    l.setLongitude(lon1 + Math.toDegrees(ll));
    return l;
  }
  
  /**
   * Adds mappings from abbreviations to full forms.
   */
  private static void populateRoadTypeMap() {
    roadTypeMap.put("St", "Street ");
    roadTypeMap.put("Fwy", "Freeway ");
    roadTypeMap.put("Hwy", "Highway ");
    roadTypeMap.put("Expy", "Expressway ");
    roadTypeMap.put("Blvd", "Boulevard ");
    roadTypeMap.put("Dr", "Drive ");
    roadTypeMap.put("Rd", "Road ");
    roadTypeMap.put("Ave", "Avenue ");
    roadTypeMap.put("Pkwy", "Parkway ");
  }
}
