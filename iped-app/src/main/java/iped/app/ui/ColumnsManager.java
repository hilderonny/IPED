package iped.app.ui;

import java.awt.Dialog.ModalityType;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;

import org.apache.lucene.document.Document;
import org.apache.tika.metadata.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemId;
import iped.engine.config.AnalysisConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDSource;
import iped.engine.data.Item;
import iped.engine.search.LoadIndexFields;
import iped.engine.task.HashDBLookupTask;
import iped.engine.task.LanguageDetectTask;
import iped.engine.task.NamedEntityTask;
import iped.engine.task.PhotoDNALookup;
import iped.engine.task.index.IndexItem;
import iped.engine.task.regex.RegexTask;
import iped.engine.util.Util;
import iped.localization.LocalizedProperties;
import iped.parsers.ocr.OCRParser;
import iped.parsers.standard.StandardParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.viewers.api.IColumnsManager;
import iped.viewers.util.ProgressDialog;

public class ColumnsManager implements Serializable, IColumnsManager {

    private static final long serialVersionUID = 1057562688829969313L;

    private static Logger LOGGER = LoggerFactory.getLogger(ColumnsManager.class);

    private static final File globalCols = getGlobalColsFile();

    private static final List<Integer> defaultWidths = Arrays.asList(50, 100, 200, 50, 50, 100, 60, 150, 155, 155, 155, 155,
            155, 155, 250, 2000);

    public static final String[] groupNames = { Messages.getString("ColumnsManager.Basic"),
            Messages.getString("ColumnsManager.HashDB"), Messages.getString("ColumnsManager.Advanced"), //$NON-NLS-2$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.Common"), // $NON-NLS-2$
            Messages.getString("ColumnsManager.Communication"), Messages.getString("ColumnsManager.Audio"), //$NON-NLS-2$
            Messages.getString("ColumnsManager.Image"), Messages.getString("ColumnsManager.Video"), //$NON-NLS-1$
            Messages.getString("ColumnsManager.PDF"), Messages.getString("ColumnsManager.Office"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.HTML"), Messages.getString("ColumnsManager.Regex"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.Language"), Messages.getString("ColumnsManager.NamedEntity"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.PeerToPeer"), Messages.getString("ColumnsManager.UFED"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ColumnsManager.Other"), Messages.getString("ColumnsManager.All") }; //$NON-NLS-1$ //$NON-NLS-2$

