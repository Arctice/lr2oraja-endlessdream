package bms.player.beatoraja.modmenu;

import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.io.IOException;
import java.nio.file.*;

import bms.player.beatoraja.skin.*;
import bms.player.beatoraja.skin.json.JSONSkinLoader;
import bms.player.beatoraja.skin.lr2.LR2SkinHeaderLoader;
import bms.player.beatoraja.skin.lua.LuaSkinLoader;

import bms.player.beatoraja.MainState;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.SkinConfig;
import bms.player.beatoraja.PlayerConfig;

import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.BarRenderer;

import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;

public class SkinMenu {
	private static MainController main = null;
	private static PlayerConfig playerConfig;
    public static void init(MainController mainState, PlayerConfig config) {
        main = mainState;
        playerConfig = config;
    }

    private static boolean ready = false;
    private static SkinType currentSkinType;
    private static List<SkinHeader> skins;

    public static void show(ImBoolean showSkinMenu) {
        if (main == null) return;
        if (!ready) {
            refresh();
            ready = true;
        }

        if (ImGui.begin("Skin", showSkinMenu)) {
            for (var header : skins) {
                String skinPath = header.getPath().toString();
                if (ImGui.button(header.getName())) {
                    switchCurrentSceneSkin(header);
                }
                ImGui.text(skinPath);
                ImGui.text(String.format("%s %s", header.getSkinType(), header.getType()));
            }
        }
        ImGui.end();
    }

    public static void invalidate() { ready = false; }

    private static void refresh() {
		Skin currentSkin = main.getCurrentState().getSkin();
        currentSkinType = currentSkin.header.getSkinType();
		skins = loadAllSkins(currentSkinType);
    }

    private static List<SkinHeader> loadAllSkins(SkinType type) {
        List<Path> paths = new ArrayList<>();
        Path skinsDir = Paths.get("skin");
        scanSkins(skinsDir, paths);
        List<SkinHeader> skins = new ArrayList<SkinHeader>();
        for (Path path : paths) {
            String pathString = path.toString().toLowerCase();
            SkinHeader header = null;
            if (pathString.endsWith(".json")) {
                JSONSkinLoader loader = new JSONSkinLoader();
                header = loader.loadHeader(path);
            }
            else if (pathString.endsWith(".luaskin")) {
                LuaSkinLoader loader = new LuaSkinLoader();
                header = loader.loadHeader(path);
            }

            if (header != null && header.getSkinType() == type) { skins.add(header); }
        }

        return skins;
    }

    private static void scanSkins(Path path, List<Path> paths) {
        if (Files.isDirectory(path)) {
            try (Stream<Path> sub = Files.list(path)) {
                sub.forEach((Path t) -> { scanSkins(t, paths); });
            }
            catch (IOException e) {}
        }
        else if (path.getFileName().toString().toLowerCase().endsWith(".lr2skin") ||
                 path.getFileName().toString().toLowerCase().endsWith(".luaskin") ||
                 path.getFileName().toString().toLowerCase().endsWith(".json")) {
            paths.add(path);
        }
    }

    private static void switchCurrentSceneSkin(SkinHeader header) {
        String skinPath = header.getPath().toString();
        SkinConfig config = new SkinConfig();
        MainState scene = main.getCurrentState();

        for (SkinConfig skinConfs : playerConfig.getSkinHistory()) {
            if (skinConfs.getPath().equals(skinPath)) {
                config.setProperties(skinConfs.getProperties());
                break;
            }
        }
        if (config.getProperties() == null) {
            config.setProperties(new SkinConfig.Property());
        }

        config.setPath(skinPath);
        Skin skin = SkinLoader.load(scene, currentSkinType, config);
        scene.setSkin(skin);
        skin.prepare(scene);

        if (scene instanceof MusicSelector) {
            ((MusicSelector)scene).getBarRender().updateBarText();
        }
    }
}

// why is simple play complaining when loaded
