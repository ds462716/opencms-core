/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ui.editors.messagebundle;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.i18n.CmsMessages;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.search.CmsSearchException;
import org.opencms.search.solr.CmsSolrIndex;
import org.opencms.search.solr.CmsSolrQuery;
import org.opencms.search.solr.CmsSolrResultList;
import org.opencms.ui.FontOpenCms;
import org.opencms.ui.components.extensions.CmsAutoGrowingTextArea;
import org.opencms.ui.editors.messagebundle.CmsMessageBundleEditorModel.ConfigurableMessages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;

import org.tepi.filtertable.FilterTable;

import com.vaadin.data.Container;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.validator.AbstractStringValidator;
import com.vaadin.event.Action;
import com.vaadin.event.Action.Handler;
import com.vaadin.event.FieldEvents.FocusEvent;
import com.vaadin.event.FieldEvents.FocusListener;
import com.vaadin.event.ShortcutAction;
import com.vaadin.shared.ui.table.TableConstants;
import com.vaadin.ui.AbstractTextField;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomTable;
import com.vaadin.ui.CustomTable.CellStyleGenerator;
import com.vaadin.ui.CustomTable.ColumnGenerator;
import com.vaadin.ui.DefaultFieldFactory;
import com.vaadin.ui.Field;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.ColumnCollapseEvent;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;

/** Types and helper classes used by the message bundle editor. */
public final class CmsMessageBundleEditorTypes {

    /** Types of bundles editable by the Editor. */
    public enum BundleType {
        /** A bundle of type propertyvfsbundle. */
        PROPERTY,
        /** A bundle of type xmlvfsbundle. */
        XML,
        /** A bundle descriptor. */
        DESCRIPTOR;

        /**
         * An adjusted version of what is typically Enum.valueOf().
         * @param value the resource type name that should be transformed into BundleType
         * @return The bundle type for the resource type name, or null, if the resource has no bundle type.
         */
        public static BundleType toBundleType(String value) {

            if (null == value) {
                return null;
            }
            if (value.equals(PROPERTY.toString())) {
                return PROPERTY;
            }
            if (value.equals(XML.toString())) {
                return XML;
            }
            if (value.equals(DESCRIPTOR.toString())) {
                return DESCRIPTOR;
            }

            return null;
        }

        /**
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {

            switch (this) {
                case PROPERTY:
                    return "propertyvfsbundle";
                case XML:
                    return "xmlvfsbundle";
                case DESCRIPTOR:
                    return "bundledescriptor";
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    /** Helper for accessing Bundle descriptor XML contents. */
    public static final class Descriptor {

        /** Message node. */
        public static final String N_MESSAGE = "Message";
        /** Key node. */
        public static final String N_KEY = "Key";
        /** Description node. */
        public static final String N_DESCRIPTION = "Description";
        /** Default node. */
        public static final String N_DEFAULT = "Default";
        /** Locale in which the content is available. */
        public static final Locale LOCALE = new Locale("en");
        /** The mandatory postfix of a bundle descriptor. */
        public static final String POSTFIX = "_desc";

    }

    /**
     * Extension of {@link FilterTable} to allow to act on column collapsing (as is possible for normal {@link Table}.
     */
    public static class ExtendedFilterTable extends FilterTable {

        /** Serialization id. */
        private static final long serialVersionUID = 5242846432188542009L;

        /**
         * Register a listener for {@link ColumnCollapseEvent}.
         *
         * @param listener the listener to register.
         */
        public void addColumnCollapseListener(Table.ColumnCollapseListener listener) {

            addListener(
                TableConstants.COLUMN_COLLAPSE_EVENT_ID,
                ColumnCollapseEvent.class,
                listener,
                ColumnCollapseEvent.METHOD);
        }

        /**
         * Adds ColumnCollapseEvent fired.
         *
         * @see org.tepi.filtertable.FilterTable#setColumnCollapsed(java.lang.Object, boolean)
         */
        @Override
        public void setColumnCollapsed(final Object propertyId, final boolean collapsed) throws IllegalStateException {

            super.setColumnCollapsed(propertyId, collapsed);
            fireEvent(new ColumnCollapseEvent(this, propertyId));
        }

    }

