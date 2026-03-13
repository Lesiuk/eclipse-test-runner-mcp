package uk.l3si.eclipse.mcp;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class McpToolPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    static final String PREF_DISABLED_TOOLS = "disabledTools";

    private final Map<String, Button> checkboxes = new LinkedHashMap<>();

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(Activator.getInstance().getPreferenceStore());
        setDescription("Select which MCP tools are available to AI assistants.");
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        ToolRegistry registry = Activator.getInstance().getToolRegistry();
        Map<String, List<McpTool>> toolsByGroup = registry.getToolsByGroup();

        for (Map.Entry<String, List<McpTool>> entry : toolsByGroup.entrySet()) {
            Group group = new Group(container, SWT.NONE);
            group.setText(entry.getKey());
            group.setLayout(new GridLayout(1, false));
            group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

            List<Button> groupCheckboxes = new ArrayList<>();

            Composite buttonBar = new Composite(group, SWT.NONE);
            RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
            rowLayout.spacing = 10;
            rowLayout.marginHeight = 0;
            buttonBar.setLayout(rowLayout);

            Button selectAll = new Button(buttonBar, SWT.PUSH);
            selectAll.setText("Select All");

            Button deselectAll = new Button(buttonBar, SWT.PUSH);
            deselectAll.setText("Deselect All");

            Label separator = new Label(group, SWT.SEPARATOR | SWT.HORIZONTAL);
            separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            for (McpTool tool : entry.getValue()) {
                Button checkbox = new Button(group, SWT.CHECK);
                checkbox.setText(tool.getName() + "  \u2014  " + shortDescription(tool.getDescription()));
                checkbox.setSelection(registry.isToolEnabled(tool.getName()));
                checkbox.setToolTipText(tool.getDescription());
                checkboxes.put(tool.getName(), checkbox);
                groupCheckboxes.add(checkbox);
            }

            selectAll.addListener(SWT.Selection, e -> {
                for (Button cb : groupCheckboxes) cb.setSelection(true);
            });
            deselectAll.addListener(SWT.Selection, e -> {
                for (Button cb : groupCheckboxes) cb.setSelection(false);
            });
        }

        return container;
    }

    private static String shortDescription(String description) {
        if (description == null || description.isEmpty()) return "";
        int end = description.indexOf(". ");
        if (end < 0) end = description.endsWith(".") ? description.length() - 1 : description.length();
        String text = description.substring(0, end);
        if (text.length() > 100) text = text.substring(0, 97) + "...";
        return text;
    }

    @Override
    public boolean performOk() {
        ToolRegistry registry = Activator.getInstance().getToolRegistry();
        Set<String> disabled = new LinkedHashSet<>();

        for (Map.Entry<String, Button> entry : checkboxes.entrySet()) {
            if (!entry.getValue().getSelection()) {
                disabled.add(entry.getKey());
            }
        }

        registry.setDisabledTools(disabled);
        getPreferenceStore().setValue(PREF_DISABLED_TOOLS, String.join(",", disabled));

        return super.performOk();
    }

    @Override
    protected void performDefaults() {
        for (Button checkbox : checkboxes.values()) {
            checkbox.setSelection(true);
        }
        super.performDefaults();
    }
}
