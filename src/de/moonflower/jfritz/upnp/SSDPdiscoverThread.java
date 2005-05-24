/*
 * Created on 24.05.2005
 *
 */
package de.moonflower.jfritz.upnp;

import java.util.Vector;

import de.moonflower.jfritz.JFritz;

/**
 * @author Arno Willig
 *
 */
public class SSDPdiscoverThread extends Thread {

	JFritz jfritz;

	int timeout;

	Vector devices;

	/**
	 * Constructs SSDPdiscoverThread
	 *
	 * @param timeout
	 */
	public SSDPdiscoverThread(JFritz jfritz, int timeout) {
		this.jfritz = jfritz;
		this.timeout = timeout;
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		jfritz.getJframe().setBusy(true);
		devices = UPNPUtils.SSDP_discoverFritzBoxes(timeout);
		jfritz.getJframe().setBusy(false);
	}

	/**
	 * @return Returns the fritz box devices.
	 */
	public final Vector getDevices() {
		return devices;
	}
}
