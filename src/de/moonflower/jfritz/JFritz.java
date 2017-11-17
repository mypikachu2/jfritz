/**
 *
 */

package de.moonflower.jfritz;

import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import jd.nutils.OSDetector;

import org.apache.http.auth.InvalidCredentialsException;
import org.apache.log4j.Logger;

import de.moonflower.jfritz.backup.JFritzBackup;
import de.moonflower.jfritz.box.BoxCommunication;
import de.moonflower.jfritz.box.fritzbox.FritzBox;
import de.moonflower.jfritz.callerlist.CallerList;
import de.moonflower.jfritz.callmonitor.DisconnectMonitor;
import de.moonflower.jfritz.callmonitor.DisplayCallsMonitor;
import de.moonflower.jfritz.callmonitor.MonitoredCalls;
import de.moonflower.jfritz.constants.ProgramConstants;
import de.moonflower.jfritz.dialogs.quickdial.QuickDials;
import de.moonflower.jfritz.dialogs.simple.MessageDlg;
import de.moonflower.jfritz.exceptions.InvalidFirmwareException;
import de.moonflower.jfritz.exceptions.WrongPasswordException;
import de.moonflower.jfritz.messages.MessageProvider;
import de.moonflower.jfritz.messages.UpdateMessageProvider;
import de.moonflower.jfritz.network.ClientLoginsTableModel;
import de.moonflower.jfritz.network.NetworkStateMonitor;
import de.moonflower.jfritz.phonebook.PhoneBook;
import de.moonflower.jfritz.properties.PropertyProvider;
import de.moonflower.jfritz.sounds.PlaySound;
import de.moonflower.jfritz.sounds.SoundProvider;
import de.moonflower.jfritz.struct.PhoneNumberOld;
import de.moonflower.jfritz.tray.JFritzTray;
import de.moonflower.jfritz.tray.Tray;
import de.moonflower.jfritz.utils.Debug;
import de.moonflower.jfritz.utils.Encryption;
import de.moonflower.jfritz.utils.JFritzUtils;
import de.moonflower.jfritz.utils.StatusListener;
import de.moonflower.jfritz.utils.reverselookup.JFritzReverseLookup;
import org.jfritz.fboxlib.exceptions.LoginBlockedException;
import org.jfritz.fboxlib.exceptions.PageNotFoundException;

/**
 *
 */
public final class JFritz implements  StatusListener {
	private final static Logger log = Logger.getLogger(JFritz.class);

	public final static String DOCUMENTATION_URL = "https://jfritz.org/wiki/Kategorie:Hilfe"; //$NON-NLS-1$

	public final static String CALLS_FILE = "jfritz.calls.xml"; //$NON-NLS-1$

	public final static String QUICKDIALS_FILE = "jfritz.quickdials.xml"; //$NON-NLS-1$

	public final static String PHONEBOOK_FILE = "jfritz.phonebook.xml"; //$NON-NLS-1$

	public final static String CLIENT_SETTINGS_FILE = "jfritz.clientsettings.xml"; //$NON-NLS-1$

	public final static String CALLS_CSV_FILE = "calls.csv"; //$NON-NLS-1$

	public final static String PHONEBOOK_CSV_FILE = "contacts.csv"; //$NON-NLS-1$

	public final static String PHONEBOOK_XML_FILE = "contacts.xml"; // 17.11.2017

	private static JFritzWindow jframe;

	private static CallerList callerlist;

	private static PhoneBook phonebook;

	private static WatchdogThread watchdog;

	private static Timer watchdogTimer;

	private static QuickDials quickDials;

	public static MonitoredCalls callMonitorList;

	private static Main main;

	private static ClientLoginsTableModel clientLogins;

	private static boolean shutdownInvoked = false;

	private static BoxCommunication boxCommunication;

	private static SoundProvider soundProvider;

	private static PlaySound playSound;

