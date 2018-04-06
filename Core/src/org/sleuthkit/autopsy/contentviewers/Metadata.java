/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Component;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * Shows file metadata as a list to make it easy to copy and paste. Typically
 * shows the same data that can also be found in the ResultViewer table, just a
 * different order and allows the full path to be visible in the bottom area.
 */
@ServiceProvider(service = DataContentViewer.class, position = 6)
public class Metadata extends javax.swing.JPanel implements DataContentViewer {

    /**
     * Creates new form Metadata
     */
    public Metadata() {
        initComponents();
        customizeComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupMenu1 = new javax.swing.JPopupMenu();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextPane1 = new javax.swing.JTextPane();

        setPreferredSize(new java.awt.Dimension(100, 52));

        jScrollPane2.setPreferredSize(new java.awt.Dimension(610, 52));

        jTextPane1.setEditable(false);
        jTextPane1.setPreferredSize(new java.awt.Dimension(600, 52));
        jScrollPane2.setViewportView(jTextPane1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextPane jTextPane1;
    // End of variables declaration//GEN-END:variables

    private void customizeComponents() {
        /*
         * jTextPane1.setComponentPopupMenu(rightClickMenu); ActionListener
         * actList = new ActionListener(){ @Override public void
         * actionPerformed(ActionEvent e){ JMenuItem jmi = (JMenuItem)
         * e.getSource(); if(jmi.equals(copyMenuItem)) outputViewPane.copy();
         * else if(jmi.equals(selectAllMenuItem)) outputViewPane.selectAll(); }
         * }; copyMenuItem.addActionListener(actList);
         * selectAllMenuItem.addActionListener(actList);
         */

        Utilities.configureTextPaneAsHtml(jTextPane1);
    }

    private void setText(String str) {
        jTextPane1.setText("<html><body>" + str + "</body></html>"); //NON-NLS
    }

    private void startTable(StringBuilder sb) {
        sb.append("<table>"); //NON-NLS
    }

    private void endTable(StringBuilder sb) {
        sb.append("</table>"); //NON-NLS
    }

    private void addRow(StringBuilder sb, String key, String value) {
        sb.append("<tr><td>"); //NON-NLS
        sb.append(key);
        sb.append("</td><td>"); //NON-NLS
        sb.append(value);
        sb.append("</td></tr>"); //NON-NLS
    }

    @Messages({
        "Metadata.tableRowTitle.mimeType=MIME Type",
        "Metadata.nodeText.truncated=(results truncated)"})
    @Override
    public void setNode(Node node) {
        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        if (file == null) {
            setText(NbBundle.getMessage(this.getClass(), "Metadata.nodeText.nonFilePassedIn"));
            return;
        }

        StringBuilder sb = new StringBuilder();
        startTable(sb);

        try {
            addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.name"), file.getUniquePath());
        } catch (TskCoreException ex) {
            addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.name"), file.getParentPath() + "/" + file.getName());
        }

        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.type"), file.getType().getName());
        addRow(sb, Bundle.Metadata_tableRowTitle_mimeType(), file.getMIMEType());
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.size"), Long.toString(file.getSize()));
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.fileNameAlloc"), file.getDirFlagAsString());
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.metadataAlloc"), file.getMetaFlagsAsString());
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.modified"), ContentUtils.getStringTime(file.getMtime(), file));
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.accessed"), ContentUtils.getStringTime(file.getAtime(), file));
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.created"), ContentUtils.getStringTime(file.getCrtime(), file));
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.changed"), ContentUtils.getStringTime(file.getCtime(), file));
        

        String md5 = file.getMd5Hash();
        if (md5 == null) {
            md5 = NbBundle.getMessage(this.getClass(), "Metadata.tableRowContent.md5notCalc");
        }
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.md5"), md5);
        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.hashLookupResults"), file.getKnown().toString());

        addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.internalid"), Long.toString(file.getId()));
        if (file.getType().compareTo(TSK_DB_FILES_TYPE_ENUM.LOCAL) == 0) {
            addRow(sb, NbBundle.getMessage(this.getClass(), "Metadata.tableRowTitle.localPath"), file.getLocalAbsPath());
        }

        endTable(sb);

        /*
         * If we have a file system file, grab the more detailed metadata text
         * too
         */
        try {
            if (file instanceof FsContent) {
                FsContent fsFile = (FsContent) file;

                sb.append("<hr /><pre>\n"); //NON-NLS
                sb.append(NbBundle.getMessage(this.getClass(), "Metadata.nodeText.text"));
                sb.append(" <br /><br />"); // NON-NLS
                for (String str : fsFile.getMetaDataText()) {
                    sb.append(str).append("<br />"); //NON-NLS
                    
                    /* 
                     * Very long results can cause the UI to hang before displaying,
                     * so truncate the results if necessary.
                     */
                    if(sb.length() > 50000){
                        sb.append(NbBundle.getMessage(this.getClass(), "Metadata.nodeText.truncated"));
                        break;
                    }
                }
                sb.append("</pre>\n"); //NON-NLS
            }
        } catch (TskCoreException ex) {
            sb.append(NbBundle.getMessage(this.getClass(), "Metadata.nodeText.exceptionNotice.text")).append(ex.getLocalizedMessage());
        }

        setText(sb.toString());
        jTextPane1.setCaretPosition(0);
        this.setCursor(null);
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "Metadata.title");
    }

    @Override
    public String getToolTip() {
        return NbBundle.getMessage(this.getClass(), "Metadata.toolTip");
    }

    @Override
    public DataContentViewer createInstance() {
        return new Metadata();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        setText("");
        return;
    }

    @Override
    public boolean isSupported(Node node) {
        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        return file != null;
    }

    @Override
    public int isPreferred(Node node) {
        return 1;
    }
}
