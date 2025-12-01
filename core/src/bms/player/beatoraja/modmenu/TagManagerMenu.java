package bms.player.beatoraja.modmenu;

import bms.player.beatoraja.tags.*;

import bms.player.beatoraja.MainController;

import imgui.ImGui;
import imgui.type.ImString;
import imgui.flag.ImGuiWindowFlags;

// import java.util.*;
// import bms.player.beatoraja.select.MusicSelector;

public class TagManagerMenu {
    private static MainController main = null;

    public static void init(MainController mainState) { main = mainState; }

    private static boolean spawnRename = false;
    private static boolean spawnLevelSymbolRename = false;
    private static boolean spawnChangeLevel = false;
    private static boolean spawnRemove = false;

    private static TagHeader header = null;
    private static TagPath path = null;
    private static ImString renameTextField = new ImString(256);
    private static String hint;

    public static void spawnRenamePopup(TagHeader header, TagPath path) {
        TagManagerMenu.header = header;
        TagManagerMenu.path = path;
        spawnRename = true;
        renameTextField = new ImString(256);

        hint = header.getName();
        if (path != null) {
            hint = path.getName();
            if (hint.isEmpty()) { hint = header.getLevelsymbol() + path.getLevel(); }
        }
    }

    public static void spawnLevelSymbolPopup(TagHeader tag) {
        TagManagerMenu.header = tag;
        spawnLevelSymbolRename = true;
        renameTextField = new ImString(256);
    }

    public static void spawnChangeLevelPopup(TagHeader header, TagPath path) {
        TagManagerMenu.header = header;
        TagManagerMenu.path = path;
        spawnChangeLevel = true;
        renameTextField = new ImString(256);
    }

    public static void spawnRemovePopup(TagHeader header, TagPath path) {
        TagManagerMenu.header = header;
        TagManagerMenu.path = path;
        spawnRemove = true;

        hint = header.getName();
        if (path != null) {
            hint = path.getName();
            if (hint.isEmpty()) { hint = header.getLevelsymbol() + path.getLevel(); }
        }
    }

    public static void show() {
        renamePopup();
        renameLevelSymbolPopup();
        changeLevelPopup();
        removePopup();
    }

    public static void renamePopup() {
        if (spawnRename) {
            spawnRename = false;
            ImGui.openPopup("tags-rename-popup");
        }

        if (!ImGui.beginPopup("tags-rename-popup", ImGuiWindowFlags.AlwaysAutoResize)) { return; }

        ImGui.textDisabled(String.format("Renaming: %s", hint));
        // ImGui.setKeyboardFocusHere();
        if (ImGui.inputTextWithHint("##tags-rename-field", hint, renameTextField)) {}
        if (ImGui.button(" Confirm ##tags-rename-confirm")) {
            main.getTagManager().renameTag(header, path, renameTextField.get());
            header = null;
            path = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    public static void renameLevelSymbolPopup() {
        if (spawnLevelSymbolRename) {
            spawnLevelSymbolRename = false;
            ImGui.openPopup("tags-rename-level-symbol-popup");
        }

        if (!ImGui.beginPopup("tags-rename-level-symbol-popup",
                              ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }

        ImGui.textDisabled(String.format("Changing level symbol: %s", header.getLevelsymbol()));
        if (ImGui.inputTextWithHint("##tags-rename-field", header.getLevelsymbol(),
                                    renameTextField)) {}
        if (ImGui.button(" Confirm ##tags-rename-confirm")) {
            main.getTagManager().renameLevelSymbol(header.getId(), renameTextField.get());
            header = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    public static void changeLevelPopup() {
        if (spawnChangeLevel) {
            spawnChangeLevel = false;
            ImGui.openPopup("tags-change-level-popup");
        }

        if (!ImGui.beginPopup("tags-change-level-popup", ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }

        ImGui.textDisabled(String.format("Changing level: %s", String.valueOf(path.getLevel())));
        if (ImGui.inputTextWithHint("##tags-rename-field", String.valueOf(path.getLevel()),
                                    renameTextField)) {}
        if (ImGui.button(" Confirm ##tags-rename-confirm")) {
            try {
                int level = Integer.parseInt(renameTextField.get());
                main.getTagManager().changeLevel(path.getId(), level);
            }
            catch (NumberFormatException e) {
            }
            header = null;
            path = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    public static void removePopup() {
        if (spawnRemove) {
            spawnRemove = false;
            ImGui.openPopup("tags-remove-confirmation-popup");
        }

        if (!ImGui.beginPopup("tags-remove-confirmation-popup",
                              ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }

        ImGui.textDisabled(String.format("Removing tag: %s", hint));
        ImGui.text("Are you sure?");
        ImGui.sameLine();
        if (ImGui.button(" Confirm ##tags-remove-confirm")) {
            main.getTagManager().removeTag(header, path);
            header = null;
            path = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.textDisabled("(click outside popup to cancel)");
        ImGui.endPopup();
    }
}