    /** The propertyIds of the table columns. */
    public enum TableProperty {
        /** Table column with the message key. */
        KEY,
        /** Table column with the message description. */
        DESCRIPTION,
        /** Table column with the message's default value. */
        DEFAULT,
        /** Table column with the current (language specific) translation of the message. */
        TRANSLATION,
        /** Table column with the options (add, delete). */
        OPTIONS
    }

    /** Custom cell style generator to allow different style for editable columns. */
    @SuppressWarnings("serial")
    static class AddEntryTableCellStyleGenerator implements Table.CellStyleGenerator {

        /** The editable columns. */
        private List<TableProperty> m_editableColums;

        /**
         * Default constructor, taking the list of editable columns.
         *
         * @param editableColumns the list of editable columns.
         */
        public AddEntryTableCellStyleGenerator(List<TableProperty> editableColumns) {
            m_editableColums = editableColumns;
            if (null == m_editableColums) {
                m_editableColums = new ArrayList<TableProperty>();
            }
        }

        /**
         * @see com.vaadin.ui.CustomTable.CellStyleGenerator#getStyle(com.vaadin.ui.CustomTable, java.lang.Object, java.lang.Object)
         */
        public String getStyle(Table source, Object itemId, Object propertyId) {

            String result = TableProperty.KEY.equals(propertyId) ? "key-" : "";
            result += m_editableColums.contains(propertyId) ? "editable" : "fix";
            return result;
        }

    }

    /** TableFieldFactory for making only some columns editable and to support enhanced navigation. */
    @SuppressWarnings("serial")
    static class AddEntryTableFieldFactory extends DefaultFieldFactory {

        /** Mapping from column -> row -> AbstractTextField. */
        private final Map<TableProperty, AbstractTextField> m_valueFields;
        /** The editable columns. */
        private final List<TableProperty> m_editableColumns;
        /** Reference to the table, the factory is used for. */
        final Table m_table;

        /** The configurable messages to read the column headers from. */
        private final ConfigurableMessages m_configurableMessages;

        /**
         * Default constructor.
         * @param table The table, the factory is used for.
         * @param editableColumns the property names of the editable columns of the table.
         * @param configurableMessages the configurable messages to read the column headers from.
         */
        public AddEntryTableFieldFactory(
            Table table,
            List<TableProperty> editableColumns,
            ConfigurableMessages configurableMessages) {
            m_table = table;
            m_valueFields = new HashMap<TableProperty, AbstractTextField>();
            m_editableColumns = editableColumns;
            m_configurableMessages = configurableMessages;
        }

        /**
         * @see com.vaadin.ui.TableFieldFactory#createField(com.vaadin.data.Container, java.lang.Object, java.lang.Object, com.vaadin.ui.Component)
         */
        @Override
        public Field<?> createField(
            final Container container,
            final Object itemId,
            final Object propertyId,
            Component uiContext) {

            TableProperty pid = (TableProperty)propertyId;

            for (int i = 1; i <= m_editableColumns.size(); i++) {
                if (pid.equals(m_editableColumns.get(i - 1))) {

                    AbstractTextField tf;
                    if (pid.equals(TableProperty.KEY)) {
                        tf = new TextField();
                        tf.addValidator(new KeyValidator());
                    } else {
                        TextArea atf = new TextArea();
                        atf.setRows(1);
                        CmsAutoGrowingTextArea.addTo(atf, 20);
                        tf = atf;
                    }
                    tf.setWidth("100%");
                    tf.setResponsive(true);

                    tf.setInputPrompt(m_configurableMessages.getColumnHeader(pid));
                    m_valueFields.put(pid, tf);

                    //TODO: remove?
                    tf.addFocusListener(new FocusListener() {

                        public void focus(FocusEvent event) {

                            if (!m_table.isSelected(itemId)) {
                                m_table.select(itemId);
                            }
                        }

                    });
                    return tf;
                }
            }
            return null;

        }

