// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;


import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.dictionary.CustomDictionaryProvider;
import com.intellij.spellchecker.dictionary.location.DictionaryLocation;
import com.intellij.spellchecker.dictionary.location.LocalDictionaryLocation;
import com.intellij.spellchecker.dictionary.location.RepositoryDictionaryLocation;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.ide.plugins.PluginManager.isPluginInstalled;
import static com.intellij.openapi.extensions.PluginId.getId;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.util.containers.ContainerUtil.concat;
import static java.util.Arrays.asList;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

public class CustomDictionariesPanel extends JPanel {
  private final SpellCheckerSettings mySettings;
  @NotNull private final SpellCheckerManager myManager;
  private final CustomDictionariesTableView myCustomDictionariesTableView;
  private final List<String> removedDictionaries = new ArrayList<>();
  private final List<String> defaultDictionaries;

  public CustomDictionariesPanel(@NotNull SpellCheckerSettings settings, @NotNull Project project, @NotNull SpellCheckerManager manager) {
    mySettings = settings;
    myManager = manager;
    defaultDictionaries = project.isDefault() ? new ArrayList<>() : asList(SpellCheckerBundle.message("app.dictionary"), SpellCheckerBundle
      .message("project.dictionary"));
    myCustomDictionariesTableView = new CustomDictionariesTableView(new ArrayList<>(settings.getCustomDictionariesPaths()),
                                                                    defaultDictionaries,
                                                                    new ArrayList<>(settings.getDisabledDictionariesPaths()));
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myCustomDictionariesTableView)

