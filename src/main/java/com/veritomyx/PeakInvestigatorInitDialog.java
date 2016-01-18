/*
 * Copyright 2006-2015 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package com.veritomyx;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;



//import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.util.ExitCode;
import net.sf.mzmine.util.GUIUtils;
import net.sf.mzmine.util.components.GridBagPanel;
import net.sf.mzmine.util.components.HelpButton;

import java.util.Map;

/**
 * This class represents the user selected SLA and PI Version dialog. 
 * This dialog presents 2 lists, one for SLA and one for PI.
 * The first SLA and the highest version of PI are automatically selected to start. 
 * 
 */
public class PeakInvestigatorInitDialog extends JDialog implements ActionListener,
	DocumentListener {

    private static final long serialVersionUID = 1L;

    private ExitCode exitCode = ExitCode.UNKNOWN;

    private String helpID;

    // Buttons
    private JButton btnOK, btnCancel, btnHelp;

    /**
     * This single panel contains a grid of all the components of this dialog
     * (see GridBagPanel). First three columns of the grid are title (JLabel),
     * INIT component (JFormattedTextField or other) and value (JLabel). 
     */
    protected GridBagPanel mainPanel;
    
    protected JComboBox<String>	   	SLA_list;
    protected JComboBox<String> 	PIV_list;
    protected JLabel                    estimatedCost;
    
    protected Map<String, Double> SLAs;
    protected String	PIV;

    /**
     * Constructor
     */
    public PeakInvestigatorInitDialog(Window parent, Double funds, Map<String, Double> SLAs, String[] PIversions) {

	// Make dialog modal
	super(parent, "Please set the parameters",
		Dialog.ModalityType.DOCUMENT_MODAL);

	this.SLAs = SLAs;

	addDialogComponents(funds, SLAs, PIversions);

	updateMinimumSize();
	pack();

	setLocationRelativeTo(parent);

    }

    /**
     * This method must be called each time when a component is added to
     * mainPanel. It will ensure the minimal size of the dialog is set to the
     * minimum size of the mainPanel plus a little extra, so user cannot resize
     * the dialog window smaller.
     */
    protected void updateMinimumSize() {
	Dimension panelSize = mainPanel.getMinimumSize();
	Dimension minimumSize = new Dimension(panelSize.width + 50,
		panelSize.height + 50);
	setMinimumSize(minimumSize);
    }

    /**
     * Constructs all components of the dialog
     */
    protected void addDialogComponents(Double funds, Map<String, Double> SLAs, String[] PIversions) {

	// Main panel which holds all the components in a grid
	mainPanel = new GridBagPanel();
	
	JLabel funds_label = new JLabel("The available funds are (in USD): $");
    mainPanel.add(funds_label, 0, 0);
    JLabel funds_disp = new JLabel(String.format( "%.2f", funds ));
    mainPanel.add(funds_disp, 1, 0);
    
	// Create the 2 labels
    JLabel SLA_label = new JLabel("Use Response Time Objectives:");
    mainPanel.add(SLA_label, 0, 1);
    JLabel PIV_label = new JLabel("Use Peak Investigator Version:");
    mainPanel.add(PIV_label, 0, 2);
    
        // Create the 2 combo boxes, filled with the available selections.
        SLA_list = new JComboBox<String>(SLAs.keySet().toArray(new String[SLAs.size()]));
        SLA_list.setEditable(false);
        SLA_list.setSelectedIndex(0);
        SLA_list.addActionListener(this);
        mainPanel.add(SLA_list, 1, 1);

        PIV_list = new JComboBox<String>(PIversions);
        PIV_list.setEditable(false);
        PIV_list.setSelectedIndex(0);
        mainPanel.add(PIV_list, 1, 2);

        JLabel costLabel = new JLabel("Estimated cost:");
        mainPanel.add(costLabel, 0, 3);
        estimatedCost = new JLabel(formatCost());
        mainPanel.add(estimatedCost, 1, 3);

	// Add a single empty cell to the 4th row. This cell is expandable
	// (weightY is 1), therefore the other components will be
	// aligned to the top, which is what we want
	// JComponent emptySpace = (JComponent) Box.createVerticalStrut(1);
	// mainPanel.add(emptySpace, 0, 99, 3, 1, 0, 1);

	// Create a separate panel for the buttons
	JPanel pnlButtons = new JPanel();

	btnOK = GUIUtils.addButton(pnlButtons, "OK", null, this);
	btnCancel = GUIUtils.addButton(pnlButtons, "Cancel", null, this);

	if (helpID != null) {
	    btnHelp = new HelpButton(helpID);
	    pnlButtons.add(btnHelp);
	}

	/*
	 * Last row in the table will be occupied by the buttons. We set the row
	 * number to 100 and width to 3, spanning the 3 component columns
	 * defined above.
	 */
	mainPanel.addCenter(pnlButtons, 0, 100, 3, 1);

	// Add some space around the widgets
	GUIUtils.addMargin(mainPanel, 10);

	// Add the main panel as the only component of this dialog
	add(mainPanel);

	pack();

    }

    /**
     * Implementation for ActionListener interface
     */
    public void actionPerformed(ActionEvent ae) {

	Object src = ae.getSource();

	if (src == btnOK) {
	    closeDialog(ExitCode.OK);
	}

	if (src == btnCancel) {
	    closeDialog(ExitCode.CANCEL);
	}

	if (src instanceof JComboBox) {
	    estimatedCost.setText(formatCost());
	}

    }

    /**
     * Method for reading exit code
     */
    public ExitCode getExitCode() {
	return exitCode;
    }

    /**
     * This method may be called by some of the dialog components, for example
     * as a result of double-click by user
     */
    public void closeDialog(ExitCode exitCode) {
	if (exitCode == ExitCode.OK) {
	    // commit the changes to the parameter set
	    
	}
	this.exitCode = exitCode;
	dispose();

    }
 
    @Override
    public void changedUpdate(DocumentEvent event) {
        System.out.println("changeUpdate called");
    }

    @Override
    public void insertUpdate(DocumentEvent event) {

    }

    @Override
    public void removeUpdate(DocumentEvent event) {

    }
    
    public String getSLA() {
        return SLA_list.getSelectedItem().toString();
    }

    public String getPIversion() {
        return PIV_list.getSelectedItem().toString();
    }
    
    // convenience method to use the selected SLA to return a cost
    private String formatCost() {
        String currentSelection = SLA_list.getSelectedItem().toString();
        return String.format("$%.2f", SLAs.get(currentSelection));
    }
}