        /**
         * Returns the editable columns.
         * @return the editable columns.
         */
        public List<TableProperty> getEditableColumns() {

            return m_editableColumns;
        }

        /**
         * Returns the mapping from the position in the table to the TextField.
         * @return the mapping from the position in the table to the TextField.
         */
        public Map<TableProperty, AbstractTextField> getValueFields() {

            return m_valueFields;
        }
    }

    /**
     * A column generater to add the row with the "+" button in the "Add entry" table.
     * It can handle only the table with the single row.
     * */
    static class AddOptionColumnGenerator implements com.vaadin.ui.Table.ColumnGenerator {

        /** Serialization id. */
        private static final long serialVersionUID = 1473149981304927914L;

        /** The handler called when the "+"-button is clicked. */
        I_AddOptionClickHandler m_handler;

        /**
         * Default constructor.
         * @param handler the handler to call when the "+" button is clicked.
         */
        public AddOptionColumnGenerator(I_AddOptionClickHandler handler) {
            m_handler = handler;
        }

        /**
         * @see com.vaadin.ui.Table.ColumnGenerator#generateCell(com.vaadin.ui.Table, java.lang.Object, java.lang.Object)
         */
        public Object generateCell(Table source, Object itemId, Object columnId) {

            CmsMessages messages = Messages.get().getBundle(UI.getCurrent().getLocale());
            Button add = new Button();
            add.addStyleName("icon-only");
            add.addStyleName("borderless");
            add.setDescription(messages.key(Messages.GUI_REMOVE_ROW_0));
            add.setIcon(FontOpenCms.CIRCLE_PLUS, messages.key(Messages.GUI_ADD_ROW_0));
            add.addClickListener(new ClickListener() {

                private static final long serialVersionUID = 1L;

                public void buttonClick(ClickEvent event) {

                    m_handler.handleAddOptionClick();
                }
            });

            return add;
        }

    }

    /** The different edit modes. */
    enum EditMode {
        /** Editing the messages and the descriptor. */
        MASTER,
        /** Only editing messages. */
        DEFAULT
    }

    /**
     * The editor state holds the information on what columns of the editors table
     * should be editable and if the options column should be shown.
     * The state depends on the loaded bundle and the edit mode.
     */
    static class EditorState {

        /** The editable columns (from left to right).*/
        private List<TableProperty> m_editableColumns;
        /** Flag, indicating if the options column should be shown. */
        private boolean m_showOptions;

        /** Constructor, setting all the state information directly.
         * @param editableColumns the property ids of the editable columns (from left to right)
         * @param showOptions flag, indicating if the options column should be shown.
         */
        public EditorState(List<TableProperty> editableColumns, boolean showOptions) {
            m_editableColumns = editableColumns;
            m_showOptions = showOptions;
        }

        /** Returns the editable columns from left to right (as there property ids).
         * @return the editable columns from left to right (as there property ids).
         */
        public List<TableProperty> getEditableColumns() {

            return m_editableColumns;
        }

        /** Returns a flag, indicating if the options column should be shown.
         * @return a flag, indicating if the options column should be shown.
         */
        public boolean isShowOptions() {

            return m_showOptions;
        }
    }

    /**
     * Interface to handle option clicks.
     */
    interface I_AddOptionClickHandler {

        /**
         * Method called, when the option is clicked.
         */
        void handleAddOptionClick();
    }

    /** Manages the keys used in at least one locale. */
    static final class KeySet {

        /** Map from keys to the number of locales they are present. */
        Map<String, Integer> m_keyset;

        /** Default constructor. */
        public KeySet() {
            m_keyset = new HashMap<String, Integer>();
        }

        /**
         * Returns the current key set.
         * @return the current key set.
         */
        public Set<String> getKeySet() {

            return new HashSet<String>(m_keyset.keySet());
        }

