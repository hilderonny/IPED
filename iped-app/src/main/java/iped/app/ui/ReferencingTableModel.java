/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.app.ui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;

import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class ReferencingTableModel extends AbstractTableModel
        implements MouseListener, ListSelectionListener, SearchResultTableModel {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private LuceneSearchResult results = new LuceneSearchResult(0);
    private int selectedIndex = -1;

    public void clear() {
        results = new LuceneSearchResult(0);
        fireTableDataChanged();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public int getRowCount() {
        return results.getLength();
    }

    @Override
    public String getColumnName(int col) {
        if (col == 2)
            return IndexItem.NAME;

        return ""; //$NON-NLS-1$
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        if (col == 1) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Class<?> getColumnClass(int c) {
        if (c == 1) {
            return Boolean.class;
        } else {
            return String.class;
        }
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        App.get().appCase.getMultiBookmarks().setChecked((Boolean) value,
                App.get().appCase.getItemId(results.getLuceneIds()[row]));
        BookmarksController.get().updateUI();
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (col == 0) {
            return row + 1;

        } else if (col == 1) {
            return App.get().appCase.getMultiBookmarks()
                    .isChecked(App.get().appCase.getItemId(results.getLuceneIds()[row]));

        } else {
            try {
                Document doc = App.get().appCase.getSearcher().doc(results.getLuceneIds()[row]);
                return doc.get(IndexItem.NAME);
            } catch (Exception e) {
                // e.printStackTrace();
            }
            return ""; //$NON-NLS-1$
        }
    }

    @Override
    public void mouseClicked(MouseEvent arg0) {
    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
    }

    @Override
    public void mouseExited(MouseEvent arg0) {
    }

    @Override
    public void mousePressed(MouseEvent arg0) {
    }

    @Override
    public void mouseReleased(MouseEvent evt) {
        if (evt.getClickCount() == 2 && selectedIndex != -1) {
            int docId = results.getLuceneIds()[selectedIndex];
            ExternalFileOpen.open(docId);
        }

    }

    @Override
    public void valueChanged(ListSelectionEvent evt) {
        ListSelectionModel lsm = (ListSelectionModel) evt.getSource();

        if (lsm.getMinSelectionIndex() == -1 || selectedIndex == lsm.getMinSelectionIndex()) {
            selectedIndex = lsm.getMinSelectionIndex();
            return;
        }

        selectedIndex = lsm.getMinSelectionIndex();
        int id = results.getLuceneIds()[selectedIndex];

        FileProcessor parsingTask = new FileProcessor(id, false);
        parsingTask.execute();

    }

    public void listReferencingItems(Document doc) {

        String[] linkedItems = doc.getValues(ExtraProperties.LINKED_ITEMS);
        
        if (linkedItems == null || linkedItems.length == 0) {
            results = new LuceneSearchResult(0);
        } else {

            StringBuilder textQuery = new StringBuilder();
            for (String q : linkedItems) {
                textQuery.append("(").append(q).append(") ");
            }
    
            try {
                IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery.toString(), BasicProps.NAME);
                results = MultiSearchResult.get(task.multiSearch(), App.get().appCase);
    
                final int length = results.getLength();
    
                if (length > 0) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            App.get().referencesDock.setTitleText(Messages.getString("ReferencesTab.Title") + " " + length);
                        }
                    });
                }
    
            } catch (Exception e) {
                results = new LuceneSearchResult(0);
                e.printStackTrace();
            }
        }

        fireTableDataChanged();

    }

    @Override
    public MultiSearchResult getSearchResult() {
        return MultiSearchResult.get(App.get().appCase, results);
    }

}