    private static final File getGlobalColsFile() {
        String name = "visibleCols"; //$NON-NLS-1$
        String locale = System.getProperty(iped.localization.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
        if (locale != null && !locale.equals("pt-BR")) //$NON-NLS-1$
            name += "-" + locale; //$NON-NLS-1$
        name += ".dat"; //$NON-NLS-1$
        return new File(System.getProperty("user.home") + "/.iped/" + name); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static final String[] defaultFields = { ResultTableModel.SCORE_COL, ResultTableModel.BOOKMARK_COL,
            IndexItem.NAME, IndexItem.EXT, IndexItem.TYPE, IndexItem.LENGTH, IndexItem.DELETED, IndexItem.CATEGORY, IndexItem.CREATED,
            IndexItem.MODIFIED, IndexItem.ACCESSED, IndexItem.CHANGED, IndexItem.TIMESTAMP, IndexItem.TIME_EVENT,
            IndexItem.HASH, IndexItem.PATH };

    private static final String[] extraFields = { IndexItem.CARVED, IndexItem.CONTENTTYPE,
            IndexItem.HASCHILD, IndexItem.ID, IndexItem.ISDIR, IndexItem.ISROOT, IndexItem.PARENTID,
            IndexItem.PARENTIDs, IndexItem.SUBITEMID, IndexItem.ID_IN_SOURCE, IndexItem.SOURCE_PATH,
            IndexItem.SOURCE_DECODER, IndexItem.SUBITEM, IndexItem.TIMEOUT, IndexItem.TREENODE, IndexItem.EVIDENCE_UUID,
            StandardParser.PARSER_EXCEPTION, OCRParser.OCR_CHAR_COUNT, ExtraProperties.CSAM_HASH_HITS,
            ExtraProperties.P2P_REGISTRY_COUNT, ExtraProperties.SHARED_HASHES, ExtraProperties.SHARED_ITEMS,
            ExtraProperties.LINKED_ITEMS, ExtraProperties.TIKA_PARSER_USED, IndexItem.META_ADDRESS,
            IndexItem.MFT_SEQUENCE, IndexItem.FILESYSTEM_ID };

    private static ColumnsManager instance;

    private IPEDSource lastCase;

    File moduleDir;
    private File caseCols;
    private File reportCols;

    String[] indexFields = null;

    public String[][] fieldGroups;

    ColumnState colState = new ColumnState();

    private ArrayList<String> loadedFields = new ArrayList<String>();

    private int firstColsToPin = 7;

    private boolean autoManageCols;

    public boolean isAutoManageCols() {
        return autoManageCols;
    }
    public void  setAutoManageCols(boolean autoManageCOls) {
        this.autoManageCols = autoManageCOls;
    }

    public static ColumnsManager getInstance() {
        if (instance == null)
            instance = new ColumnsManager();
        return instance;
    }

    public void setPinnedColumns(int firstColsToPin) {
        this.firstColsToPin = firstColsToPin;
    }

    public int getPinnedColumns() {
        return this.firstColsToPin;
    }

    @Override
    public String[] getLoadedCols() {
        String[] cols = loadedFields.toArray(new String[0]);
        return cols;
    }

    public ArrayList<String> getLoadedFields() {
        return loadedFields;
    }

    static class ColumnState implements Serializable {

        private static final long serialVersionUID = 1L;
        List<Integer> initialWidths = new ArrayList<Integer>();
        ArrayList<String> visibleFields = new ArrayList<String>();
    }

    static class ColumnsReport implements Serializable {

        private static final long serialVersionUID = 1L;
        ArrayList<String> selectedCols = new ArrayList<String>();
    }

    protected void saveReportColumns() {
        try {
            ColumnsReport cr = new ColumnsReport();
            cr.selectedCols.addAll(ColumnsManagerUI.getInstance().getSelectedProperties());
            if (cr.selectedCols.size() > 0) {
                Util.writeObject(cr, reportCols.getAbsolutePath());
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    public void saveColumnsState() {
        try {
            ColumnState cs = new ColumnState();
            for (int i = 0; i < App.get().resultsTable.getColumnModel().getColumnCount(); i++) {
                TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i);
                if (tc.getModelIndex() >= ResultTableModel.fixedCols.length) {
                    int idx = tc.getModelIndex() - ResultTableModel.fixedCols.length;
                    cs.visibleFields.add(loadedFields.get(idx));
                    cs.initialWidths.add(tc.getWidth());
                }
            }
            if (cs.visibleFields.size() > 0) {
                globalCols.getParentFile().mkdirs();
                Util.writeObject(cs, globalCols.getAbsolutePath());
                Util.writeObject(cs, caseCols.getAbsolutePath());
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    protected ColumnsManager() {
        AnalysisConfig analysisConfig = ConfigurationManager.get().findObject(AnalysisConfig.class);
        autoManageCols = analysisConfig.isAutoManageCols();

        moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
        reportCols = new File(moduleDir, "reportCols.dat");

        updateDinamicFields();

        loadSavedCols();
    }

    private File getColStateFile() {
        caseCols = new File(moduleDir, "visibleCols.dat"); //$NON-NLS-1$
        File cols = caseCols;
        if (!cols.exists())
            cols = globalCols;
        return cols;
    }

    private void loadSavedCols() {
        File cols = getColStateFile();
        boolean lastColsOk = false;
        if (cols.exists()) {
            try {
                colState = (ColumnState) Util.readObject(cols.getAbsolutePath());
                loadedFields = (ArrayList<String>) colState.visibleFields.clone();
                if (loadedFields.size() > 0) {
                    lastColsOk = true;
                    HashSet<String> indexedSet = new HashSet<String>();
                    indexedSet.addAll(Arrays.asList(indexFields));
                    // remove inexistent columns in current case
                    int removed = 0;
                    for (int i = 0; i < loadedFields.size(); i++) {
                        String field = loadedFields.get(i);
                        if (!indexedSet.contains(field) && !field.equals(ResultTableModel.SCORE_COL)
                                && !field.equals(ResultTableModel.BOOKMARK_COL)) {
                            colState.visibleFields.remove(i - removed);
                            colState.initialWidths.remove(i - removed);
                            removed++;
                        }
                    }
                    if (removed > 0)
                        loadedFields = (ArrayList<String>) colState.visibleFields.clone();
                }

            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
        }
        if (!lastColsOk) {
            LOGGER.info("Loading default columns"); //$NON-NLS-1$
            for (String col : defaultFields)
                loadedFields.add(col);
            colState.visibleFields = (ArrayList<String>) loadedFields.clone();
            colState.initialWidths = defaultWidths;
        }
    }

    protected ArrayList<String> loadReportSelectedFields() {
        ColumnsReport cr = new ColumnsReport();
        ArrayList<String> selectedFields = null;
        if (reportCols.exists()) {
            try {
                cr = (ColumnsReport) Util.readObject(reportCols.getAbsolutePath());
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
            selectedFields = (ArrayList<String>) cr.selectedCols.clone();
        }
        return selectedFields;
    }

    public void updateDinamicCols() {
        if (!autoManageCols)
            return;

        if (App.get().ipedResult.getLength() == App.get().appCase.getTotalItems()
                || App.get().ipedResult.getLength() == 0)
            return;

        final ProgressDialog progress = new ProgressDialog(App.get(), null, false, 100, ModalityType.TOOLKIT_MODAL);
        progress.setNote(Messages.getString("ColumnsManager.LoadingCols")); //$NON-NLS-1$

        new Thread() {
            public void run() {
                final Set<String> usedCols = getUsedCols(progress);

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        updateDinamicCols(usedCols);
                        progress.close();
                    }
                });
            }
        }.start();
    }

    private Set<String> getUsedCols(ProgressDialog progress) {

        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        TreeSet<String> dinamicFields = new TreeSet<>(collator);

        int[] docs = new int[App.get().ipedResult.getLength()];
        int i = 0;
        for (IItemId item : App.get().ipedResult.getIterator())
            docs[i++] = App.get().appCase.getLuceneId(item);

        int MAX_ITEMS_TO_CHECK = 50;
        progress.setMaximum(MAX_ITEMS_TO_CHECK);

        int interval = docs.length / MAX_ITEMS_TO_CHECK;
        if (interval == 0)
            interval = 1;

        int p = 0;
        for (i = 0; i < docs.length; i += interval) {
            if (progress.isCanceled())
                return null;
            try {
                Document doc = App.get().appCase.getReader().document(docs[i]);
                for (String field : indexFields) {
                    if (doc.getField(field) != null)
                        dinamicFields.add(field);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            progress.setProgress(++p);
        }
        return dinamicFields;
    }

    private void updateDinamicCols(Set<String> dinamicFields) {

        if (dinamicFields == null)
            return;

        Set<String> colNamesToPin = new HashSet<>();
        for (int i = 2; i < Math.min(firstColsToPin, App.get().resultsTable.getColumnCount()); i++) {
            colNamesToPin.add(
                    App.get().resultsTable.getColumnModel().getColumn(i).getHeaderValue().toString().toLowerCase());
        }

        for (String field : (List<String>) colState.visibleFields.clone()) {
            if (!dinamicFields.contains(field) && !colNamesToPin.contains(field.toLowerCase())
                    && !field.equals(BasicProps.LENGTH)) // length header changes to listed items total size info
            {
                updateCol(field, false);
            }
        }

        int newColStart = App.get().resultsTable.getColumnCount();

        for (String field : dinamicFields) {
            if (!colState.visibleFields.contains(field))
                updateCol(field, true);
        }

        // move important new cols to front
        int newPosEmail = firstColsToPin;
        int newPosOther = firstColsToPin;
        for (int i = newColStart; i < App.get().resultsTable.getColumnCount(); i++) {
            TableColumn col = App.get().resultsTable.getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            if (colName.startsWith(ExtraProperties.MESSAGE_PREFIX)
                    || colName.startsWith(ExtraProperties.COMMUNICATION_PREFIX)) {
                App.get().resultsTable.moveColumn(i, newPosEmail++);
                newPosOther++;
            } else if (colName.toLowerCase().startsWith(ExtraProperties.UFED_META_PREFIX)) {
                App.get().resultsTable.moveColumn(i, newPosOther++);
            }
        }

        // move important old cols to front
        int lastOldCol = newColStart - 1 + newPosOther - firstColsToPin;
        newPosEmail = firstColsToPin;
        for (int i = newPosOther; i <= lastOldCol; i++) {
            TableColumn col = App.get().resultsTable.getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            if (colName.startsWith(ExtraProperties.MESSAGE_PREFIX)
                    || colName.startsWith(ExtraProperties.COMMUNICATION_PREFIX)) {
                App.get().resultsTable.moveColumn(i, newPosEmail++);
            }
        }
    }

    public void moveTimelineColumns(int newPos) {
        String[] timeFields = { BasicProps.TIMESTAMP, BasicProps.TIME_EVENT };
        for (int i = 0; i < App.get().resultsTable.getColumnCount(); i++) {
            TableColumn col = App.get().resultsTable.getColumnModel().getColumn(i);
            String colName = col.getHeaderValue().toString();
            for (int k = 0; k < timeFields.length; k++) {
                if (colName.equalsIgnoreCase(timeFields[k])) {
                    if (!colState.visibleFields.contains(timeFields[k])) {
                        updateCol(colName, true);
                    }
                    App.get().resultsTable.moveColumn(i, newPos);
                    if (newPos > i) {
                        i--;
                    } else {
                        newPos++;
                    }
                    timeFields[k] = null;
                }
            }
        }
    }

    public void resetToLastLayout() {
        File cols = this.getColStateFile();
        try {
            ColumnState lastState = (ColumnState) Util.readObject(cols.getAbsolutePath());
            resetColumns(lastState.visibleFields, lastState.initialWidths);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public void resetToDefaultLayout() {
        // resetColumns(Arrays.asList(defaultFields).stream().map(f ->
        // BasicProps.getLocalizedField(f))
        // .collect(Collectors.toList()), defaultWidths);
        resetColumns(Arrays.asList(defaultFields), defaultWidths);
    }

    public void resetColumns(List<String> newCols, List<Integer> widths) {
        for (String field : (List<String>) colState.visibleFields.clone())
            if (!newCols.contains(field) && !field.equals(ResultTableModel.SCORE_COL)
                    && !field.equals(ResultTableModel.BOOKMARK_COL))
                updateCol(field, false);

        for (String field : newCols) {
            if (!colState.visibleFields.contains(field))
                updateCol(field, true);
        }

        int newPos = 2;
        for (String col : newCols) {
            col = LocalizedProperties.getLocalizedField(col);
            for (int i = 0; i < App.get().resultsTable.getColumnModel().getColumnCount(); i++) {
                TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i);
                if (tc.getHeaderValue() instanceof String && ((String) tc.getHeaderValue())
                        .startsWith(col.substring(0, 1).toUpperCase() + col.substring(1))) {
                    App.get().resultsTable.moveColumn(i, newPos++);
                }
            }
        }

        int j = 0;
        for (int i = 0; i < App.get().resultsTable.getColumnModel().getColumnCount(); i++) {
            TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i);
            if (tc.getModelIndex() >= ResultTableModel.fixedCols.length && j < widths.size()) {
                tc.setPreferredWidth(widths.get(j++));
            }
        }
    }

    void updateDinamicFields() {

        if (indexFields == null || lastCase != App.get().appCase) {
            lastCase = App.get().appCase;
            indexFields = LoadIndexFields.getFields(App.get().appCase.getAtomicSources());
        }

        TreeSet<String> extraAttrs = new TreeSet<String>();
        extraAttrs.addAll(Arrays.asList(extraFields));
        extraAttrs.addAll(Item.getAllExtraAttributes());
        String[] allExtraAttrs = extraAttrs.toArray(new String[0]);
        extraAttrs.clear();

        ArrayList<String> regexFields = new ArrayList<String>();
        ArrayList<String> languageFields = new ArrayList<String>();
        ArrayList<String> audioFields = new ArrayList<String>();
        ArrayList<String> imageFields = new ArrayList<String>();
        ArrayList<String> videoFields = new ArrayList<String>();
        ArrayList<String> pdfFields = new ArrayList<String>();
        ArrayList<String> officeFields = new ArrayList<String>();
        ArrayList<String> htmlFields = new ArrayList<String>();
        ArrayList<String> nerFields = new ArrayList<String>();
        ArrayList<String> p2pFields = new ArrayList<String>();
        ArrayList<String> ufedFields = new ArrayList<String>();
        ArrayList<String> hashDbFields = new ArrayList<String>();
        ArrayList<String> communicationFields = new ArrayList<String>();
        ArrayList<String> commonFields = new ArrayList<String>();

        for (String f : allExtraAttrs) {
            if (f.startsWith(RegexTask.REGEX_PREFIX))
                regexFields.add(f);
            else if (f.startsWith(LanguageDetectTask.LANGUAGE_PREFIX))
                languageFields.add(f);
            else if (f.startsWith(HashDBLookupTask.ATTRIBUTES_PREFIX)
                    || f.startsWith(PhotoDNALookup.PHOTO_DNA_HIT_PREFIX))
                hashDbFields.add(f);
            else
                extraAttrs.add(f);
        }

        for (String f : indexFields) {
            if (f.startsWith(ExtraProperties.AUDIO_META_PREFIX))
                audioFields.add(f);
            else if (f.startsWith(ExtraProperties.IMAGE_META_PREFIX))
                imageFields.add(f);
            else if (f.startsWith(ExtraProperties.VIDEO_META_PREFIX))
                videoFields.add(f);
            else if (f.startsWith(ExtraProperties.PDF_META_PREFIX))
                pdfFields.add(f);
            else if (f.startsWith(ExtraProperties.OFFICE_META_PREFIX))
                officeFields.add(f);
            else if (f.startsWith(ExtraProperties.HTML_META_PREFIX))
                htmlFields.add(f);
            else if (f.startsWith(NamedEntityTask.NER_PREFIX))
                nerFields.add(f);
            else if (f.startsWith(ExtraProperties.P2P_META_PREFIX))
                p2pFields.add(f);
            else if (f.startsWith(ExtraProperties.UFED_META_PREFIX))
                ufedFields.add(f);
            else if (f.startsWith(Message.MESSAGE_PREFIX) || ExtraProperties.COMMUNICATION_BASIC_PROPS.contains(f))
                communicationFields.add(f);
            else if (f.startsWith(ExtraProperties.COMMON_META_PREFIX))
                commonFields.add(f);
        }

        String[][] customGroups = new String[][] { defaultFields.clone(), hashDbFields.toArray(new String[0]),
                extraAttrs.toArray(new String[0]), commonFields.toArray(new String[0]),
                communicationFields.toArray(new String[0]),
                audioFields.toArray(new String[0]), imageFields.toArray(new String[0]),
                videoFields.toArray(new String[0]), pdfFields.toArray(new String[0]),
                officeFields.toArray(new String[0]), htmlFields.toArray(new String[0]),
                regexFields.toArray(new String[0]), languageFields.toArray(new String[0]),
                nerFields.toArray(new String[0]), p2pFields.toArray(new String[0]),
                ufedFields.toArray(new String[0]) };

        ArrayList<String> otherFields = new ArrayList<String>();
        for (String f : indexFields) {
            boolean insertField = true;
            for (int i = 0; i < customGroups.length; i++) {
                if (Arrays.asList(customGroups[i]).contains(f)) {
                    insertField = false;
                    break;
                }
            }
            if (insertField)
                otherFields.add(f);
        }

        fieldGroups = new String[customGroups.length + 2][];
        for (int i = 0; i < customGroups.length; i++)
            fieldGroups[i] = customGroups[i];
        fieldGroups[fieldGroups.length - 2] = otherFields.toArray(new String[0]);
        fieldGroups[fieldGroups.length - 1] = indexFields.clone();

        for (String[] fields : fieldGroups)
            Arrays.sort(fields, Collator.getInstance());

    }

    Map<String, Integer> lastWidths = new HashMap<>();
    int lastModelIdx; 

    void updateCol(String colName, boolean insert) {
        colName = LocalizedProperties.getNonLocalizedField(colName);
        int modelIdx = loadedFields.indexOf(colName);
        if (insert) {
            colState.visibleFields.add(colName);
            if (modelIdx == -1) {
                loadedFields.add(colName);
                App.get().resultsModel.updateCols();
                modelIdx = ResultTableModel.fixedCols.length + loadedFields.size() - 1;
            } else
                modelIdx += ResultTableModel.fixedCols.length;
        } else {
            colState.visibleFields.remove(colName);
            modelIdx += ResultTableModel.fixedCols.length;
            int viewIdx = App.get().resultsTable.convertColumnIndexToView(modelIdx);
            if (viewIdx > -1) {
                TableColumn col = App.get().resultsTable.getColumnModel().getColumn(viewIdx);
                lastWidths.put(colName, col.getWidth());
            }
        }
        lastModelIdx = modelIdx;
    }

}
