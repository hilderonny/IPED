package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import iped.engine.data.ReportInfo;

public class ReportInfoDialog extends JDialog implements ActionListener {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private ReportInfo reportInfo = new ReportInfo();

    JDialog infoDialog;
    JButton infoButton = new JButton(Messages.getString("ReportDialog.LoadButton")); //$NON-NLS-1$
    JButton okButton = new JButton("OK"); //$NON-NLS-1$
    JTextField rNumber = new JTextField();
    JTextField rDate = new JTextField();
    JTextField rTitle = new JTextField();
    JTextField rExaminer = new JTextField();
    JTextField rCaseNumber = new JTextField();
    JTextField rRequestForm = new JTextField();
    JTextField rRequestDate = new JTextField();
    JTextField rRequester = new JTextField();
    JTextField rLabNumber = new JTextField();
    JTextField rLabDate = new JTextField();
    JTextArea rEvidences = new JTextArea();

    private GridBagConstraints getGridBagConstraints(int x, int y, int width, int height) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        if (width > 1)
            c.weightx = 1.0;
        if (height > 1) {
            c.weighty = 1.0;
            c.fill = GridBagConstraints.BOTH;
        }
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.gridheight = height;
        return c;
    }

    public ReportInfoDialog(JDialog owner) {
        super(owner);

        infoDialog = this;
        infoDialog.setTitle(Messages.getString("ReportDialog.CaseInfo")); //$NON-NLS-1$
        infoDialog.setBounds(0, 0, 500, 550);
        infoDialog.setLocationRelativeTo(null);

        JPanel fullPanel = new JPanel(new BorderLayout());
        fullPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel panel = new JPanel(new GridBagLayout());

        JLabel loadFile = new JLabel();
        loadFile.setText(Messages.getString("ReportDialog.LoadInfo"));
        panel.add(loadFile, getGridBagConstraints(0, 0, 2, 1));
        panel.add(infoButton, getGridBagConstraints(2, 0, 1, 1));

        JLabel num = new JLabel(Messages.getString("ReportDialog.ReportNum")); //$NON-NLS-1$
        panel.add(num, getGridBagConstraints(0, 1, 1, 1));
        panel.add(rNumber, getGridBagConstraints(1, 1, 2, 1));

        JLabel date = new JLabel(Messages.getString("ReportDialog.ReportDate")); //$NON-NLS-1$
        panel.add(date, getGridBagConstraints(0, 2, 1, 1));
        panel.add(rDate, getGridBagConstraints(1, 2, 2, 1));

        JLabel record = new JLabel(Messages.getString("ReportDialog.Record")); //$NON-NLS-1$
        panel.add(record, getGridBagConstraints(0, 9, 1, 1));
        panel.add(rLabNumber, getGridBagConstraints(1, 9, 2, 1));

        JLabel examiner = new JLabel(Messages.getString("ReportDialog.Examiner")); //$NON-NLS-1$
        panel.add(examiner, getGridBagConstraints(0, 4, 1, 1));
        panel.add(rExaminer, getGridBagConstraints(1, 4, 2, 1));

        JLabel ipl = new JLabel(Messages.getString("ReportDialog.Investigation")); //$NON-NLS-1$
        panel.add(ipl, getGridBagConstraints(0, 5, 1, 1));
        panel.add(rCaseNumber, getGridBagConstraints(1, 5, 2, 1));

        fullPanel.add(panel, BorderLayout.CENTER);
        JPanel bPanel = new JPanel(new BorderLayout());
        bPanel.add(okButton, BorderLayout.EAST);
        fullPanel.add(bPanel, BorderLayout.SOUTH);

        infoDialog.getContentPane().add(fullPanel);

        infoButton.addActionListener(this);
        okButton.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == infoButton) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setFileFilter(new InfoFileFilter());
            if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                File infoFile = fileChooser.getSelectedFile();
                try {
                    reportInfo = new ReportInfo();
                    if (infoFile.getName().endsWith(".json"))
                        reportInfo.readJsonInfoFile(infoFile);
                    else if (infoFile.getName().endsWith(".asap"))
                        reportInfo.readAsapInfoFile(infoFile);

                    populateTextFields(reportInfo);

                } catch (IOException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Error loading case info file: " + e1.toString()); //$NON-NLS-1$
                }
            }

        }

        if (e.getSource() == okButton) {
            loadTextFields();
            this.setVisible(false);
        }

    }

    private class InfoFileFilter extends FileFilter {
        @Override
        public boolean accept(File f) {
            return f.isDirectory() || f.getName().toLowerCase().endsWith(".asap") || //$NON-NLS-1$
                    f.getName().toLowerCase().endsWith(".json");
        }

        @Override
        public String getDescription() {
            return "*.json;*.asap"; //$NON-NLS-1$
        }
    }

    private void populateTextFields(ReportInfo info) {
        rNumber.setText(info.reportNumber);
        rDate.setText(info.reportDate);
        rExaminer.setText(info.getExaminersText());
        rCaseNumber.setText(info.caseNumber);
        rLabNumber.setText(info.labCaseNumber);
    }

    private void loadTextFields() {
        reportInfo.reportNumber = rNumber.getText().trim();
        reportInfo.reportDate = rDate.getText().trim();
        reportInfo.fillExaminersFromText(rExaminer.getText().trim());
        reportInfo.caseNumber = rCaseNumber.getText().trim();
        reportInfo.labCaseNumber = rLabNumber.getText().trim();
    }

    public ReportInfo getReportInfo() {
        return reportInfo;
    }
}