	protected PropertyProvider properties = PropertyProvider.getInstance();
	protected MessageProvider messages = MessageProvider.getInstance();
	protected UpdateMessageProvider updateMessages = UpdateMessageProvider.getInstance();

	/**
	 * Constructs JFritz object
	 */
	public JFritz(Main mn) {
		main = mn;

		/*
		JFritzEventDispatcher eventDispatcher = new JFritzEventDispatcher();
		JFritzEventDispatcher.registerEventType(new MessageEvent());

		JFritzEventDispatcher.registerActionType(new PopupAction());
		JFritzEventDispatcher.registerActionType(new TrayMessageAction());

		JFritzEventDispatcher.loadFromXML();

		*/

		if (JFritzUtils.parseBoolean(properties.getProperty("option.createBackup"))) { //$NON-NLS-1$
			JFritzBackup.getInstance().doBackup();
		}

			//option was removed from the config dialog in 0.7.1, make sure
			//it is automatically deselected
		if(properties.getProperty("option.callMonitorType").equals("6"))
			properties.setProperty("option.callMonitorType", "0");

		// make sure there is a plus on the country code, or else the number
		// scheme won't work
		if (!properties.getProperty("country.code").startsWith("+"))
			properties.setProperty("country.code", "+"
					+ properties.getProperty("country.code"));

		if (OSDetector.isMac()) { //$NON-NLS-1$
			new MacHandler(this);
		}

		//once the machandler has been installed, activate the debug panel
		//otherwise it will cause ui problems on the mac
		//stupid concept really, but it has to be done
		Debug.generatePanel();
	}

	public void initNumbers()
	{
		// loads various country specific number settings and tables
		loadNumberSettings();
	}

	public int initFritzBox() throws WrongPasswordException, InvalidFirmwareException, IOException
	{
		int result = 0;		
		FritzBox fritzBox = new FritzBox("Fritz!Box",
											     "My Fritz!Box",
											     "http",
												 properties.getProperty("box.address"),
												 properties.getProperty("box.port"),
												 Boolean.parseBoolean(properties.getProperty("box.loginUsingUsername")), 
												 properties.getProperty("box.username"), 
												 Encryption.decrypt(properties.getProperty("box.password")));
		fritzBox.init(true);
		
		if ("not_detected".equals(properties.getProperty("box.serial"))) {
			// check mac address only, if we did not detect a serial (only for old firmwares)
			result = fritzBox.checkMacAddress(fritzBox);
		}
		
		boxCommunication = new BoxCommunication(log);
		boxCommunication.addBox(fritzBox);

		log.info("connection. --: " + result);
		return result;
	}

	public void initQuickDials()
	{
		quickDials = new QuickDials();
		quickDials.loadFromXMLFile(JFritzDataDirectory.getInstance().getDataDirectory() + JFritz.QUICKDIALS_FILE);
	}

	public void initCallerListAndPhoneBook()
	{
		callerlist = new CallerList();
		phonebook = new PhoneBook(PHONEBOOK_FILE);
		callerlist.setPhoneBook(phonebook);
		phonebook.setCallerList(callerlist);
		phonebook.loadFromXMLFile(JFritzDataDirectory.getInstance().getDataDirectory() + PHONEBOOK_FILE);
		callerlist.loadFromXMLFile(JFritzDataDirectory.getInstance().getDataDirectory() + CALLS_FILE);
	}

	public void initSounds() {
		soundProvider = new SoundProvider();
		playSound = new PlaySound(soundProvider);
	}

	public void initCallMonitorListener()
	{
		callMonitorList = new MonitoredCalls();
		callMonitorList.addCallMonitorListener(new DisplayCallsMonitor(playSound));
		callMonitorList.addCallMonitorListener(new DisconnectMonitor());
	}

	public void initClientServer()
	{
		clientLogins = new ClientLoginsTableModel();

		ClientLoginsTableModel.loadFromXMLFile(JFritzDataDirectory.getInstance().getDataDirectory()+CLIENT_SETTINGS_FILE);
	}

