package bms.player.beatoraja.tags;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import bms.player.beatoraja.modmenu.TagManagerMenu;

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
        catch (PlayerConfigException e) {
            logger.error("Failed to access tag database: {}", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public int createTag(String name) { return tagdb.createTag(name); }

    public int createTable(String name) { return tagdb.createTable(name); }

    public int addSubfolder(int id) {
		// should be smarter and use either the highest unused or first gap
		int level = tagFolders(id).size();
        return tagdb.addChild(id, level);
	}

    public void renameTag(int id, String newName) { tagdb.renameTag(id, newName); }

    public void tagChart(int tagId, String sha256) { tagdb.tagChart(tagId, sha256); }

    public List<TagHeader> allTagHeaders() { return tagdb.allTagHeaders(); }

    public Optional<TagHeader> tagHeader(int id) { return tagdb.tagHeader(id); }

    public List<TagHierarchy> tagFolders(int id) { return tagdb.tagChildren(id); }

    public List<TagHeader> chartTags(String sha256) { return tagdb.chartTags(sha256); }

    public List<String> taggedCharts(int tagId) { return tagdb.taggedCharts(tagId); }

    public List<TagHeader> chartTagsComplement(String sha256) {
        List<TagHeader> allTags = allTagHeaders();
        List<TagHeader> marked = chartTags(sha256);
        Map<Integer, TagHeader> missing = new HashMap<>();
        for (TagHeader tag : allTags) { missing.put(tag.getId(), tag); }
        for (TagHeader tag : marked) { missing.remove(tag.getId()); }
        return new ArrayList<TagHeader>(missing.values());
    }

    public Bar[] createTagBars(MusicSelector select) {
        ArrayList<Bar> options = new ArrayList<>();

        {
            var addTag = new FunctionBar((selector, self) -> {
                selector.getBarManager().updateBar(new TagManagerBar(selector));
                selector.play(FOLDER_OPEN);
            }, "Manage Collection", FunctionBar.STYLE_SPECIAL);
            options.add(addTag);
        }

        List<TagHeader> tags = tagdb.allTagHeaders();
        for (TagHeader tag : tags) { options.add(makeTagFolderBar(select, tag)); }

        return options.toArray(new Bar[0]);
    }

    private Bar makeTagFolderBar(MusicSelector select, TagHeader tag){
        var folder = new TagBar(select, tag);
		return folder;
    }

    public Bar[] tagContext(MusicSelector select, TagBar bar) {
        ArrayList<Bar> options = new ArrayList<>();

        var rename = new FunctionBar((selector, self) -> {
            TagManagerMenu.spawnRenamePopup(bar.getTag());
        }, "Rename", FunctionBar.STYLE_SPECIAL);
        options.add(rename);

        if (0 < bar.getTag().getNested()) {
            var addFolder = new FunctionBar((selector, self) -> {
                addSubfolder(bar.getTag().getId());
            }, "Add New Folder", FunctionBar.STYLE_COURSE);
            options.add(addFolder);
        }

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


// tags in manage tags unsorted?
// imgui manager
//  allowing mixing folders+charts?
//  higher nesting
// fill out metadata when tagging charts
// account for md5 hashes
// store full difficulty table metadata
// async sql writes
// backing out of tag bars places songwheel cursor on the wrong bar
// rename doesnt block kb inputs
// adjust tag configuration for non-editable tags
// properly initialize tagcount with highest id from db
// for spawnRenamePopup, should we hold on to a maincontroller handle
//  to provide here instead of relying on imgui being initialized?
// reload on profile switch
// yes, sql injection
// notes support
// update bar after context menu change
// cleanup imports
// make sure tagbar isnt missing any table or command bar functionality
// rework tagdb public api names
// bulk tagging charts from search/current folder

// rename / tag menu:
// merge with song manager menu
// not fully capturing keyboard input (esc?)
// enter to confirm rename
// capture rename field focus