      .setAddActionName(SpellCheckerBundle.message("add.custom.dictionaries"))
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myCustomDictionariesTableView.stopEditing();
          final PluginId hunspellId = getId("hunspell");
          final IdeaPluginDescriptor ideaPluginDescriptor = PluginManager.getPlugin(hunspellId);
          if (isPluginInstalled(hunspellId) && ideaPluginDescriptor != null && ideaPluginDescriptor.isEnabled()) {
            JBList<DictionaryLocation> locationList = new JBList<>();
            locationList.setListData(new DictionaryLocation[]{
              new RepositoryDictionaryLocation(project), new LocalDictionaryLocation(project)
            });
            locationList.getSelectionModel().setSelectionMode(SINGLE_SELECTION);
            locationList.setCellRenderer(new ColoredListCellRenderer<DictionaryLocation>() {
              @Override
              protected void customizeCellRenderer(@NotNull JList<? extends DictionaryLocation> list,
                                                   DictionaryLocation value,
                                                   int index,
                                                   boolean selected,
                                                   boolean hasFocus) {
                append(value.getName());
              }
            });
            JBPopupFactory.getInstance().createListPopupBuilder(locationList)
                          .setTitle(SpellCheckerBundle.message("dictionary.location.choose"))
                          .setItemChoosenCallback(() -> locationList.getSelectedValue().findAndAddNewDictionary(this::accept))
                          .setMovable(false)
                          .setResizable(false)
                          .createPopup()
                          .show(button.getPreferredPopupPoint());
          }
          else {
            new LocalDictionaryLocation(project).findAndAddNewDictionary(this::accept);
          }
        }

        private void accept(String dictionary) {
          if (!myCustomDictionariesTableView.getItems().contains(dictionary)) {
            myCustomDictionariesTableView.getListTableModel().addRow(dictionary);
          }
        }
      })

      .setRemoveActionName(SpellCheckerBundle.message("remove.custom.dictionaries"))
      .setRemoveAction(button -> {
        removedDictionaries.addAll(myCustomDictionariesTableView.getSelectedObjects());
        TableUtil.removeSelectedItems(myCustomDictionariesTableView);
      })
      .setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return !ContainerUtil.exists(myCustomDictionariesTableView.getSelectedObjects(), defaultDictionaries::contains);
        }
      })

      .setEditActionName(SpellCheckerBundle.message("edit.custom.dictionary"))
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          String selectedDictionary = myCustomDictionariesTableView.getSelectedObject();
          if (selectedDictionary == null) return;

          if (defaultDictionaries.contains(selectedDictionary)) {
            selectedDictionary = selectedDictionary.equals(SpellCheckerBundle.message("app.dictionary"))
                                 ? myManager.getAppDictionaryPath()
                                 : myManager.getProjectDictionaryPath();
          }
          manager.openDictionaryInEditor(selectedDictionary);
        }
      })

      .disableUpDownActions();
    myCustomDictionariesTableView.getEmptyText().setText((SpellCheckerBundle.message("no.custom.dictionaries")));
    this.setLayout(new BorderLayout());
    this.add(decorator.createPanel(), BorderLayout.CENTER);
  }


  public List<String> getRemovedDictionaries() {
    return removedDictionaries;
  }

  public boolean isModified() {
    final List<String> oldPaths = mySettings.getCustomDictionariesPaths();
    final List<String> newPaths = ContainerUtil.filter(myCustomDictionariesTableView.getItems(), o -> !defaultDictionaries.contains(o));
    if (oldPaths.size() != newPaths.size()) {
      return true;
    }

    final Set<String> oldDisabled = mySettings.getDisabledDictionariesPaths();
    final List<String> newDisabled = myCustomDictionariesTableView.getDisabled();
    if (oldDisabled.size() != newDisabled.size()) {
      return true;
    }
    if (!newPaths.containsAll(oldPaths) || !oldPaths.containsAll(newPaths)) {
      return true;
    }
    if (!newDisabled.containsAll(oldDisabled) || !oldDisabled.containsAll(newDisabled)) {
      return true;
    }
    return false;
  }

  public void reset() {
    myCustomDictionariesTableView.getListTableModel()
      .setItems(new ArrayList<>(concat(defaultDictionaries, mySettings.getCustomDictionariesPaths())));
    myCustomDictionariesTableView.setDisabled(new ArrayList<>(mySettings.getDisabledDictionariesPaths()));
    removedDictionaries.clear();
  }

  public void apply() {
    mySettings.setCustomDictionariesPaths(new ArrayList<>(ContainerUtil.filter(myCustomDictionariesTableView.getItems(),
                                                                               dict -> !defaultDictionaries.contains(dict))));
    mySettings.setDisabledDictionariesPaths(new HashSet<>(myCustomDictionariesTableView.getDisabled()));
  }

  public List<String> getValues() {
    return myCustomDictionariesTableView.getItems();
  }

  private static class CustomDictionariesTableView extends TableView<String> {

    @NotNull private final List<String> myDefaultDictionaries;
    @NotNull private List<String> myDisabled;
    final TableCellRenderer myTypeRenderer;

    private CustomDictionariesTableView(@NotNull List<String> dictionaries,
                                        @NotNull List<String> defaultDictionaries,
                                        @NotNull List<String> disabled) {
      myDefaultDictionaries = defaultDictionaries;
      myDisabled = disabled;
      myTypeRenderer = createTypeRenderer(myDefaultDictionaries);
      setModelAndUpdateColumns(new ListTableModel<>(createDictionaryColumnInfos(), concat(defaultDictionaries, dictionaries), 1));
      setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
      getColumnModel().getColumn(0).setResizable(false);
      setShowGrid(false);
      setShowVerticalLines(false);
      setGridColor(getForeground());
      setTableHeader(null);
      TableUtil.setupCheckboxColumn(getColumnModel().getColumn(0));
    }

    @NotNull
    private List<String> getDisabled() {
      return myDisabled;
    }

    public void setDisabled(@NotNull List<String> disabled) {
      myDisabled = disabled;
    }

    private static TableCellRenderer createTypeRenderer(List<String> defaultDictionaries) {
      return new TableCellRenderer() {
        final SimpleColoredComponent myLabel = new SimpleColoredComponent();

        public Component getTableCellRendererComponent(final JTable table,
                                                       final Object value,
                                                       final boolean isSelected,
                                                       final boolean hasFocus,
                                                       final int row,
                                                       final int column) {

          myLabel.clear();
          myLabel.append((String)value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          final String type;
          if (defaultDictionaries.contains(value)) {
            type = SpellCheckerBundle.message("built.in.dictionary");
          }
          else {
            final CustomDictionaryProvider provider = Stream.of(Extensions.getExtensions(CustomDictionaryProvider.EP_NAME))
              .filter(dictionaryProvider -> dictionaryProvider.isApplicable((String)value))
              .findAny()
              .orElse(null);
            type = provider != null ? provider.getDictionaryType() : SpellCheckerBundle.message("words.list.dictionary");
          }
          myLabel.append(" [" + type + "]", GRAY_ATTRIBUTES);
          myLabel.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
          myLabel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
          return myLabel;
        }
      };
    }

    private ColumnInfo[] createDictionaryColumnInfos() {
      return new ColumnInfo[]{
        new ColumnInfo<String, Boolean>(" ") {
          @Override
          public Class<?> getColumnClass() {
            return Boolean.class;
          }

          @Override
          public void setValue(String s, Boolean value) {
            if (value) {
              myDisabled.remove(s);
            }
            else {
              myDisabled.add(s);
            }
          }

          @Override
          public boolean isCellEditable(String s) {
            return !myDefaultDictionaries.contains(s);
          }

          @Override
          public Boolean valueOf(final String o) {
            return !myDisabled.contains(o);
          }
        },
        new ColumnInfo<String, String>(SpellCheckerBundle.message("custom.dictionary.title")) {
          @Override
          public String valueOf(final String info) {
            return info;
          }

          @Nullable
          @Override
          public TableCellRenderer getRenderer(String s) {
            return myTypeRenderer;
          }
        }
      };
    }
  }
}