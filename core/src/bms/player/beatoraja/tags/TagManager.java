package bms.player.beatoraja.tags;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.select.bar.Bar;
import bms.player.beatoraja.select.bar.FunctionBar;
import bms.player.beatoraja.select.bar.TagBar;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.song.SongData;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.exceptions.PlayerConfigException;

import bms.player.beatoraja.modmenu.TagManagerMenu;

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
            logger.error("Failed to access score database: {}", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public void renameTag(String id, String newName) { tagdb.renameTag(id, newName); }

    public void tagChart(String tagId, String sha256) { tagdb.tagChart(tagId, sha256); }

    public List<TagHeader> allTagHeaders() { return tagdb.allTagHeaders(); }

    public List<TagHeader> chartTags(String sha256) { return tagdb.chartTags(sha256); }

    public List<String> taggedCharts(String tagId) { return tagdb.taggedCharts(tagId); }

    public List<TagHeader> chartTagsComplement(String sha256) {
        List<TagHeader> allTags = allTagHeaders();
        List<TagHeader> marked = chartTags(sha256);
        Map<String, TagHeader> missing = new HashMap<>();
        for (TagHeader tag : allTags) { missing.put(tag.getId(), tag); }
        for (TagHeader tag : marked) { missing.remove(tag.getId()); }
        return new ArrayList<TagHeader>(missing.values());
    }

    public Bar[] createMusicSelectBars(MusicSelector select) {
        ArrayList<Bar> options = new ArrayList<>();
        var addTag = new FunctionBar((selector, self) -> {
            tagdb.newTag("New Tag");
        }, "add tag", FunctionBar.STYLE_SEARCH);
        options.add(addTag);

        List<TagHeader> tags = tagdb.allTagHeaders();
        for (TagHeader tag : tags) { options.add(makeTagFolderBar(select, tag)); }

        for (TagHeader tag : tags) {
            var bar = new FunctionBar((selector, self) -> {
                TagManagerMenu.spawnRenamePopup(tag);
            }, "Rename " + tag.getName(), FunctionBar.STYLE_SPECIAL);
            options.add(bar);
        }

        return options.toArray(new Bar[0]);
    }

    private Bar makeTagFolderBar(MusicSelector select, TagHeader tag){
        List<String> hashes = taggedCharts(tag.getId());
        SongData[] songs = main.getSongDatabase().getSongDatas(hashes.toArray(new String[0]));
        var folder = new TagBar(select, tag.getName(), songs);
		return folder;
    }

}


// for spawnRenamePopup, should we hold on to a maincontroller handle
//  to provide here instead of relying on imgui being initialized?
// reload on profile switch
// yes, sql injection
// notes support
// update bar after context menu change
// cleanup imports

// rename / tag menu:
// merge with song manager menu
// not fully capturing keyboard input (esc?)
// enter to confirm rename
// capture rename field focus
