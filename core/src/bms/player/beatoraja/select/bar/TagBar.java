package bms.player.beatoraja.select.bar;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;

import bms.tool.util.Pair;

import bms.player.beatoraja.tags.*;

import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.select.MusicSelector;

// represents both top-level tag bars as well as their subfolders,
// which share some logic; the variants can be distinguished with isNested()

public class TagBar extends DirectoryBar {
    private final String title;
    private final TagHeader header;
    private final TagPath path;

    private List<SongData> tagged = null;
    private String[] preferredHashes = null;

    // merge into a single constructor
    public TagBar(MusicSelector selector, TagHeader header, TagPath path) {
        super(selector, false);
        this.header = header;

        String name = header.getName();
        if (path != null) {
            name = path.getName();
            if (name.isEmpty()) { name = header.getLevelsymbol() + path.getLevel(); }
        }
        this.title = name;

        this.path = path;
        loadSongs();
    }

    public TagHeader getHeader() { return header; }
    public TagPath getPath() { return path; }
    public Pair<TagHeader, TagPath> getTag() { return Pair.of(header, path); }
    public int getId() { return path != null ? path.getId() : header.getId(); }

    public boolean isSimpleTag() { return 0 == header.getNested(); }
    public boolean isTable() { return path == null && 1 == header.getNested(); }
    public boolean isTableFolder() { return path != null && 1 == header.getNested(); }
    public boolean isCollection() { return false; }

    @Override
    public final String getTitle() {
        return title;
    }

    private void loadSongs() {
        TagManager tags = selector.main.getTagManager();

        this.tagged = tags.taggedCharts(getId());

        // a HashBar collects a SongData[] array from difficulty table data, then
        // converts it into an array of preferred hashes:
        preferredHashes = tagged.stream()
            .map(e -> e.getSha256().length() > 0 ? e.getSha256() : e.getMd5())
            .toArray(String[] ::new);
    }

    private SongData toSongData(String hash) {
        SongData song = new SongData();
        song.setSha256(hash);
        return song;
    }

    @Override
    public final Bar[] getChildren() {
        ArrayList<Bar> options = new ArrayList<>();
        // abbreviate into a "may have folders" predicate?
        if (isTable() || isCollection()) {
            TagManager tags = selector.main.getTagManager();
            List<TagPath> folders = tags.tagFolders(getId());
            for (TagPath subtag : folders) {
                options.add(new TagBar(this.selector, header, subtag));
            }
        }

        // then it uses those to recover the local song data from the database:
        SongData[] dbsongs = selector.getSongDatabase().getSongDatas(preferredHashes);
        for (SongBar bar : SongBar.toSongBarArray(dbsongs, tagged.toArray(new SongData[0]))) {
            options.add(bar);
        }
        return options.toArray(new Bar[0]);
    }

    @Override
    public void updateFolderStatus() {
        updateFolderStatus(selector.getSongDatabase().getSongDatas(preferredHashes));
    }
}