	public void createJFrame() {
		log.info("New instance of JFrame"); //$NON-NLS-1$
		jframe = new JFritzWindow(this);
		if (Main.checkForSystraySupport()) {
			log.info("Check Systray-Support"); //$NON-NLS-1$
			try {
				JFritzTray.initTray(jframe, getBoxCommunication());
			} catch (Throwable e) {
				Main.systraySupport = false;
			}
		}
		jframe.checkStartOptions();
	}

	public void startClientServer() {
		if (!shutdownInvoked)
		{
			javax.swing.SwingUtilities.invokeLater(jframe);

			if(properties.getProperty("network.type").equals("1") &&
					Boolean.parseBoolean(properties.getProperty("option.listenOnStartup"))){
				log.info("listening on startup enabled, starting client listener!");
				NetworkStateMonitor.startServer();
			}else if(properties.getProperty("network.type").equals("2") &&
					Boolean.parseBoolean(properties.getProperty("option.connectOnStartup"))){
				log.info("Connect on startup enabled, connectig to server");
				NetworkStateMonitor.startClient();
			}
		}
	}
	
	public void registerListeners() {
		boxCommunication.registerCallListProgressListener(getCallerList());
		boxCommunication.registerBoxCallBackListener(JFritz.getCallerList());
	}

	public void registerGuiListeners() {
		boxCommunication.registerCallMonitorStateListener(jframe);
		boxCommunication.registerCallListProgressListener(jframe.getCallerListPanel());
		boxCommunication.registerBoxStatusListener(jframe);
	}

	/**
	 * This constructor is used for JUnit based testing suites
	 * Only the default settings are loaded for this jfritz object
	 *
	 * @author brian jensen
	 * @throws IOException
	 * @throws InvalidFirmwareException
	 * @throws WrongPasswordException
	 * @throws de.robotniko.fboxlib.exceptions.InvalidCredentialsException 
	 * @throws InvalidCredentialsException 
	 * @throws PageNotFoundException 
	 * @throws LoginBlockedException 
	 */
	public JFritz(String test) throws WrongPasswordException, InvalidFirmwareException, IOException 
	{
		// make sure there is a plus on the country code, or else the number
		// scheme won't work
		if (!properties.getProperty("country.code").startsWith("+"))
			properties.setProperty("country.code", "+"
					+ properties.getProperty("country.code"));

		// loadSounds();

		// loads various country specific number settings and tables
		loadNumberSettings();

		FritzBox fritzBox = new FritzBox("Fritz!Box",
									     "My Fritz!Box",
									     "http",
										 properties.getProperty("box.address"),
										 properties.getProperty("box.port"),
										 false,
										 "",
										 Encryption.decrypt(properties.getProperty("box.password")));
		fritzBox.init(false);

		boxCommunication = new BoxCommunication(log);
		boxCommunication.addBox(fritzBox);

		callerlist = new CallerList();
		// callerlist.loadFromXMLFile(SAVE_DIR + CALLS_FILE);

		phonebook = new PhoneBook(PHONEBOOK_FILE);
		// phonebook.loadFromXMLFile(SAVE_DIR + PHONEBOOK_FILE);
		phonebook.setCallerList(callerlist);
		callerlist.setPhoneBook(phonebook);
	}

	/**
	 * Displays balloon info message
	 *
	 * @param msg
	 *            Message to be displayed
	 */
	public static void infoMsg(String msg) {
		switch (Integer.parseInt(PropertyProvider.getInstance().getProperty("option.popuptype"))) { //$NON-NLS-1$,  //$NON-NLS-2$
		case 0: { // No Popup
			break;
		}
		case 1: {
			MessageDlg msgDialog = new MessageDlg();
			msgDialog.showMessage(msg, Long.parseLong(PropertyProvider.getInstance().getProperty(
					"option.popupDelay")) * 1000);
			msgDialog.repaint();
			msgDialog.toFront();
			break;
		}
		case 2: {
			if (JFritzTray.isSupported())
				JFritzTray.displayMessage(ProgramConstants.PROGRAM_NAME, msg,
						Tray.MESSAGE_TYPE_INFO);
			else {
				MessageDlg msgDialog = new MessageDlg();
				msgDialog.showMessage(msg, Long.parseLong(PropertyProvider.getInstance().getProperty(
						"option.popupDelay")) * 1000);
				msgDialog.repaint();
				msgDialog.toFront();
			}
			break;
		}
		}
	}

