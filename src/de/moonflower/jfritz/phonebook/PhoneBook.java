package de.moonflower.jfritz.phonebook;

/**
 * This is the phonebook
 *
 * @author Robert Palmer
 *
 * TODO: Cellrenderer for PrivateCell
 *
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import de.moonflower.jfritz.Main;
import de.moonflower.jfritz.callerlist.CallerList;
import de.moonflower.jfritz.struct.Call;
import de.moonflower.jfritz.struct.Person;
import de.moonflower.jfritz.struct.PhoneNumber;
import de.moonflower.jfritz.utils.Debug;
import de.moonflower.jfritz.utils.JFritzUtils;

public class PhoneBook extends AbstractTableModel {
	private static final long serialVersionUID = 1;

	private static final String PHONEBOOK_DTD_URI = "http://jfritz.moonflower.de/dtd/phonebook.dtd"; //$NON-NLS-1$

	// TODO Write correct dtd
	private static final String PHONEBOOK_DTD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" //$NON-NLS-1$
			+ "<!-- DTD for JFritz phonebook -->" //$NON-NLS-1$
			+ "<!ELEMENT firstname (commment?,entry*)>" //$NON-NLS-1$
			+ "<!ELEMENT middlename (#PCDATA)>" //$NON-NLS-1$
			+ "<!ELEMENT lastname (#PCDATA)>" //$NON-NLS-1$
			+ "<!ELEMENT entry (firstname?,middlename?,lastname?)>"; //$NON-NLS-1$

	private final String columnNames[] = {
			"private_entry", "fullName", "telephoneNumber", //$NON-NLS-1$,  //$NON-NLS-2$,  //$NON-NLS-3$
			"address", "city", "last_call" }; //$NON-NLS-1$,  //$NON-NLS-2$,  //$NON-NLS-3$

	private static final String PATTERN_THUNDERBRID_CSV = ","; //$NON-NLS-1$

	private Vector filteredPersons;

	private Vector unfilteredPersons;
	private String fileLocation;
	private CallerList callerList;
	private boolean allLastCallsSearched = false;
	/**
	 * A vector of Persons that will match any search filter. In other words: a
	 * list of sticky Persons, that will always show up. Used to ensure that a
	 * newly created Person can be seen by the user, even if there is a filter
	 * active
	 */
	private Vector filterExceptions;

	private int sortColumn = 1;

	private boolean sortDirection = true;

	public PhoneBook(String fileLocation) {
		this.fileLocation = fileLocation;
		filteredPersons = new Vector();
		unfilteredPersons = new Vector();
		filterExceptions = new Vector();
	}

	/**
	 * Sort table model rows by a specific column and direction
	 *
	 * @param col
	 *            Index of column to be sorted by
	 * @param asc
	 *            Order of sorting
	 */
	public void sortAllFilteredRowsBy(int col, boolean asc) {
		Collections.sort(filteredPersons, new ColumnSorter(col, asc));
		//Debug.msg("last calls: "+(t2-t1) + "ms sorting: "+(t3-t2)+"ms");
		fireTableDataChanged();
	}

	/**
	 * Sort table model rows by a specific column. The direction is determined
	 * automatically.
	 *
	 * @param col
	 *            Index of column to be sorted by
	 */
	public void sortAllFilteredRowsBy(int col) {
		if ((sortColumn == col) && (sortDirection == true)) {
			sortDirection = false;
		} else {
			sortColumn = col;
			sortDirection = true;
		}
		sortAllFilteredRowsBy(sortColumn, sortDirection);
	}

	/**
	 *
	 */
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		String columnName = getColumnName(columnIndex);
		// If the wahlhilfe doesnt work, check here again!
		if (columnName.equals(Main.getMessage("telephoneNumber"))) {
			return true;
		}
		return false;
	}

	/**
	 * Sort table model rows automatically.
	 *
	 */
	public void sortAllFilteredRows() {
		sortAllFilteredRowsBy(sortColumn, sortDirection);
	}

	public void sortAllUnfilteredRows() {
		Debug.msg("Sorting unfiltered data"); //$NON-NLS-1$
		Collections.sort(unfilteredPersons, new ColumnSorter(1, true));
		// Resort filtered data
		Collections.sort(filteredPersons, new ColumnSorter(sortColumn,
				sortDirection));
		updateFilter();
		fireTableStructureChanged();
	}

	/**
	 * This comparator is used to sort vectors of data
	 */
	public class ColumnSorter implements Comparator {
		int colIndex;

		boolean ascending;

		ColumnSorter(int colIndex, boolean ascending) {
			this.colIndex = colIndex;
			this.ascending = ascending;
		}

		public int compare(Object a, Object b) {
			Object o1, o2;
			Person p1 = (Person) a;
			Person p2 = (Person) b;
			switch (colIndex) {
			case 0:
				o1 = Boolean.toString(p1.isPrivateEntry());
				o2 = Boolean.toString(p2.isPrivateEntry());
				break;
			case 1:
				o1 = p1.getFullname().toUpperCase();
				o2 = p2.getFullname().toUpperCase();
				break;
			case 2:
				o1 = ""; //$NON-NLS-1$
				o2 = ""; //$NON-NLS-1$
				if (p1.getStandardTelephoneNumber() != null) {
					o1 = p1.getStandardTelephoneNumber().toString();
				}
				if (p2.getStandardTelephoneNumber() != null) {
					o2 = p2.getStandardTelephoneNumber().toString();
				}
				break;
			case 3:
				o1 = p1.getStreet().toUpperCase();
				o2 = p2.getStreet().toUpperCase();
				break;
			case 4:
				o1 = p1.getPostalCode() + p1.getCity().toUpperCase();
				o2 = p2.getPostalCode() + p2.getCity().toUpperCase();
				break;
			case 5:
				o1 = ""; //$NON-NLS-1$
				o2 = ""; //$NON-NLS-1$
				Call call1 = p1.getLastCall();
				if (call1 != null) {
					o1 = call1.getCalldate();
				}
				Call call2 = p2.getLastCall();
				if (call2 != null) {
					o2 = call2.getCalldate();
				}
				break;
			default:
				o1 = p1.getFullname();
				o2 = p2.getFullname();
			}

			// Treat empty strings like nulls
			if ((o1 instanceof String) && (((String) o1).trim().length() == 0)) {
				o1 = null;
			}
			if ((o2 instanceof String) && (((String) o2).trim().length() == 0)) {
				o2 = null;
			}

			// Sort nulls so they appear last, regardless
			// of sort order
			if ((o1 == null) && (o2 == null)) {
				return 0;
			} else if (o1 == null) {
				return 1;
			} else if (o2 == null) {
				return -1;
			} else if (o1 instanceof Comparable) {
				if (ascending) {
					return ((Comparable) o1).compareTo(o2);
				} else {
					return ((Comparable) o2).compareTo(o1);
				}
			} else {
				if (ascending) {
					return o1.toString().compareTo(o2.toString());
				} else {
					return o2.toString().compareTo(o1.toString());
				}
			}
		}
	}


	public Vector getFilteredPersons() {
		return filteredPersons; //TODO maybe clone()?
	}

	public Vector getUnfilteredPersons() {
		return unfilteredPersons;
	}

	/**
	 * @author haeusler DATE: 02.04.06 Adds a Person to the list of
	 *         filterExceptions.
	 * @param nonFilteredPerson
	 * @see #filterExceptions
	 */
	public void addFilterException(Person nonFilteredPerson) {
		filterExceptions.add(nonFilteredPerson);
	}

	/**
	 * Clears the list of filterExceptions.
	 *
	 * @see #filterExceptions
	 */
	public void clearFilterExceptions() {
		filterExceptions.clear();
	}
