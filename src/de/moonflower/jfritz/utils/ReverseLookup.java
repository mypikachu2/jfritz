/*
 *
 * Created on 05.05.2005
 *
 */
package de.moonflower.jfritz.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.moonflower.jfritz.struct.Person;
import de.moonflower.jfritz.struct.PhoneNumber;

/**
 * Class for telephone number reverse lookup using "dasoertliche.de"
 *
 * @author Arno Willig
 *
 */
public class ReverseLookup {

	public static Person lookup(PhoneNumber number) {
		Person newPerson;
		if (number.isMobile()) {
			newPerson = new Person();
			newPerson.addNumber(number);
			Debug.msg("Adding mobile " + number.getFullNumber());
		} else if (number.isFreeCall()) {
			newPerson = new Person("", "FreeCall");
			newPerson.addNumber(number);
		} else {
			newPerson = lookupDasOertliche(number.getAreaNumber());
		}
		return newPerson;
	}

	/**
	 * Static method for looking up entries from "dasoertliche"
	 *
	 * @param number
	 * @return name
	 */
	public static Person lookupDasOertliche(String number) {
		Debug.msg("Looking up " + number + "...");
		URL url = null;
		URLConnection urlConn;
		DataOutputStream printout;
		String data = "";
		Person newPerson;

		String urlstr = "http://www.dasoertliche.de/DB4Web/es/oetb2suche/home.htm?main=Antwort&s=2&kw_invers="
				+ number;
		try {
			url = new URL(urlstr);
			if (url != null) {

				URLConnection con;
				try {
					con = url.openConnection();

					// Get response data
					BufferedReader d = new BufferedReader(
							new InputStreamReader(con.getInputStream()));
					int i = 0;
					String str = "";

					while ((i < 700) && (null != ((str = d.readLine())))) {
						data += str;
						i++;
					}
					d.close();
					Pattern p = Pattern
							.compile("<a class=\"blb\" href=\"[^\"]*\">([^<]*)</a><br>([^<]*)</td>");
					Matcher m = p.matcher(data);
					if (m.find()) {
						Debug.msg(3, "Pattern: " + m.group(1).trim());
						Debug.msg(3, "Pattern: " + m.group(2).trim());
						String line1 = m.group(1).trim();
						String line2 = m.group(2).trim();

						String[] split = line1.split(" ", 2);
						String firstname = "", lastname = "", company = "", address = "", zipcode = "", city = "";
						lastname = split[0];
						if (split.length > 1) {
							firstname = " " + split[1];
							Debug.msg("*" + firstname + "*"
									+ firstname.indexOf("  "));
							if ((firstname.indexOf("  ") > -1)
									&& (firstname.indexOf("  u.") == -1)) {
								company = firstname.substring(
										firstname.indexOf("  ")).trim();
								firstname = firstname.substring(0,
										firstname.indexOf("  ")).trim();
							} else {
								firstname = firstname.replace("  u. ", " und ");
							}
						}
						firstname = firstname.trim();
						split = line2.split(", ", 2);
						String zipcity = "";
						if (split.length > 1) {
							address = split[0].trim();
							zipcity = split[1].trim();
						} else {
							zipcity = split[0].trim();
							address = "";
						}
						split = zipcity.split(" ");
						if (split.length > 1) {
							zipcode = split[0].trim();
							city = split[1].trim();
						} else {
							city = split[0].trim();
						}

						String[] splitNames, splitAddress, splitPostCodeCity;
						splitAddress = m.group(2).trim().split(",* ");
						Debug.msg(splitAddress[0]);
						splitPostCodeCity = splitAddress[1].split(" ", 2);

						newPerson = new Person(firstname, company, lastname,
								address, zipcode, city, "");
						if (company.length() > 0) {
							newPerson.addNumber(number, "business");
						} else {
							newPerson.addNumber(number, "home");
						}
						return newPerson;
					}
				} catch (IOException e1) {
					Debug.err("Error while retrieving " + urlstr);
				}
			}
		} catch (MalformedURLException e) {
			Debug.err("URL invalid: " + urlstr);
		}
		newPerson = new Person();
		newPerson.addNumber(number, "home");
		return newPerson;
	}

}