        /**
         * Updates the set with all keys that are used in at least one language.
         * @param oldKeys keys of a locale as registered before
         * @param newKeys keys of the locale now
         */
        public void updateKeySet(Set<String> oldKeys, Set<String> newKeys) {

            // Remove keys that are not present anymore
            if (null != oldKeys) {
                Set<String> removedKeys = new HashSet<String>(oldKeys);
                if (null != newKeys) {
                    removedKeys.removeAll(newKeys);
                }
                for (String key : removedKeys) {
                    Integer i = m_keyset.get(key);
                    int uses = null != i ? i.intValue() : 0;
                    if (uses > 1) {
                        m_keyset.put(key, Integer.valueOf(uses - 1));
                    } else if (uses == 1) {
                        m_keyset.remove(key);
                    }
                }
            }

            // Add keys that are new
            if (null != newKeys) {
                Set<String> addedKeys = new HashSet<String>(newKeys);
                if (null != oldKeys) {
                    addedKeys.removeAll(oldKeys);
                }
                for (String key : addedKeys) {
                    if (m_keyset.containsKey(key)) {
                        m_keyset.put(key, Integer.valueOf(m_keyset.get(key).intValue() + 1));
                    } else {
                        m_keyset.put(key, Integer.valueOf(1));
                    }
                }
            }

        }

    }

    /** The different ways the key set is shown. */
    enum KeySetMode {
        /** All keys used in any of the available languages. */
        ALL,
        /** Only keys used for the current language. */
        USED_ONLY;
    }

    /** Validates keys. */
    @SuppressWarnings("serial")
    static class KeyValidator extends AbstractStringValidator {

        /**
         * Default constructor.
         */
        public KeyValidator() {
            super(CmsMessageBundleEditor.m_messages.key(Messages.GUI_INVALID_KEY_0));

        }

        /**
         * @see com.vaadin.data.validator.AbstractValidator#isValidValue(java.lang.Object)
         */
        @Override
        protected boolean isValidValue(String value) {

            if (null == value) {
                return true;
            }
            return !value.matches(".*\\p{IsWhite_Space}.*");
        }

    }

    /** A column generator that additionally adjusts the appearance of the options buttons to selection changes on the table. */
    @SuppressWarnings("serial")
    static class OptionColumnGenerator implements ColumnGenerator {

        /** Map from itemId (row) -> option buttons in the row. */
        Map<Object, Collection<Component>> m_buttons;
        /** The id of the currently selected item (row). */
        Object m_selectedItem;
        /** The table, the column is generated for. */
        CustomTable m_table;

        /**
         * Default constructor.
         *
         * @param table the table, for which the column is generated for.
         */
        public OptionColumnGenerator(final CustomTable table) {
            m_buttons = new HashMap<Object, Collection<Component>>();
            m_table = table;
            m_table.addValueChangeListener(new Property.ValueChangeListener() {

                public void valueChange(ValueChangeEvent event) {

                    selectItem(m_table.getValue());
                }
            });

        }

        /**
         * @see com.vaadin.ui.CustomTable.ColumnGenerator#generateCell(com.vaadin.ui.CustomTable, java.lang.Object, java.lang.Object)
         */
        public Object generateCell(final CustomTable source, final Object itemId, final Object columnId) {

            CmsMessages messages = Messages.get().getBundle(UI.getCurrent().getLocale());
            HorizontalLayout options = new HorizontalLayout();
            Button delete = new Button();
            delete.addStyleName("icon-only");
            delete.addStyleName("borderless-colored");
            delete.setDescription(messages.key(Messages.GUI_REMOVE_ROW_0));
            delete.setIcon(FontOpenCms.CIRCLE_MINUS, messages.key(Messages.GUI_REMOVE_ROW_0));
            delete.addClickListener(new ClickListener() {

                public void buttonClick(ClickEvent event) {

                    m_table.removeItem(itemId);
                }
            });

            options.addComponent(delete);

            Collection<Component> buttons = new ArrayList<Component>(1);
            buttons.add(delete);
            m_buttons.put(itemId, buttons);

            if (source.isSelected(itemId)) {
                selectItem(itemId);
            }

            return options;
        }

