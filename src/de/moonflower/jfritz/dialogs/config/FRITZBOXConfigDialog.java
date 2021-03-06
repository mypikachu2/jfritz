/*
 * Created on 09.09.2005
 *
 */
package de.moonflower.jfritz.dialogs.config;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import de.moonflower.jfritz.messages.MessageProvider;
import de.moonflower.jfritz.properties.PropertyProvider;
import de.moonflower.jfritz.utils.JFritzUtils;

/**
 * @author Robert Palmer
 * This class is the config dialog for the call monitor and not for
 * Jfritz!
 *
 */
public class FRITZBOXConfigDialog extends
        CallMonitorConfigDialog {

    private static final long serialVersionUID = -8662130877265779872L;

    private int exitCode = 0;

    private JButton cancelButton, okButton;

    public static final int APPROVE_OPTION = 1;

    public static final int CANCEL_OPTION = 2;

    private JCheckBox monitorIncomingCalls, monitorOutgoingCalls,
    				  popupIncomingCalls, popupOutgoingCalls,
                      fetchAfterDisconnect;

    private JTextField ignoreMSN;

    protected PropertyProvider properties = PropertyProvider.getInstance();
	protected MessageProvider messages = MessageProvider.getInstance();

    public FRITZBOXConfigDialog(JDialog parent) {
        super(parent, true);
        initDialog();
        if (parent != null) {
            setLocationRelativeTo(parent);
        }
    }

    public void initDialog() {
        setTitle(messages.getMessage("monitor_settings")); //$NON-NLS-1$
        drawDialog();
        setProperties();
    }

    private void setProperties() {
        monitorIncomingCalls.setSelected(JFritzUtils
                .parseBoolean(properties.getProperty(
                        "option.callmonitor.monitorTableIncomingCalls"))); //$NON-NLS-1$
        monitorOutgoingCalls.setSelected(JFritzUtils
                .parseBoolean(properties.getProperty(
                        "option.callmonitor.monitorTableOutgoingCalls"))); //$NON-NLS-1$
        popupIncomingCalls.setSelected(JFritzUtils
                .parseBoolean(properties.getProperty(
                        "option.callmonitor.popupIncomingCalls"))); //$NON-NLS-1$
        popupOutgoingCalls.setSelected(JFritzUtils
                .parseBoolean(properties.getProperty(
                        "option.callmonitor.popupOutgoingCalls"))); //$NON-NLS-1$
        fetchAfterDisconnect.setSelected(JFritzUtils
                .parseBoolean(properties.getProperty(
                        "option.callmonitor.fetchAfterDisconnect"))); //$NON-NLS-1$,  //$NON-NLS-2$
        ignoreMSN.setText(properties.getProperty("option.callmonitor.ignoreMSN")); //$NON-NLS-1$,  //$NON-NLS-2$
    }

    private void storeProperties() {
        properties.setProperty("option.callmonitor.monitorTableIncomingCalls", Boolean //$NON-NLS-1$
                .toString(monitorIncomingCalls.isSelected()));
        properties.setProperty("option.callmonitor.monitorTableOutgoingCalls", Boolean //$NON-NLS-1$
                .toString(monitorOutgoingCalls.isSelected()));
        properties.setProperty("option.callmonitor.popupIncomingCalls", Boolean //$NON-NLS-1$
                .toString(popupIncomingCalls.isSelected()));
        properties.setProperty("option.callmonitor.popupOutgoingCalls", Boolean //$NON-NLS-1$
                .toString(popupOutgoingCalls.isSelected()));
        properties.setProperty("option.callmonitor.fetchAfterDisconnect", Boolean.toString(fetchAfterDisconnect.isSelected())); //$NON-NLS-1$
        properties.setProperty("option.callmonitor.ignoreMSN", ignoreMSN.getText()); //$NON-NLS-1$
    }

    public int showConfigDialog() {
        // super.show();
        super.setVisible(true);
        return exitCode;
    }

    private void drawDialog() {
        KeyListener keyListener = (new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                // Cancel
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE
                        || (e.getSource() == cancelButton && e.getKeyCode() == KeyEvent.VK_ENTER)) {
                    exitCode = CANCEL_OPTION;
                    setVisible(false);
                }
                // OK
                if (e.getSource() == okButton
                        && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    storeProperties();
                    exitCode = APPROVE_OPTION;
                    setVisible(false);
                }
            }
        });
        ActionListener actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Object source = e.getSource();
                if (source == okButton) {
                    // OK
                    exitCode = APPROVE_OPTION;
                    storeProperties();
                } else if (source == cancelButton) {
                    exitCode = CANCEL_OPTION;
                }
                // Close Window
                if (source == okButton || source == cancelButton) {
                    setVisible(false);
                }
            }
        };

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets.top = 5;
        c.insets.bottom = 5;
        c.anchor = GridBagConstraints.WEST;

        c.gridwidth = 1;
        c.gridy = 0;
        popupIncomingCalls = new JCheckBox(
        		messages.getMessage("popup_incoming_calls")); //$NON-NLS-1$
        panel.add(popupIncomingCalls, c);
        c.gridy += 1;
        popupOutgoingCalls = new JCheckBox("" + //$NON-NLS-1$
        		messages.getMessage("popup_outgoing_calls")); //$NON-NLS-1$
        panel.add(popupOutgoingCalls, c);
        c.gridwidth = 1;
        c.gridy += 1;
        monitorIncomingCalls = new JCheckBox(
        		messages.getMessage("monitortable_incoming_calls")); //$NON-NLS-1$
        panel.add(monitorIncomingCalls, c);
        c.gridy += 1;
        monitorOutgoingCalls = new JCheckBox("" + //$NON-NLS-1$
        		messages.getMessage("monitortable_outgoing_calls")); //$NON-NLS-1$
        panel.add(monitorOutgoingCalls, c);
        c.gridy += 1;
        fetchAfterDisconnect = new JCheckBox(
        		messages.getMessage("monitor_fetch_disconnect")); //$NON-NLS-1$
        panel.add(fetchAfterDisconnect, c);
        c.gridy += 1;
        JLabel label = new JLabel(
        		messages.getMessage("monitor_ignore_msns")); //$NON-NLS-1$
        panel.add(label, c);
        c.gridy += 1;
        ignoreMSN = new JTextField("", 20); //$NON-NLS-1$
        ignoreMSN.setMinimumSize(new Dimension(150, 23));

        panel.add(ignoreMSN, c);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JPanel buttonPanel = new JPanel();
        okButton = new JButton(messages.getMessage("okay")); //$NON-NLS-1$
        okButton.setActionCommand("ok_pressed"); //$NON-NLS-1$
        okButton.addActionListener(actionListener);
        okButton.addKeyListener(keyListener);

        cancelButton = new JButton(messages.getMessage("cancel")); //$NON-NLS-1$
        cancelButton.setActionCommand("cancel_pressed"); //$NON-NLS-1$
        cancelButton.addActionListener(actionListener);
        cancelButton.addKeyListener(keyListener);

        //set default confirm button (Enter)
        getRootPane().setDefaultButton(okButton);

        //set default close button (ESC)
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        Action escapeAction = new AbstractAction()
        {
            private static final long serialVersionUID = 3L;

            public void actionPerformed(ActionEvent e)
            {
                 cancelButton.doClick();
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE"); //$NON-NLS-1$
        getRootPane().getActionMap().put("ESCAPE", escapeAction); //$NON-NLS-1$

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        getContentPane().add(panel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    }
}
