package bms.player.beatoraja.tags;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import bms.tool.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;

import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.FunctionBar;
import bms.player.beatoraja.select.bar.TagBar;
import bms.player.beatoraja.select.bar.DirectoryBar;
import static bms.player.beatoraja.SystemSoundManager.SoundType.FOLDER_OPEN;
import static bms.player.beatoraja.SystemSoundManager.SoundType.OPTION_CHANGE;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.song.SongData;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.exceptions.PlayerConfigException;

import bms.table.DifficultyTable;
import bms.table.DifficultyTableParser;
import bms.table.BMSTableElement;
import bms.table.DifficultyTableElement;

import bms.player.beatoraja.TableData;
import bms.player.beatoraja.TableDataAccessor;

import bms.player.beatoraja.modmenu.TagManagerMenu;

import bms.player.beatoraja.modmenu.ImGuiNotify;
import bms.player.beatoraja.PerformanceMetrics;


public class TagManager {
	private static final Logger logger = LoggerFactory.getLogger(TagManager.class);

    private TagDatabaseAccessor tagdb;

	private MainController main;

    public TagManager(MainController main, Config config) {
        this.main = main;
        try {
            tagdb = new TagDatabaseAccessor(config);
        }
        catch (PlayerConfigException | SQLException e) {
            logger.error("Failed to access tag database: {}", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public int createTag(String name) { return tagdb.createTag(name); }

    public int createTable(String name) { return tagdb.createTable(name); }

    public int addSubfolder(int treeId) {
        int highestLevel =
            tagFolders(treeId).stream().mapToInt(path -> path.getLevel()).max().orElse(0);
        return tagdb.addChild(treeId, highestLevel + 1);
    }

    public void renameTag(TagHeader tag, TagPath path, String newName) {
        if (path == null) { tagdb.renameTag(tag.getId(), newName); }
        else { tagdb.renamePath(path.getId(), newName); }
    }

    public void removeTag(TagHeader tag, TagPath path) {
        if (path != null) { tagdb.removePath(path.getId()); }
        else { tagdb.removeHeader(tag.getId()); }
    }

    public void renameLevelSymbol(int id, String symbol) { tagdb.renameLevelSymbol(id, symbol); }

    public void changeLevel(int id, int level) { tagdb.changeLevel(id, level); }

    public void tagChart(int tag, String sha256, String md5) { tagdb.tagChart(tag, sha256, md5); }

    public void untagChart(int tag, String sha256, String md5) { tagdb.untagChart(tag, sha256, md5); }

    public void setChartTagMeta(int tag, String sha256, String md5, TagMetadata meta) {
        tagdb.setChartTagMeta(tag, sha256, md5, meta);
    }

    public TagMetadata getChartTagMeta(int tag, String sha256, String md5) {
        return tagdb.getChartTagMeta(tag, sha256, md5);
    }

    public List<TagHeader> allTagHeaders() { return tagdb.allTagHeaders(); }

    public Optional<TagHeader> tagHeader(int id) { return tagdb.tagHeader(id); }

    public List<TagPath> tagFolders(int id) { return tagdb.tagChildren(id); }

    public List<Pair<TagHeader, List<TagPath>>> chartTags(String sha256, String md5) {
        return tagdb.chartTags(sha256, md5);
    }

    public List<SongData> taggedCharts(int tag) {
        var charts = new ArrayList<SongData>();
        List<TagDatabaseAccessor.HashPair> hashlist = tagdb.taggedCharts(tag);
        for(var hashes : hashlist) {
            var chart = new SongData();
            chart.setMd5(hashes.getMd5());
            chart.setSha256(hashes.getSha256());
            TagMetadata meta = tagdb.getChartTagMeta(tag, hashes.getSha256(), hashes.getMd5());
            if(meta != null) {
                chart.setTitle(meta.getTitle());
                chart.setUrl(meta.getUrl());
                chart.setAppendurl(meta.getAppendurl());
            }
            charts.add(chart);
        }
        return charts;
    }

	public void importReset() {
		long a = System.nanoTime();
        for (var tag : allTagHeaders()) {
            removeTag(tag, null);
            System.out.println("removal: (" + tag.getName() + ") " +
                               (System.nanoTime() - a) / 1000000.);
            a = System.nanoTime();
        }
        Set<String> urlSet = new HashSet<>(List.of(main.getConfig().getTableURL()));
        TableDataAccessor tdaccessor = new TableDataAccessor(main.getConfig().getTablepath());
        TableData[] tds = tdaccessor.readAll();
        System.out.println("td read: " + (System.nanoTime() - a)/1000000.);

        for (TableData td : tds) {
            a = System.nanoTime();
            int tag = createTable(td.getName());
            tagdb.setTagSourceUrl(tag, td.getUrl());
            // non editable

            for (TableData.TableFolder folder : td.getFolder()) {
                int path = addSubfolder(tag);
                // awkward that im not using the manager API
                tagdb.renamePath(path, folder.getName());

                List<String> sha256s = new ArrayList<>();
                List<String> md5s = new ArrayList<>();
                List<TagMetadata> metas = new ArrayList<>();
                for (SongData song : folder.getSong()) {
                    sha256s.add(song.getSha256());
                    md5s.add(song.getMd5());
                    var meta = new TagMetadata();
                    meta.setTitle(song.getTitle());
                    meta.setUrl(song.getUrl());
                    meta.setAppendurl(song.getAppendurl());
                    // org md5?
                    metas.add(meta);
                }
                tagdb.tagChartBatch(path,
                                    sha256s.toArray(new String[0]),
                                    md5s.toArray(new String[0]),
                                    metas.toArray(new TagMetadata[0]));

            }
            System.out.println("time: (" + td.getName() + ") " + (System.nanoTime() - a)/1000000.);
        }
    }

    public void updateTag(TagHeader header) {
        String url = header.getSourceurl();
        if (url.isEmpty()) {
            ImGuiNotify.info(String.format("%s has no URL to reload from.", header.getName()));
            return;
        }

        DifficultyTableParser dtp = new DifficultyTableParser();
        DifficultyTable dt = new DifficultyTable();
        if (url.endsWith(".json")) {
            dt.setHeadURL(url);
        } else {
            dt.setSourceURL(url);
        }

        try {
            dtp.decode(true, dt);

            removeTag(header, null);

            int tag = createTable(dt.getName());
            tagdb.setTagSourceUrl(tag, url);
            String levelSymbol = dt.getTag();
            renameLevelSymbol(tag, levelSymbol);
            // Mode defaultMode = dt.getMode() != null ? Mode.getMode(dt.getMode()) : null;

            for(String level : dt.getLevelDescription()) {
                int path = addSubfolder(tag);
                // awkward that im not using the manager API
                tagdb.renamePath(path, levelSymbol + level);
                for(DifficultyTableElement entry : dt.getElements()) {
                    if (!entry.getLevel().equals(level)) { continue; }
                    addTableEntry(path, entry);
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
            logger.warn("難易度表 - "+url+" の読み込み失敗。");
        }

        ImGuiNotify.info(String.format("%s updated.", header.getName()));
    }

    private void addTableEntry(int tag, DifficultyTableElement entry) {
        String md5 = entry.getMD5();
        if(md5 == null) { md5 = ""; }
        String sha256 = entry.getSHA256();
        if(sha256 == null) { sha256 = ""; }
        tagChart(tag, sha256.toLowerCase(), md5.toLowerCase());
        tagdb.setChartTagMeta(tag, sha256, md5, "title", entry.getTitle());
        tagdb.setChartTagMeta(tag, sha256, md5, "artist", entry.getArtist());
        tagdb.setChartTagMeta(tag, sha256, md5, "url", entry.getURL());
        tagdb.setChartTagMeta(tag, sha256, md5, "appendurl", entry.getAppendURL());
		// song.setOrg_md5(entry.getParentHash());
		// Mode mode = entry.getMode() != null ? Mode.getMode(entry.getMode()) : null;
		// song.setMode(mode != null ? mode.id : (defaultMode != null ? defaultMode.id : 0));
    }

    public Bar[] createTagBars(MusicSelector select) {
        ArrayList<Bar> options = new ArrayList<>();

        var reset = new FunctionBar((selector, self) -> {
            selector.main.getTagManager().importReset();
            selector.getBarManager().updateBar(null);
        }, "Reset tags from tables", FunctionBar.STYLE_SPECIAL);
        options.add(reset);

        var addTag = new FunctionBar((selector, self) -> {
            selector.getBarManager().updateBar(new TagManagerBar(selector));
            selector.play(FOLDER_OPEN);
        }, "Manage Collection", FunctionBar.STYLE_SPECIAL);
        options.add(addTag);

        List<TagHeader> tags = tagdb.allTagHeaders();
        for (TagHeader tag : tags) { options.add(makeTagFolderBar(select, tag)); }

        return options.toArray(new Bar[0]);
    }

    private Bar makeTagFolderBar(MusicSelector select, TagHeader tag){
        var folder = new TagBar(select, tag, null);
        return folder;
    }

    public Bar[] tagContext(MusicSelector select, TagBar bar) {
        ArrayList<Bar> options = new ArrayList<>();

        var rename = new FunctionBar((selector, self) -> {
            TagManagerMenu.spawnRenamePopup(bar.getHeader(), bar.getPath());
        }, "Rename", FunctionBar.STYLE_SPECIAL);
        options.add(rename);

        if (bar.isTable()) {
            var levelSymbol = new FunctionBar((selector, self) -> {
                TagManagerMenu.spawnLevelSymbolPopup(bar.getHeader());
            }, "Change Level Symbol", FunctionBar.STYLE_SPECIAL);
            options.add(levelSymbol);
        }

        if (bar.isCollection() || bar.isTable()) {
            var addFolder = new FunctionBar((selector, self) -> {
                addSubfolder(bar.getId());
            }, "Add New Folder", FunctionBar.STYLE_COURSE);
            options.add(addFolder);
        }

        if (bar.isTableFolder()) {
            var changeLevel = new FunctionBar((selector, self) -> {
                TagManagerMenu.spawnChangeLevelPopup(bar.getHeader(), bar.getPath());
            }, "Change Level", FunctionBar.STYLE_SPECIAL);
            options.add(changeLevel);
        }

        var remove = new FunctionBar((selector, self) -> {
            TagManagerMenu.spawnRemovePopup(bar.getHeader(), bar.getPath());
        }, "Remove", FunctionBar.STYLE_SPECIAL);
        options.add(remove);

        return options.toArray(new Bar[0]);
    }

	public static class TagManagerBar extends DirectoryBar {
        public TagManagerBar(MusicSelector selector) {
            super(selector, true);
            this.setSortable(false);
        }

        public String getTitle() { return "Tag Management"; }

        public Bar[] getChildren() {
            ArrayList<Bar> options = new ArrayList<>();

            var addTag = new FunctionBar((selector, self) -> {
                selector.main.getTagManager().createTag("New Tag");
            }, "Create Tag", FunctionBar.STYLE_SPECIAL);
            options.add(addTag);

            var addTable = new FunctionBar((selector, self) -> {
                selector.main.getTagManager().createTable("New Table");
            }, "Create Table", FunctionBar.STYLE_SEARCH);
            options.add(addTable);

            return options.toArray(new Bar[0]);
        }
    }
}


// behaviour
//  subfolders with no name display symbol+level; how should this work for nested subfolders?
//   default to empty name and display a path: parent-separator-level, ordered by level

// compatibility
//  importing tables
//   migrating from existing local data
//    determine when tags are entirely unitialized
//     (missing notes or favourites tag?)
//    then migrate everything once
//   loading from URL, store as backing source, refreshing
//    stash unused json fields
//   from file
//  replacing launcher UI
//  exporting to json
//  fill out metadata when tagging charts
//  store difficulty table metadata

// ui
//  notes ui?
//   transient "notify" window with current text displayed when hovering
//    when no local note, display current table note?
//   default built-in notes tag
//   interacting opens a imgui editing window with a dropdown to select which table to edit for
//  hidden tags
//  bulk tagging charts from search/current folder
//  imgui manager
//   allowing mixing folders+charts?
//   higher nesting
//   easier reordering
//  text / url tag entries

// songwheel
//  configurable access to quick favourite chart/song?
//  kick out from context menu after removing a tag
//  display current bar in a context
//  reload from url in context
//   also current level for a subfolder's?
//  adjust tag configuration for non-editable tags
//  backing out of tag bars with the same name places songwheel cursor on the wrong bar
//  when removing a tag from a song while inside that tag, it stays visible until reentering
//  lamps for top-level table bar?
//  for spawnRenamePopup, should we hold on to a maincontroller handle
//   to provide here instead of relying on imgui being initialized?
//  what to do with the favourite chart/song buttons

// auxiliary
//  filtering / lenses
//   arbitrary directory viewed through a folder, tag, or stratified by feature
//  complex search / dynamic collections
//   how to make contextual?
//  courses
//   access by top-level bar separate from tags
//  song tags?
//  making sense of random select
//  auto-updates

// rename / tag menu:
//  not fully blocking input
//  enter to confirm rename
//  capture rename field focus

// implementation
//  reload on profile switch
//  async sql writes
//  rework tagdb public api names
//  type-safe tag ids and hashes
//  yes, sql injection
//  make sure tagbar isnt missing any table or command bar functionality
//  clean up imports
//  name -> title