        /**
         * Call this method, when a new item is selected. It will adjust the style of the option buttons, thus that they stay visible.
         *
         * @param itemId the id of the newly selected item (row).
         */
        void selectItem(final Object itemId) {

            if ((null != m_selectedItem) && (null != m_buttons.get(m_selectedItem))) {
                for (Component button : m_buttons.get(m_selectedItem)) {
                    button.removeStyleName("borderless");
                    button.addStyleName("borderless-colored");

                }
            }
            m_selectedItem = itemId;
            if ((null != m_selectedItem) && (null != m_buttons.get(m_selectedItem))) {
                for (Component button : m_buttons.get(m_selectedItem)) {
                    button.removeStyleName("borderless-colored");
                    button.addStyleName("borderless");
                }
            }
        }

    }

    /** Handler to improve the keyboard navigation in the table. */
    @SuppressWarnings("serial")
    static class TableKeyboardHandler implements Handler {

        /** The field factory keeps track of the editable rows and the row/col positions of the TextFields. */
        private FilterTable m_table;

        /** Tab was pressed. */
        private Action m_tabNext = new ShortcutAction("Tab", ShortcutAction.KeyCode.TAB, null);
        /** Tab+Shift was pressed. */
        private Action m_tabPrev = new ShortcutAction(
            "Shift+Tab",
            ShortcutAction.KeyCode.TAB,
            new int[] {ShortcutAction.ModifierKey.SHIFT});
        /** Down was pressed. */
        private Action m_curDown = new ShortcutAction("Down", ShortcutAction.KeyCode.ARROW_DOWN, null);
        /** Up was pressed. */
        private Action m_curUp = new ShortcutAction("Up", ShortcutAction.KeyCode.ARROW_UP, null);
        /** Enter was pressed. */
        private Action m_enter = new ShortcutAction("Enter", ShortcutAction.KeyCode.ENTER, null);

        /**
         * Shortcut-Handler to improve the navigation in the table component.
         *
         * @param table the table, the handler is attached to.
         */
        public TableKeyboardHandler(final FilterTable table) {
            m_table = table;
        }

        /**
         * @see com.vaadin.event.Action.Handler#getActions(java.lang.Object, java.lang.Object)
         */
        public Action[] getActions(Object target, Object sender) {

            return new Action[] {m_tabNext, m_tabPrev, m_curDown, m_curUp, m_enter};
        }

        /**
         * @see com.vaadin.event.Action.Handler#handleAction(com.vaadin.event.Action, java.lang.Object, java.lang.Object)
         */
        public void handleAction(Action action, Object sender, Object target) {

            TranslateTableFieldFactory fieldFactory = (TranslateTableFieldFactory)m_table.getTableFieldFactory();
            List<TableProperty> editableColums = fieldFactory.getEditableColumns();

            if (target instanceof AbstractTextField) {
                // Move according to keypress
                String data = (String)(((AbstractTextField)target).getData());
                // Abort if no data attribute found
                if (null == data) {
                    return;
                }
                String[] dataItems = data.split(":");
                int colId = Integer.parseInt(dataItems[0]);
                int rowId = Integer.parseInt(dataItems[1]);

                // NOTE: A collection is returned, but actually it's a linked list.
                // It's a hack, but actually I don't know how to do better here.
                List<Integer> visibleItemIds = (List<Integer>)m_table.getVisibleItemIds();

                if ((action == m_curDown) || (action == m_enter)) {
                    int currentRow = visibleItemIds.indexOf(Integer.valueOf(rowId));
                    if (currentRow < (visibleItemIds.size() - 1)) {
                        rowId = visibleItemIds.get(currentRow + 1).intValue();
                    }
                } else if (action == m_curUp) {
                    int currentRow = visibleItemIds.indexOf(Integer.valueOf(rowId));
                    if (currentRow > 0) {
                        rowId = visibleItemIds.get(currentRow - 1).intValue();
                    }
                } else if (action == m_tabNext) {
                    int nextColId = getNextColId(editableColums, colId);
                    if (colId >= nextColId) {
                        int currentRow = visibleItemIds.indexOf(Integer.valueOf(rowId));
                        rowId = visibleItemIds.get((currentRow + 1) % visibleItemIds.size()).intValue();
                    }
                    colId = nextColId;
                } else if (action == m_tabPrev) {
                    int previousColId = getPreviousColId(editableColums, colId);
                    if (colId <= previousColId) {
                        int currentRow = visibleItemIds.indexOf(Integer.valueOf(rowId));
                        rowId = visibleItemIds.get(
                            ((currentRow + visibleItemIds.size()) - 1) % visibleItemIds.size()).intValue();
                    }
                    colId = previousColId;
                }

                AbstractTextField newTF = fieldFactory.getValueFields().get(Integer.valueOf(colId)).get(
                    Integer.valueOf(rowId));
                if (newTF != null) {
                    newTF.focus();
                }
            }
        }