	/**
	 * Displays balloon error message
	 *
	 * @param msg
	 */
	public static void errorMsg(String msg, Throwable t) {
		log.error(msg, t);
		if (Main.systraySupport) {
			JFritzTray.displayMessage(ProgramConstants.PROGRAM_NAME, msg,
					Tray.MESSAGE_TYPE_ERROR);
		}
	}

	/**
	 * @return Returns the callerlist.
	 */
	public static final CallerList getCallerList() {
		return callerlist;
	}

	/**
	 * @return Returns the phonebook.
	 */
	public static final PhoneBook getPhonebook() {
		return phonebook;
	}

	/**
	 * @return Returns the jframe.
	 */
	public static final JFritzWindow getJframe() {
		return jframe;
	}

	/**
	 * start timer for watchdog
	 *
	 */
	public void startWatchdog() {
		if (!shutdownInvoked)
		{
			int interval = 5; // seconds
			int factor = 2; // factor how many times a STANDBY will be checked
			watchdogTimer = new Timer("Watchdog-Timer", true);
			watchdog = new WatchdogThread(interval, factor);
			watchdogTimer.schedule(new TimerTask() {
				public void run() {
					if (shutdownInvoked)
						this.cancel();
					watchdog.run();
				}
			}, interval*1000, interval * 1000);
			log.info("Watchdog enabled"); //$NON-NLS-1$
		}
	}

	/**
	 * @Brian Jensen This function changes the state of the ResourceBundle
	 *        object currently available locales: see lang subdirectory Then it
	 *        destroys the old window and redraws a new one with new locale
	 *
	 * @param l
	 *            the locale to change the language to
	 */
	public void createNewWindow(Locale l) {
		log.info("Loading new locale"); //$NON-NLS-1$
		messages.loadMessages(l);
		updateMessages.loadMessages(l);

		refreshWindow();
	}

	/**
	 * Sets default Look'n'Feel
	 */
	public void setDefaultLookAndFeel() {
		if (JFritzUtils.parseBoolean(properties.getProperty("window.useDecorations"))) {
			JFritzWindow.setDefaultLookAndFeelDecorated(true);
			JDialog.setDefaultLookAndFeelDecorated(true);
			JFrame.setDefaultLookAndFeelDecorated(true);
		} else {
			JFritzWindow.setDefaultLookAndFeelDecorated(false);
			JDialog.setDefaultLookAndFeelDecorated(false);
			JFrame.setDefaultLookAndFeelDecorated(false);
		}
		try {
			log.info("Changing look and feel to: " + properties.getStateProperty("lookandfeel")); //$NON-NLS-1$
			UIManager.setLookAndFeel(properties.getStateProperty("lookandfeel")); //$NON-NLS-1$
			if ( jframe != null )
			{
				SwingUtilities.updateComponentTreeUI(jframe);
			}
			// Wunsch eines MAC Users, dass das Default LookAndFeel des
			// Betriebssystems genommen wird
		} catch (Exception ex) {
			log.error(ex.toString());
		}
	}

	/**
	 * @ Bastian Schaefer
	 *
	 * Destroys and repaints the Main Frame.
	 *
	 */

	public void refreshWindow() {
		boxCommunication.unregisterCallMonitorStateListener(jframe);
		boxCommunication.unregisterCallListProgressListener(jframe.getCallerListPanel());
		jframe.dispose();
		setDefaultLookAndFeel();
		javax.swing.SwingUtilities.invokeLater(jframe);
		jframe = new JFritzWindow(this);
		boxCommunication.registerCallMonitorStateListener(jframe);
		boxCommunication.registerCallListProgressListener(jframe.getCallerListPanel());
		javax.swing.SwingUtilities.invokeLater(jframe);
		jframe.checkOptions();
		javax.swing.SwingUtilities.invokeLater(jframe);
		jframe.setVisible(true);
	}