/*
 * inherited from AbstractTableModel
 */
	public boolean addEntry(Person newPerson) {
		Enumeration en = unfilteredPersons.elements();
		while (en.hasMoreElements()) {
			Person p = (Person) en.nextElement();
			PhoneNumber pn1 = p.getStandardTelephoneNumber();
			PhoneNumber pn2 = newPerson.getStandardTelephoneNumber();
			if ((pn1 != null) && (pn2 != null)
					&& pn1.getIntNumber().equals(pn2.getIntNumber())) {
				return false;
			}
		}
		newPerson.setLastCall(callerList.findLastCall(newPerson));
		unfilteredPersons.add(newPerson);
		//updateFilter();
		return true;
	}
	public void setLastCall(Person p ,Call c){
		int index = unfilteredPersons.indexOf(p);
		((Person)unfilteredPersons.get(index)).setLastCall(c);
		fireTableDataChanged();
	}

	public void deleteEntry(Person person) {
		unfilteredPersons.remove(person);
		updateFilter();
	}

	/**
	 * Saves phonebook to BIT FBF Dialer file.
	 *
	 * @param filename
	 */
	public synchronized void saveToBITFBFDialerFormat(String filename) {
		Debug.msg("Saving to BIT FBF Dialer file " + filename); //$NON-NLS-1$
		try {
			BufferedWriter pw = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filename), "UTF8")); //$NON-NLS-1$
			Enumeration en1 = unfilteredPersons.elements();

			Enumeration en2;
			Person current;
			String name;

			String nr, type;
			PhoneNumber pn;

			while (en1.hasMoreElements()) {
				current = (Person) en1.nextElement();
				name = ""; //$NON-NLS-1$
				if (current.getFullname().length() > 0) {
					if (current.getLastName().length() > 0) {
						name += current.getLastName();
					}
					if ((current.getLastName().length() > 0)
							&& (current.getFirstName().length() > 0)) {
						name += ", "; //$NON-NLS-1$
					}
					if (current.getFirstName().length() > 0) {
						name += current.getFirstName();
					}
					if (current.getCompany().length() > 0) {
						name += " (" + current.getCompany() + ")"; //$NON-NLS-1$,   //$NON-NLS-2$
					}
				} else if (current.getCompany().length() > 0) {
					name += current.getCompany();
				}

				if (name.length() > 0) {
					en2 = current.getNumbers().elements();
					while (en2.hasMoreElements()) {
						pn = (PhoneNumber) en2.nextElement();
						nr = pn.getIntNumber();
						if (nr.startsWith("+49")) {
							nr = "0" + nr.substring(3, nr.length()); //$NON-NLS-1$,  //$NON-NLS-2$
						}
						type = pn.getType();

						pw.write(nr + "=" + name); //$NON-NLS-1$
						pw.newLine();
					}
				}
			}
			pw.close();
		} catch (Exception e) {
			Debug.err("Could not write file!"); //$NON-NLS-1$
		}
	}

	/**
	 * Saves phonebook to BIT FBF Dialer file.
	 *
	 * @param filename
	 */
	public synchronized void saveToCallMonitorFormat(String filename) {
		Debug.msg("Saving to Call Monitor file " + filename); //$NON-NLS-1$
		try {
			BufferedWriter pw = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filename), "UTF8")); //$NON-NLS-1$
			Enumeration en1 = unfilteredPersons.elements();

			Enumeration en2;
			Person current;
			String name;

			String nr, type;
			PhoneNumber pn;

			while (en1.hasMoreElements()) {
				current = (Person) en1.nextElement();
				name = ""; //$NON-NLS-1$
				if (current.getFullname().length() > 0) {
					if (current.getLastName().length() > 0) {
						name += current.getLastName();
					}
					if ((current.getLastName().length() > 0)
							&& (current.getFirstName().length() > 0)) {
						name += ", "; //$NON-NLS-1$
					}
					if (current.getFirstName().length() > 0) {
						name += current.getFirstName();
					}
					if (current.getCompany().length() > 0) {
						name += " (" + current.getCompany() + ")"; //$NON-NLS-1$,  //$NON-NLS-2$
					}
				} else if (current.getCompany().length() > 0) {
					name += current.getCompany();
				}

				if (name.length() > 0) {
					en2 = current.getNumbers().elements();
					while (en2.hasMoreElements()) {
						pn = (PhoneNumber) en2.nextElement();
						nr = pn.getIntNumber();
						if (nr.startsWith("+49")) {
							nr = "0" + nr.substring(3, nr.length()); //$NON-NLS-1$,  //$NON-NLS-2$
						}
						type = pn.getType();

						pw.write("\"" + name + "\",\"" + nr + "\""); //$NON-NLS-1$, //$NON-NLS-2$,  //$NON-NLS-3$
						pw.newLine();
					}
				}
			}
			pw.close();
		} catch (Exception e) {
			Debug.err("Could not write file!"); //$NON-NLS-1$
		}
	}

	/**
	 * Saves phonebook to xml file.
	 *
	 * @param filename
	 */
	public synchronized void saveToXMLFile(String filename) {
		Debug.msg("Saving to file " + filename); //$NON-NLS-1$
		try {
			BufferedWriter pw = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filename), "UTF8")); //$NON-NLS-1$
			pw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"); //$NON-NLS-1$
			pw.newLine();
			// pw.write("<!DOCTYPE phonebook SYSTEM \"" + PHONEBOOK_DTD_URI
			// + "\">");
			// pw.newLine();
			pw.write("<phonebook>"); //$NON-NLS-1$
			pw.newLine();
			pw.write("<comment>Phonebook for " + Main.PROGRAM_NAME + " v" //$NON-NLS-1$,  //$NON-NLS-2$
					+ Main.PROGRAM_VERSION + "</comment>"); //$NON-NLS-1$
			pw.newLine();
			Enumeration en = unfilteredPersons.elements();
			while (en.hasMoreElements()) {
				Person current = (Person) en.nextElement();
				pw
						.write("<entry private=\"" + current.isPrivateEntry() + "\">"); //$NON-NLS-1$,  //$NON-NLS-2$
				pw.newLine();
				if (current.getFullname().length() > 0) {
					pw.write("\t<name>"); //$NON-NLS-1$
					pw.newLine();
					if (current.getFirstName().length() > 0) {
						pw
								.write("\t\t<firstname>" + JFritzUtils.convertSpecialChars(current.getFirstName()) //$NON-NLS-1$
										+ "</firstname>"); //$NON-NLS-1$
					}
					pw.newLine();
					if (current.getLastName().length() > 0) {
						pw
								.write("\t\t<lastname>" + JFritzUtils.convertSpecialChars(current.getLastName()) //$NON-NLS-1$
										+ "</lastname>"); //$NON-NLS-1$
					}
					pw.newLine();
					pw.write("\t</name>"); //$NON-NLS-1$
					pw.newLine();
					if (current.getCompany().length() > 0) {
						pw
								.write("\t<company>" + JFritzUtils.convertSpecialChars(current.getCompany()) //$NON-NLS-1$
										+ "</company>"); //$NON-NLS-1$
					}
					pw.newLine();
				}

				if ((current.getStreet().length() > 0)
						|| (current.getPostalCode().length() > 0)
						|| (current.getCity().length() > 0)) {
					pw.write("\t<address>"); //$NON-NLS-1$
					pw.newLine();
					if (current.getStreet().length() > 0) {
						pw
								.write("\t\t<street>" + JFritzUtils.convertSpecialChars(current.getStreet()) //$NON-NLS-1$
										+ "</street>"); //$NON-NLS-1$
					}
					pw.newLine();
					if (current.getPostalCode().length() > 0) {
						pw
								.write("\t\t<postcode>" + JFritzUtils.convertSpecialChars(current.getPostalCode()) //$NON-NLS-1$
										+ "</postcode>"); //$NON-NLS-1$
					}
					pw.newLine();
					if (current.getCity().length() > 0) {
						pw
								.write("\t\t<city>" + JFritzUtils.convertSpecialChars(current.getCity()) //$NON-NLS-1$
										+ "</city>"); //$NON-NLS-1$
					}
					pw.newLine();
					pw.write("\t</address>"); //$NON-NLS-1$
					pw.newLine();
				}

				pw.write("\t<phonenumbers standard=\"" //$NON-NLS-1$
						+ current.getStandard() + "\">"); //$NON-NLS-1$
				pw.newLine();
				Enumeration en2 = current.getNumbers().elements();
				while (en2.hasMoreElements()) {
					PhoneNumber nr = (PhoneNumber) en2.nextElement();
					pw.write("\t\t<number type=\"" + nr.getType() + "\">" //$NON-NLS-1$,  //$NON-NLS-2$
							+ JFritzUtils
									.convertSpecialChars(nr.getIntNumber())
							+ "</number>"); //$NON-NLS-1$
					pw.newLine();

				}
				pw.write("\t</phonenumbers>"); //$NON-NLS-1$
				pw.newLine();

				if (current.getEmailAddress().length() > 0) {
					pw.write("\t<internet>"); //$NON-NLS-1$
					pw.newLine();
					if (current.getEmailAddress().length() > 0) {
						pw
								.write("\t\t<email>" + JFritzUtils.convertSpecialChars(current.getEmailAddress()) //$NON-NLS-1$
										+ "</email>"); //$NON-NLS-1$
					}
					pw.newLine();
					pw.write("\t</internet>"); //$NON-NLS-1$
					pw.newLine();
				}
				pw.write("</entry>"); //$NON-NLS-1$
				pw.newLine();
			}
			pw.write("</phonebook>"); //$NON-NLS-1$
			pw.newLine();
			pw.close();
		} catch (UnsupportedEncodingException e) {
			Debug.err("UTF-8 not supported."); //$NON-NLS-1$
		} catch (FileNotFoundException e) {
			Debug.err("Could not write " + filename + "!"); //$NON-NLS-1$,  //$NON-NLS-2$
		} catch (IOException e) {
			Debug.err("IOException " + filename); //$NON-NLS-1$
		}
	}

	public synchronized void loadFromXMLFile(String filename) {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setValidating(false);
			SAXParser parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();

			reader.setErrorHandler(new ErrorHandler() {
				public void error(SAXParseException x) throws SAXException {
					// Debug.err(x.toString());
					throw x;
				}

				public void fatalError(SAXParseException x) throws SAXException {
					// Debug.err(x.toString());
					throw x;
				}

				public void warning(SAXParseException x) throws SAXException {
					// Debug.err(x.toString());
					throw x;
				}
			});
			reader.setEntityResolver(new EntityResolver() {
				public InputSource resolveEntity(String publicId,
						String systemId) throws SAXException, IOException {
					if (systemId.equals(PHONEBOOK_DTD_URI)) {
						InputSource is;
						is = new InputSource(new StringReader(PHONEBOOK_DTD));
						is.setSystemId(PHONEBOOK_DTD_URI);
						return is;
					}
					throw new SAXException("Invalid system identifier: " //$NON-NLS-1$
							+ systemId);
				}

			});
			reader.setContentHandler(new PhonebookFileXMLHandler(this));
			reader.parse(new InputSource(new FileInputStream(filename)));
			updateFilter();
		} catch (ParserConfigurationException e) {
			Debug.err("Error with ParserConfiguration!"); //$NON-NLS-1$
		} catch (SAXException e) {
			Debug.err("Error on parsing " + filename + "!"); //$NON-NLS-1$,  //$NON-NLS-2$
			Debug.err(e.toString());
			if (e.getLocalizedMessage().startsWith("Relative URI") //$NON-NLS-1$
					|| e.getLocalizedMessage().startsWith(
							"Invalid system identifier")) { //$NON-NLS-1$
				Debug.err(e.getLocalizedMessage());

				Debug.errDlg("Error on paring " + filename);
				// System.exit(0);
			}
		} catch (IOException e) {
			Debug.err("Could not read " + filename + "!"); //$NON-NLS-1$,  //$NON-NLS-2$
		}
		allLastCallsSearched = true;
		updateFilter();
	}

	public Object getValueAt(int rowIndex, int columnIndex) {
		Person person = (Person) filteredPersons.get(rowIndex);
		switch (columnIndex) {
		case 0:
			if (person.isPrivateEntry()) {
				return "YES"; //$NON-NLS-1$
			} else {
				return "NO"; //$NON-NLS-1$
			}
		case 1:
			return person.getFullname();
		case 2:
			return person.getStandardTelephoneNumber();
		case 3:
			return person.getStreet();
		case 4:
			return (person.getPostalCode() + " " + person.getCity()).trim(); //$NON-NLS-1$
		case 5: {
			return person.getLastCall();
		}

		default:
			return "X"; //$NON-NLS-1$
			// throw new IllegalArgumentException("Invalid column: " +
			// columnIndex);
		}
	}

	/**
	 * Returns the index of a Person in the filtered PhoneBook
	 *
	 * @param p
	 * @return
	 */
	public int indexOf(Person p) {
		return filteredPersons.indexOf(p);
	}

	private String getCSVHeader(char separator) {
		return "\"Private\"" + separator + "\"Last Name\"" + separator + "\"First Name\"" + separator + "\"Company\"" + separator + "\"Street\"" + separator + "\"ZIP Code\"" + separator + "\"City\"" + separator + "\"E-Mail\"" + separator + "\"Home\"" + separator + "\"Mobile\"" + separator + "\"Homezone\"" + separator + "\"Business\"" + separator + "\"Other\"" + separator + "\"Fax\"" + separator + "\"Sip\"" + separator + "\"Main\""; //$NON-NLS-1$
	}

	/**
	 * Saves PhoneBook to csv file
	 *
	 * @author Bastian Schaefer
	 *
	 * @param filename
	 *            Filename to save to
	 * @param wholePhoneBook
	 *            Save whole phone book or only selected entries
	 *            @deprecated
	 */