        /**
         * Calculates the id of the next editable column.
         * @param editableColumns all editable columns
         * @param colId id (index in <code>editableColumns</code> plus 1) of the current column.
         * @return id of the next editable column.
         */
        private int getNextColId(List<TableProperty> editableColumns, int colId) {

            for (int i = colId % editableColumns.size(); i != (colId - 1); i = (i + 1) % editableColumns.size()) {
                if (!m_table.isColumnCollapsed(editableColumns.get(i))) {
                    return i + 1;
                }
            }
            return colId;
        }

        /**
         * Calculates the id of the previous editable column.
         * @param editableColumns all editable columns
         * @param colId id (index in <code>editableColumns</code> plus 1) of the current column.
         * @return id of the previous editable column.
         */
        private int getPreviousColId(List<TableProperty> editableColumns, int colId) {

            // use +4 instead of -1 to prevent negativ numbers
            for (int i = ((colId + editableColumns.size()) - 2) % editableColumns.size(); i != (colId
                - 1); i = ((i + editableColumns.size()) - 1) % editableColumns.size()) {
                if (!m_table.isColumnCollapsed(editableColumns.get(i))) {
                    return i + 1;
                }
            }
            return colId;
        }
    }

    /** Custom cell style generator to allow different style for editable columns. */
    @SuppressWarnings("serial")
    static class TranslateTableCellStyleGenerator implements CellStyleGenerator {

        /** The editable columns. */
        private List<TableProperty> m_editableColums;

        /**
         * Default constructor, taking the list of editable columns.
         *
         * @param editableColumns the list of editable columns.
         */
        public TranslateTableCellStyleGenerator(List<TableProperty> editableColumns) {
            m_editableColums = editableColumns;
            if (null == m_editableColums) {
                m_editableColums = new ArrayList<TableProperty>();
            }
        }

        /**
         * @see com.vaadin.ui.CustomTable.CellStyleGenerator#getStyle(com.vaadin.ui.CustomTable, java.lang.Object, java.lang.Object)
         */
        public String getStyle(CustomTable source, Object itemId, Object propertyId) {

            String result = TableProperty.KEY.equals(propertyId) ? "key-" : "";
            result += m_editableColums.contains(propertyId) ? "editable" : "fix";
            return result;
        }

    }

    /** TableFieldFactory for making only some columns editable and to support enhanced navigation. */
    @SuppressWarnings("serial")
    static class TranslateTableFieldFactory extends DefaultFieldFactory {

        /** Mapping from column -> row -> AbstractTextField. */
        private final Map<Integer, Map<Integer, AbstractTextField>> m_valueFields;
        /** The editable columns. */
        private final List<TableProperty> m_editableColumns;
        /** Reference to the table, the factory is used for. */
        final CustomTable m_table;

        /**
         * Default constructor.
         * @param table The table, the factory is used for.
         * @param editableColumns the property names of the editable columns of the table.
         */
        public TranslateTableFieldFactory(CustomTable table, List<TableProperty> editableColumns) {
            m_table = table;
            m_valueFields = new HashMap<Integer, Map<Integer, AbstractTextField>>();
            m_editableColumns = editableColumns;
        }