	boolean maybeExit(int i, boolean check) {
		boolean exit = true;
		if (check &&
				JFritzUtils.parseBoolean(properties.getProperty(
				"option.confirmOnExit"))) { //$NON-NLS-1$ $NON-NLS-2$
			exit = showExitDialog();
		}
		if (exit) {
			main.exit(0);
		}
		return exit;
	}

	void prepareShutdown(boolean shutdownThread, boolean shutdownHook) throws InterruptedException {
		shutdownInvoked = true;

		// TODO maybe some more cleanup is needed
		log.debug("prepareShutdown in JFritz.java");

		if ( jframe != null) {
			jframe.prepareShutdown();
			properties.saveStateProperties();
		}

		log.info("Stopping reverse lookup");
		JFritzReverseLookup.terminateAsyncLookup();

		if ( (Main.systraySupport))
		{
			JFritzTray.removeTrayMenu();
		}

		log.info("Stopping watchdog"); //$NON-NLS-1$

		if ( watchdog != null ) {
			watchdogTimer.cancel();
			watchdog = null;
			watchdogTimer = null;
//			// FIXME: interrupt() lässt JFritz beim System-Shutdown hängen
//			//			watchdog.interrupt();
		}

		log.debug("prepareShutdown in JFritz.java done");

		// Keep this order to properly shutdown windows. First interrupt thread,
		// then dispose.
		if ( ((shutdownThread) || (shutdownHook)) && (jframe != null))
		{
			jframe.interrupt();
		}
		// This must be the last call, after disposing JFritzWindow nothing
		// is executed at windows-shutdown
		if ( (!shutdownThread) && (!shutdownHook) && (jframe != null) )
		{
			jframe.dispose();
		}
	}

	/**
	 * Shows the exit dialog
	 */
	boolean showExitDialog() {
		boolean exit = true;
		exit = JOptionPane.showConfirmDialog(jframe, messages
				.getMessage("really_quit"), ProgramConstants.PROGRAM_NAME, //$NON-NLS-1$
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;

		return exit;
	}

	public static void loadNumberSettings() {
		// load the different area code -> city mappings
		JFritzReverseLookup.loadSettings();
		PhoneNumberOld.loadFlagMap();
		PhoneNumberOld.loadCbCXMLFile();
	}

	public static MonitoredCalls getCallMonitorList() {
		return callMonitorList;
	}

	public void statusChanged(Object status) {
		String statusMsg = "";

		if(status instanceof Integer){
			int duration = ((Integer)status).intValue();
			int hours = duration / 3600;
			int mins = duration % 3600 / 60;
			 statusMsg = messages.getMessage("telephone_entries").replaceAll("%N", Integer.toString(JFritz.getCallerList().getRowCount())) + ", " //$NON-NLS-1$,  //$NON-NLS-2$,  //$NON-NLS-3$
					+ messages.getMessage("total_duration") + ": " + hours + "h " //$NON-NLS-1$,  //$NON-NLS-2$,  //$NON-NLS-3$
					+ mins + " min " + " (" + duration / 60 + " min)"; //$NON-NLS-1$,  //$NON-NLS-2$,  //$NON-NLS-3$
			;
		}
		if(status instanceof String){
			statusMsg = (String) status;
		}
		jframe.setStatus(statusMsg);
	}

	public static QuickDials getQuickDials() {
		return quickDials;
	}

	public static ClientLoginsTableModel getClientLogins(){
		return clientLogins;
	}

	public static boolean isShutdownInvoked()
	{
		return shutdownInvoked;
	}

	public static BoxCommunication getBoxCommunication()
	{
		return boxCommunication;
	}
}
