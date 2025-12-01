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
    private static TagHeader currentTag = null;
    private static ImString renameTextField = new ImString(256);

    public static void spawnRenamePopup(TagHeader tag) {
        currentTag = tag;
        spawnRename = true;
        renameTextField = new ImString(256);
    }

    public static void show() {
        if (spawnRename) {
            spawnRename = false;
            ImGui.openPopup("tag-manager-rename-popup");
        }

        if (ImGui.beginPopup("tag-manager-rename-popup", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textDisabled(String.format("Renaming: %s", currentTag.getName()));
            // ImGui.setKeyboardFocusHere();
            if (ImGui.inputTextWithHint("##tag-manager-rename-field", currentTag.getName(),
                                        renameTextField)) {}
            if (ImGui.button(" Confirm ##tag-manager-rename-confirm")) {
                main.getTagManager().renameTag(currentTag.getId(), renameTextField.get());
                currentTag = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
        else {
            currentTag = null;
        }
    }
}