        /**
         * @see com.vaadin.ui.TableFieldFactory#createField(com.vaadin.data.Container, java.lang.Object, java.lang.Object, com.vaadin.ui.Component)
         */
        @Override
        public Field<?> createField(
            final Container container,
            final Object itemId,
            final Object propertyId,
            Component uiContext) {

            TableProperty pid = (TableProperty)propertyId;

            for (int i = 1; i <= m_editableColumns.size(); i++) {
                if (pid.equals(m_editableColumns.get(i - 1))) {

                    AbstractTextField tf;
                    if (pid.equals(TableProperty.KEY)) {
                        tf = new TextField();
                        tf.addValidator(new KeyValidator());
                    } else {
                        TextArea atf = new TextArea();
                        atf.setRows(1);
                        CmsAutoGrowingTextArea.addTo(atf, 20);
                        tf = atf;
                    }
                    tf.setWidth("100%");
                    tf.setResponsive(true);

                    tf.setInputPrompt(CmsMessageBundleEditor.m_messages.key(Messages.GUI_PLEASE_ADD_VALUE_0));
                    tf.setData(i + ":" + itemId);
                    if (!m_valueFields.containsKey(Integer.valueOf(i))) {
                        m_valueFields.put(Integer.valueOf(i), new HashMap<Integer, AbstractTextField>());
                    }
                    m_valueFields.get(Integer.valueOf(i)).put((Integer)itemId, tf);
                    tf.addFocusListener(new FocusListener() {

                        public void focus(FocusEvent event) {

                            if (!m_table.isSelected(itemId)) {
                                m_table.select(itemId);
                            }
                        }

                    });
                    return tf;
                }
            }
            return null;

        }

        /**
         * Returns the editable columns.
         * @return the editable columns.
         */
        public List<TableProperty> getEditableColumns() {

            return m_editableColumns;
        }

        /**
         * Returns the mapping from the position in the table to the TextField.
         * @return the mapping from the position in the table to the TextField.
         */
        public Map<Integer, Map<Integer, AbstractTextField>> getValueFields() {

            return m_valueFields;
        }
    }

    /** The log object for this class. */
    static final Log LOG = CmsLog.getLog(CmsMessageBundleEditorTypes.class);

    /** Hide default constructor. */
    private CmsMessageBundleEditorTypes() {
        //noop
    }

    /**
     * Returns the bundle descriptor for the bundle with the provided base name.
     * @param cms {@link CmsObject} used for searching.
     * @param basename the bundle base name, for which the descriptor is searched.
     * @return the bundle descriptor, or <code>null</code> if it does not exist or searching fails.
     */
    public static CmsResource getDescriptor(CmsObject cms, String basename) {

        CmsSolrQuery query = new CmsSolrQuery();
        query.setResourceTypes(CmsMessageBundleEditorTypes.BundleType.DESCRIPTOR.toString());
        query.setFilterQueries("filename:\"" + basename + CmsMessageBundleEditorTypes.Descriptor.POSTFIX + "\"");
        query.add("fl", "path");
        CmsSolrResultList results;
        try {
            boolean isOnlineProject = cms.getRequestContext().getCurrentProject().isOnlineProject();
            String indexName = isOnlineProject
            ? CmsSolrIndex.DEFAULT_INDEX_NAME_ONLINE
            : CmsSolrIndex.DEFAULT_INDEX_NAME_OFFLINE;
            results = OpenCms.getSearchManager().getIndexSolr(indexName).search(cms, query, true, null, true, null);
        } catch (CmsSearchException e) {
            LOG.error(Messages.get().getBundle().key(Messages.ERR_BUNDLE_DESCRIPTOR_SEARCH_ERROR_0), e);
            return null;
        }

        switch (results.size()) {
            case 0:
                return null;
            case 1:
                return results.get(0);
            default:
                String files = "";
                for (CmsResource res : results) {
                    files += " " + res.getRootPath();
                }
                LOG.warn(Messages.get().getBundle().key(Messages.ERR_BUNDLE_DESCRIPTOR_NOT_UNIQUE_1, files));
                return results.get(0);
        }
    }
}