/*	public void saveToCSVFile(String filename, boolean wholePhoneBook,
			char separator) {
		Debug.msg("Saving phone book to csv file " + filename); //$NON-NLS-1$
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(filename);
			PrintWriter pw = new PrintWriter(fos);
			// pw.println("\"Private\";\"Last Name\";\"First
			// Name\";\"Number\";\"Address\";\"City\"");
			pw.println(getCSVHeader(separator));
			int rows[] = null;

			if (JFritz.getJframe() != null) {
				rows = JFritz.getJframe().getPhoneBookPanel()
						.getPhoneBookTable().getSelectedRows();
			}

			if (!wholePhoneBook && (rows != null) && (rows.length > 0)) {
				for (int i = 0; i < rows.length; i++) {
					Person currentPerson = (Person) filteredPersons
							.elementAt(rows[i]);
					pw.println(currentPerson.toCSV(separator));
				}
			} else if (wholePhoneBook) { // Export ALL UNFILTERED Calls
				Enumeration en = getUnfilteredPersons().elements();
				while (en.hasMoreElements()) {
					Person person = (Person) en.nextElement();
					pw.println(person.toCSV(separator));
				}
			} else { // Export ALL FILTERED Calls
				Enumeration en = getFilteredPersons().elements();
				while (en.hasMoreElements()) {
					Person person = (Person) en.nextElement();
					pw.println(person.toCSV(separator));
				}
			}
			pw.close();
		} catch (FileNotFoundException e) {
			Debug.err("Could not write " + filename + "!"); //$NON-NLS-1$,  //$NON-NLS-2$
		}
	}
*/
	/**
	 * Saves PhoneBook to csv file
	 *
	 * @author Bastian Schaefer
	 *
	 * @param filename
	 *            Filename to save to
	 *            Save whole phone book
	 */
	public void saveToCSVFile(String filename, char separator) {
		Debug.msg("Saving phone book("+unfilteredPersons.size()+" lines) to csv file " + filename); //$NON-NLS-1$
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(filename);
			PrintWriter pw = new PrintWriter(fos);
			// pw.println("\"Private\";\"Last Name\";\"First
			// Name\";\"Number\";\"Address\";\"City\"");
			pw.println(getCSVHeader(separator));
				//wenn man das komplette buch speichern will
			// unfilteredPersons durchsuchen
				for (int i = 0; i < unfilteredPersons.size(); i++) {
					Person currentPerson = (Person) unfilteredPersons
							.elementAt(i);
					pw.println(currentPerson.toCSV(separator));
				}
			pw.close();
		} catch (FileNotFoundException e) {
			Debug.err("Could not write " + filename + "!"); //$NON-NLS-1$,  //$NON-NLS-2$
		}

	}
	/**
	 * Saves PhoneBook to csv file
	 *
	 * @author Bastian Schaefer
	 *
	 * @param filename
	 *            Filename to save to
	 *            Save phone book only selected entries
	 */
	public void saveToCSVFile(String filename, int[] rows,
			char separator) {
		if(rows.length ==0){
			saveToCSVFile(filename, separator);
			return;
		}
		Debug.msg("Saving phone book("+rows.length+" lines) to csv file " + filename); //$NON-NLS-1$
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(filename);
			PrintWriter pw = new PrintWriter(fos);
			// pw.println("\"Private\";\"Last Name\";\"First
			// Name\";\"Number\";\"Address\";\"City\"");
			pw.println(getCSVHeader(separator));
				// wenn man nicht das komplette buch speichern will
			// muss man filteredPersons durchsuchen
				for (int i = 0; i < rows.length; i++) {
					Person currentPerson = (Person) filteredPersons
							.elementAt(rows[i]);
					pw.println(currentPerson.toCSV(separator));
				}
			pw.close();
		} catch (FileNotFoundException e) {
			Debug.err("Could not write " + filename + "!"); //$NON-NLS-1$,  //$NON-NLS-2$
		}
	}
	/**
	 * Returns info about stored Person
	 *
	 * @param rowIndex
	 */
	public Person getPersonAt(int rowIndex) {
		if (rowIndex >= 0) {
			return (Person) filteredPersons.get(rowIndex);
		} else {
			return null;
		}
	}

	public int getRowCount() {
		return filteredPersons.size();
	}

	public int getColumnCount() {
		return columnNames.length;
	}

	public String getColumnName(int column) {
		try {
			return Main.getMessage(columnNames[column]);
		} catch (Exception e) {
			return columnNames[column];
		}
	}

	public Person findPerson(PhoneNumber number) {
		return findPerson(number, true);
	}
	public Person findPerson(Call call){
		return findPerson(call.getPhoneNumber(), true);
	}

	/**
	 * Finds a person with the given number.
	 *
	 * @param number
	 *            a String containing the number to search for
	 * @param considerMain
	 *            true, if search for main number (telephone switchboard) shoul
	 *            be enabled.
	 * @return the Person having that number or the main number of telephone
	 *         switchboard in companies, null if no person was found
	 * @author Benjamin Schmitt (overwriting)
	 */
	public Person findPerson(PhoneNumber number, boolean considerMain) {
		if (number == null) {
			return null;
		}
		Vector foundPersons = new Vector();
		Enumeration en = unfilteredPersons.elements();
		while (en.hasMoreElements()) {
			Person p = (Person) en.nextElement();
			if (p.hasNumber(number.getIntNumber(), considerMain)) {
				foundPersons.add(p);
			}
		}
		if (foundPersons.size() == 0) {
			return null;
		} else if (foundPersons.size() == 1) {
			return (Person) foundPersons.get(0);
		} else {
			// delete all dummy entries for this number and return first element
			// of foundPersons
			for (int i = 0; i < foundPersons.size(); i++) {
				Person p = (Person) foundPersons.get(i);
				if (p.getFullname().equals("") && (p.getNumbers().size() == 1)
						&& p.getAddress().equals("") && p.getCity().equals("")
						&& p.getCompany().equals("")
						&& p.getEmailAddress().equals("")
						&& p.getPostalCode().equals("")
						&& p.getStreet().equals("")) {
					// dummy entry, delete it from database
					foundPersons.removeElement(p);
					unfilteredPersons.removeElement(p);
					this.saveToXMLFile(fileLocation);
				}
			}
			return (Person) foundPersons.get(0);
		}
	}

	/**
	 * searches for the last call for every Person in the
	 * Addressbook, and write it to person.lastCall to speed up sorting
	 */
	public void findAllLastCalls(){
		//TODO updaten wenn neue call oder personen oder rufnummern hinzukommen
		// oder alte gelöscht werden
		Debug.msg("searching lastCall for allPersons in the phonebook....");
		if(callerList==null){ Debug.err("setCallerList first!");}
		/*
		JFritz.getCallerList().calculateAllLastCalls(unfilteredPersons);
		too slow
		*/
		Person current;
		Call call;
		for (int i = 0; i < unfilteredPersons.size(); i++) {
			current = (Person)unfilteredPersons.get(i);
			call = callerList.findLastCall(current);
			if(call!=null){
				call.setPerson(current); // wichtig, sonst stht da noch null drinn und den Fehler findet man dann niemals
			}
			current.setLastCall(call);
		}
		Debug.msg("...done");
		fireTableDataChanged();
	}

	/**
	 * @param columnIndex
	 * @return class of column
	 */
	public Class getColumnClass(int columnIndex) {
		Object o = getValueAt(0, columnIndex);
		if (o == null) {
			return Object.class;
		} else {
			return o.getClass();
		}
	}

	public void updateFilter() {
		/*
		 * try { JFritz.getJframe().getCallerTable().getCellEditor()
		 * .cancelCellEditing(); } catch (NullPointerException e) { }
		 */
		boolean filter_private = JFritzUtils.parseBoolean(Main
				.getProperty("filter_private")); //$NON-NLS-1$

		String filterSearch = Main.getProperty("filter.Phonebook.search", ""); //$NON-NLS-1$,  //$NON-NLS-2$
		String keywords[] = filterSearch.split(" "); //$NON-NLS-1$

		if ((!filter_private) && (keywords.length == 0)) {
			// Use unfiltered data
			filteredPersons = unfilteredPersons;
		} else {
			// Data got to be filtered
			Vector newFilteredPersons = new Vector();
			Enumeration en = unfilteredPersons.elements();
			while (en.hasMoreElements()) {
				Person current = (Person) en.nextElement();

				// check whether this Person should be shown anyway
				if (filterExceptions.contains(current)) {
					newFilteredPersons.add(current);
					continue; // skip to next person in the while-loop
				}

				boolean match = true;

				// check wether the private filter rules this Person out
				if (filter_private && (!current.isPrivateEntry())) {
					match = false;
				}

				// check the keywords, if there are any
				for (int i = 0; match && (i < keywords.length); i++) {
					if (!current.matchesKeyword(keywords[i])) {
						match = false;
					}
				}

				// if all filter criteria are met, we add the person
				if (match) {
					newFilteredPersons.add(current);
				}
			}
			filteredPersons = newFilteredPersons;
		}

		sortAllFilteredRows();

		/*//FIXME checken, ob das jetzt echt nicht mehr gebraucht wird
		if (JFritz.getJframe() != null) {
			if (JFritz.getJframe().getPhoneBookPanel() != null) {
				JFritz.getJframe().getPhoneBookPanel().setStatus();
			}
		}
	*/
	}

	/**
	 * @author Brian Jensen function reads the thunderbird csv file line for
	 *         line adding new contacts after each line
	 *
	 * @param filename
	 *            is the path to a valid thunderbird csv file
	 */
	public String importFromThunderbirdCSVfile(String filename) {
		Debug.msg("Importing Thunderbird Contacts from csv file " + filename); //$NON-NLS-1$
		String line = ""; //$NON-NLS-1$
		String message;
		try {
			FileReader fr = new FileReader(filename);
			BufferedReader br = new BufferedReader(fr);

			int linesRead = 0;
			int newEntries = 0;
			// read until EOF
			while (null != (line = br.readLine())) {
				linesRead++;
				Person person = parseContactsThunderbirdCSV(line);

				// check if person had person had phone number
				if (person != null) {
					// check if it was a new person
					if (addEntry(person)) {
						newEntries++;
					}
				}

			}

			Debug.msg(linesRead
					+ " Lines read from Thunderbird csv file " + filename); //$NON-NLS-1$
			Debug.msg(newEntries + " New contacts processed"); //$NON-NLS-1$

			if (newEntries > 0) {
				sortAllUnfilteredRows();
				saveToXMLFile(Main.SAVE_DIR + fileLocation);
				String msg;

				if (newEntries == 1) {
					msg = Main.getMessage("imported_contact"); //$NON-NLS-1$
				} else {
					msg = newEntries
							+ " " + Main.getMessage("imported_contacts"); //$NON-NLS-1$,  //$NON-NLS-2$
				}
				message = msg;

			} else {
				message = Main.getMessage("no_imported_contacts"); //$NON-NLS-1$
			}

			br.close();

		} catch (FileNotFoundException e) {
			message = "Could not read from " + filename + "!";
			Debug.err("Could not read from " + filename + "!"); //$NON-NLS-1$, //$NON-NLS-2$
		} catch (IOException e) {
			message = "IO Exception reading csv file";
			Debug.err("IO Exception reading csv file"); //$NON-NLS-1$
		}
		return message;
	}

	/**
	 * @author Brian Jensen
	 *
	 * function parses out relevant contact information from a csv file, if no
	 * telephone number is found or the format is invalid null is returned
	 * tested with thunderbird version 1.50 tested with Mozilla suite 1.7.x
	 *
	 * Note: This class does NOT check for valid telephone numbers! That means
	 * contacts could be created without telephone numbers
	 *
	 * @param string
	 *            line is the current line of the csv file
	 * @return returns a person object if a telephone number can be processed
	 *         from the datei
	 */
	public Person parseContactsThunderbirdCSV(String line) {
		String[] field = line.split(PATTERN_THUNDERBRID_CSV);
		Person person;

		// check if line has correct amount of entries
		if (field.length < 36) {
			Debug.err("Invalid Thunderbird CSV format!"); //$NON-NLS-1$
			return null;
		}

		// check first if the entry even has a phone number
		// Debug.msg(field[6]+" "+field[7]+" "+field[8]+" "+field[9]+"
		// "+field[10]);
		if (field[6].equals("") && field[7].equals("") && field[8].equals("") && //$NON-NLS-1$,  //$NON-NLS-2$,  //$NON-NLS-3$
				field[9].equals("") && field[10].equals("")) { //$NON-NLS-1$,  //$NON-NLS-2$
			Debug.msg("No phone number present for contact"); //$NON-NLS-1$
			return null;
		}

		// at least a phone number and an email exists because thunderbird
		// is an email client and stores at least an email addy
		// so create a new person object
		person = new Person(field[0], field[25], field[1], field[11]
				+ field[12], field[15], field[13], field[4]);

		// TODO: Check for valid numbers, as you can never gurantee
		// that users do things properly, could be possible to create
		// contacts in the phonebook with no phone number = useless

		// Work number
		if (!field[6].equals("")) {
			person.addNumber(field[6], "business"); //$NON-NLS-1$
		}

		// home number
		if (!field[7].equals("")) {
			person.addNumber(field[7], "home"); //$NON-NLS-1$
		}

		// fax number
		if (!field[8].equals("")) {
			person.addNumber(field[8], "fax"); //$NON-NLS-1$
		}

		// pager number
		if (!field[9].equals("")) {
			person.addNumber(field[9], "other"); //$NON-NLS-1$
		}

		// Cell phone number
		if (!field[10].equals("")) {
			person.addNumber(field[10], "mobile"); //$NON-NLS-1$
		}

		// lets quit while we're still sane and return the person object
		return person;

	}

	/**
	 * Removes redundant entries from the phonebook. It checks for every pair of
	 * entries, if one entry supersedes another entry.
	 *
	 * @see de.moonflower.jfritz.struct.Person#supersedes(Person)
	 * @return the number of removed entries
	 */
	public synchronized int deleteDuplicateEntries() {
		Set redundantEntries = new HashSet();

		synchronized (unfilteredPersons) {
			int size = unfilteredPersons.size();
			for (int i = 0; i < size; i++) {
				Person currentOuter = (Person) unfilteredPersons.elementAt(i);
				for (int j = i + 1; j < size; j++) {
					Person currentInner = (Person) unfilteredPersons
							.elementAt(j);
					if (currentOuter.supersedes(currentInner)) {
						redundantEntries.add(currentInner);
					} else if (currentInner.supersedes(currentOuter)) {
						redundantEntries.add(currentOuter);
					}
				}
			}

			Iterator iterator = redundantEntries.iterator();
			while (iterator.hasNext()) {
				Person p = (Person) iterator.next();
				deleteEntry(p);
			}
		}

		if (redundantEntries.size() > 0) {
			saveToXMLFile(Main.SAVE_DIR + fileLocation);
			updateFilter();
		}

		return redundantEntries.size();
	}

	public void setCallerList(CallerList list) {
		this.callerList = list;

	}

	public boolean getAllLastCallsSearched() {
		return allLastCallsSearched;
	}

}