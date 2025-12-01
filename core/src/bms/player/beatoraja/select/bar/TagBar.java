package bms.player.beatoraja.select.bar;

import java.util.List;
import java.util.ArrayList;

import bms.player.beatoraja.tags.*;

import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.select.MusicSelector;

import java.util.stream.Stream;

// represents both top-level tag bars as well as their subfolders,
// which share some logic; the variants can be distinguished with isNested()

public class TagBar extends DirectoryBar {
    private final String title;
    private final TagHeader header;
    private final TagHierarchy subtag;

    private String[] songHashes = null;
    private SongData[] songs = null;

    public TagBar(MusicSelector selector, TagHeader header, TagHierarchy subtag) {
        super(selector, false);
        this.header = header;
        this.title = String.valueOf(subtag.getLevel());
        this.subtag = subtag;
        loadSongs();
    }

    public TagBar(MusicSelector selector, TagHeader header) {
        super(selector, false);
        this.header = header;
        this.title = header.getName();
        this.subtag = null;
        loadSongs();
    }

    @Override
    public final String getTitle() {
        return title;
    }

    public TagHeader getTag() { return header; }

    public boolean isNested() { return subtag == null && 0 < header.getNested(); }

    private void loadSongs() {
        TagManager tags = selector.main.getTagManager();
        int id = subtag != null ? subtag.getTagid() : header.getId();
        this.songHashes = tags.taggedCharts(id).toArray(new String[0]);
        this.songs = Stream.of(songHashes).map(hash -> toSongData(hash)).toArray(SongData[] ::new);
    }

    private SongData toSongData(String hash) {
        SongData song = new SongData();
        song.setSha256(hash);
        return song;
    }

    @Override
    public final Bar[] getChildren() {
        ArrayList<Bar> options = new ArrayList<>();
        if (isNested()) {
            TagManager tags = selector.main.getTagManager();
            List<TagHierarchy> folders = tags.tagFolders(header.getId());
            for (TagHierarchy subtag : folders) {
                options.add(new TagBar(this.selector, header, subtag));
            }
        }

        // a HashBar collects a SongData[] array from difficulty table data, then
        // converts it into an array of preferred hashes:
        // songHashes = Stream.of(songs)
        //                  .map(e -> e.getSha256().length() > 0 ? e.getSha256() : e.getMd5())
        //                  .toArray(String[] ::new);
        // then it uses those to recover the local song data from the database:
        // selector.getSongDatabase().getSongDatas(songHashes)
        SongData[] dbsongs = selector.getSongDatabase().getSongDatas(songHashes);
        for (SongBar bar : SongBar.toSongBarArray(dbsongs, this.songs)) { options.add(bar); }
        return options.toArray(new Bar[0]);
    }

    @Override
    public void updateFolderStatus() {
        if (isNested()) { return; }
        updateFolderStatus(selector.getSongDatabase().getSongDatas(songHashes));
    }
}
