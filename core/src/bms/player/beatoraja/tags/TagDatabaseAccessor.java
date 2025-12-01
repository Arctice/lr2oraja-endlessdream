package bms.player.beatoraja.tags;

// import java.util.List;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteConfig.SynchronousMode;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.SQLiteDatabaseAccessor;

import bms.tool.util.Pair;

public class TagDatabaseAccessor extends SQLiteDatabaseAccessor {
    private static final Logger logger = LoggerFactory.getLogger(TagDatabaseAccessor.class);

    private final String player;

    private final String playerpath;

    private SQLiteDataSource ds;

    private final QueryRunner qr;

    private int nextTagId;
    private int nextTagId() { return nextTagId++; }

    private int nextMetaId;
    private int nextMetaId() { return nextMetaId++; }

    Connection singleConnection;

    public TagDatabaseAccessor(Config config) throws SQLException {
        super(new Table("header",
                        new Column("id", "INTEGER", 1, 1),
                        new Column("name", "TEXT", 1, 0),
                        new Column("levelsymbol", "TEXT"),
						new Column("nested", "INTEGER"),
                        new Column("editable", "BOOL"),
                        new Column("sourceurl", "TEXT")
                        // new Column("songtags", "BOOL"),
                        ),
              new Table("tree",
                        new Column("id", "INTEGER", 1, 1),
                        new Column("parentid", "INTEGER", 1, 0),
                        new Column("level", "INTEGER"),
                        new Column("name", "TEXT")
                        ),

              new Table("sha256map",
                        new Column("hash", "TEXT", 1, 1),
                        new Column("tag", "INTEGER", 1, 1),
                        new Column("meta", "INTEGER", 1, 1)
                        ),
              new Table("md5map",
                        new Column("hash", "TEXT", 1, 1),
                        new Column("tag", "INTEGER", 1, 1),
                        new Column("meta", "INTEGER", 1, 1)
                        ),
              new Table("metadata",
                        new Column("id", "INTEGER", 1, 1),
                        new Column("title", "TEXT"),
                        new Column("artist", "TEXT"),
                        new Column("notes", "TEXT"),
                        new Column("url", "TEXT"),
                        new Column("appendurl", "TEXT")
                        ),

              new Table("unifiedmap",
                        new Column("tag", "INTEGER", 1, 1),
                        new Column("sha256", "TEXT", 0, 1),
                        new Column("md5", "TEXT", 0, 1),
                        new Column("title", "TEXT"),
                        new Column("artist", "TEXT"),
                        new Column("notes", "TEXT"),
                        new Column("url", "TEXT"),
                        new Column("appendurl", "TEXT")
                        )

              // new Table("songmap",
			  // 		   new Column("path", "TEXT", 1, 0)
              //           )
              );

        this.player = config.getPlayername();
        this.playerpath = config.getPlayerpath();

        try {
            Class.forName("org.sqlite.JDBC");
        }
        catch (ClassNotFoundException e) {
            logger.error("tag db error: {}", e.getLocalizedMessage());
        }
        String dbpath = playerpath + File.separatorChar + player + File.separatorChar + "tags.db";
        SQLiteConfig conf = new SQLiteConfig();
        conf.setJournalMode(SQLiteConfig.JournalMode.WAL);
		conf.setSynchronous(SynchronousMode.NORMAL);

        ds = new SQLiteDataSource(conf);
        ds.setUrl("jdbc:sqlite:" + dbpath);
        qr = new QueryRunner(ds);

        try {
            this.validate(qr);
        }
        catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            // these must be initialized for the database to work correctly
            // there's supposed to be some way to do this within
            // sql, but that'll require switching out SQLiteDatabaseAccessor
            List<TagPath> allNodes = qr.query("SELECT id FROM tree", pathHandler);
            this.nextTagId = 1 + allNodes.stream().mapToInt(path -> path.getId()).max().orElse(-1);

            // yes, this is very stupid right now
            List<HashEntry> sha256Entries = qr.query("SELECT meta FROM sha256map", hashHandler);
            List<HashEntry> md5Entries = qr.query("SELECT meta FROM md5map", hashHandler);
            int lastMD5id = allNodes.stream().mapToInt(path -> path.getId()).max().orElse(-1);
            int lastSHA256id = allNodes.stream().mapToInt(path -> path.getId()).max().orElse(-1);
            this.nextMetaId = Math.max(lastMD5id, lastSHA256id) + 1;

            singleConnection = qr.getDataSource().getConnection();
        }
        catch (SQLException e) {
            throw e;
        }
    }

    private final ResultSetHandler<List<TagHeader>> headerHandler =
        new BeanListHandler<TagHeader>(TagHeader.class);
    private final ResultSetHandler<TagHeader> singleHeaderHandler =
        new BeanHandler<TagHeader>(TagHeader.class);
    private final ResultSetHandler<List<TagPath>> pathHandler =
        new BeanListHandler<TagPath>(TagPath.class);
    private final ResultSetHandler<TagPath> singlePathHandler =
        new BeanHandler<TagPath>(TagPath.class);
    private final ResultSetHandler<List<HashEntry>> hashHandler =
        new BeanListHandler<HashEntry>(HashEntry.class);
    private final ResultSetHandler<List<HashPair>> hashPairsHandler =
        new BeanListHandler<HashPair>(HashPair.class);
    private final ResultSetHandler<UnifiedEntry> singleUnifiedHandler =
        new BeanHandler<UnifiedEntry>(UnifiedEntry.class);
    private final ScalarHandler<Integer> intHandler = new ScalarHandler<>();
    private final ResultSetHandler<TagMetadata> metadataHandler =
        new BeanHandler<TagMetadata>(TagMetadata.class);


    public int createTag(String name) {
        TagHeader header = new TagHeader(nextTagId(), name);
        return addTag(name, header);
    }

    public int createTable(String name) {
        TagHeader header = new TagHeader(nextTagId(), name);
		header.setNested(1);
        return addTag(name, header);
    }

    private int addTag(String name, TagHeader header) {
        try {
            this.insert(qr, singleConnection, "header", header);
            // inserting a tree root to simplify loading the tag tree
            TagPath root = new TagPath(header.getId(), -1, 0);
            this.insert(qr, singleConnection, "tree", root);
            return header.getId();
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public int addChild(int parent, int level) {
        TagPath child = new TagPath(nextTagId(), parent, level);
        try {
            this.insert(qr, singleConnection, "tree", child);
            return child.getId();
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public void renameTag(int id, String newName) {
        try {
            qr.update(singleConnection, "UPDATE header SET name = ? WHERE id = ?", newName, id);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void setTagSourceUrl(int id, String url) {
        try {
            qr.update(singleConnection, "UPDATE header SET sourceurl = ? WHERE id = ?", url, id);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void renamePath(int id, String newName) {
        try {
            qr.update(singleConnection, "UPDATE tree SET name = ? WHERE id = ?", newName, id);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void renameLevelSymbol(int id, String symbol) {
        try {
            qr.update(singleConnection, "UPDATE header SET levelsymbol = ? WHERE id = ?", symbol, id);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void changeLevel(int id, int level) {
        try {
            qr.update(singleConnection, "UPDATE tree SET level = ? WHERE id = ?", level, id);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public List<TagHeader> allTagHeaders() {
		try {
            List<TagHeader> tags =
                qr.query(singleConnection, "SELECT * FROM header ORDER BY id", headerHandler);
            return tags;
        } catch (Exception e) {
			logger.error("tag db error: {}", e.getMessage());
		}
		return new ArrayList<>();
	}

    public Optional<TagHeader> tagHeader(int id) {
        try {
            List<TagHeader> found =
                qr.query(singleConnection, "SELECT * FROM header WHERE id = '" + id + "'", headerHandler);
            if (!found.isEmpty()) return Optional.of(found.get(0));
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public List<TagPath> tagChildren(int id) {
        try {
            List<TagPath> children = qr.query(singleConnection,
                                              "SELECT * FROM tree WHERE parentid = '" + id + "' "
                                                  + "ORDER BY level",
                                              pathHandler);
            return children;
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public static class HashPair {
        private String sha256, md5;
        public HashPair() { sha256 = ""; md5 = ""; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        public String getMd5() { return md5; }
        public void setMd5(String md5) { this.md5 = md5; }
    }


    public List<HashPair> taggedCharts(int id) {
        try {
            // this was insanely slow:
            // List<HashPair> mapEntries =
            //     qr.query(singleConnection,
            //              "SELECT sha256map.hash AS sha256, md5map.hash AS md5 "
            //                  + "FROM sha256map FULL JOIN md5map "
            //                  + "ON sha256map.meta = md5map.meta "
            //                  + "WHERE sha256map.tag = ? OR md5map.tag = ? ",
            //              hashPairsHandler, id, id);

            List<HashEntry> sha256Entries =
                qr.query(singleConnection, "SELECT * FROM sha256map WHERE tag = ?", hashHandler, id);
            List<HashEntry> md5Entries =
                qr.query(singleConnection, "SELECT * FROM md5map WHERE tag = ?", hashHandler, id);

            var chartset = new HashMap<Integer, HashPair>();
            for (var entry : sha256Entries) {
                var v = new HashPair();
                v.setSha256(entry.getHash());
                chartset.put(entry.getMeta(), v);
            }
            for (var entry : md5Entries) {
                int meta = entry.getMeta();
                String md5 = entry.getHash();
                if(!chartset.containsKey(meta)) { chartset.put(meta, new HashPair()); }
                chartset.get(meta).setMd5(md5);
            }

            List<HashPair> results = new ArrayList<HashPair>(chartset.values());

            List<HashPair> altResults = qr.query(singleConnection,
                     "SELECT sha256, md5 FROM unifiedmap WHERE tag = ?",
                     hashPairsHandler, id);

            assert results.size() == altResults.size();

            return results;
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    public void tagChartBatch(int tag, String[] sha256, String[] md5, TagMetadata[] meta) {
        // complain if lengths arent the same
        int length = sha256.length;
        try {
            Object[][] varbatch = new Object[length][8];
            for(int i = 0; i < length; ++i) {
                varbatch[i][0] = tag;
                varbatch[i][1] = sha256[i];
                varbatch[i][2] = md5[i];
                varbatch[i][3] = meta[i].getTitle();
                varbatch[i][4] = meta[i].getArtist();
                varbatch[i][5] = meta[i].getNotes();
                varbatch[i][6] = meta[i].getUrl();
                varbatch[i][7] = meta[i].getAppendurl();
            }
            qr.batch(singleConnection,
                     "INSERT OR REPLACE INTO unifiedmap (tag, sha256, md5, title, artist, notes, url, appendurl) "
                     + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                     varbatch);

            Object[][] shabatch = new Object[length][3];
            Object[][] md5batch = new Object[length][3];
            Object[][] metabatch = new Object[length][6];
            for(int i = 0; i < length; ++i) {
                int entry = nextMetaId();
                metabatch[i][0] = entry;
                metabatch[i][1] = meta[i].getTitle();
                metabatch[i][2] = meta[i].getArtist();
                metabatch[i][3] = meta[i].getNotes();
                metabatch[i][4] = meta[i].getUrl();
                metabatch[i][5] = meta[i].getAppendurl();
                shabatch[i][0] = sha256[i];
                shabatch[i][1] = tag;
                shabatch[i][2] = entry;
                md5batch[i][0] = md5[i];
                md5batch[i][1] = tag;
                md5batch[i][2] = entry;
            }

            shabatch = Arrays.stream(shabatch).filter(row -> !((String)row[0]).isEmpty()).toArray(Object[][]::new);
            md5batch = Arrays.stream(md5batch).filter(row -> !((String)row[0]).isEmpty()).toArray(Object[][]::new);

            qr.batch(singleConnection,
                     "INSERT OR REPLACE INTO sha256map (hash, tag, meta) "
                     + "VALUES (?, ?, ?)",
                     shabatch);
            qr.batch(singleConnection,
                     "INSERT OR REPLACE INTO md5map (hash, tag, meta) "
                     + "VALUES (?, ?, ?)",
                     md5batch);
            qr.batch(singleConnection,
                     "INSERT OR REPLACE INTO metadata (id, title, artist, notes, url, appendurl) "
                     + "VALUES (?, ?, ?, ?, ?, ?)",
                     metabatch);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void tagChart(int tagId, String sha256, String md5) {
        // verify tagid exists?
        // notes
        if(sha256 == null) { sha256 = ""; }
        if(md5 == null) { md5 = ""; }
        if(sha256.equals("") && md5.equals("")) { return; }
        try  {
            UnifiedEntry uniMap = new UnifiedEntry();
            uniMap.setTag(tagId);
            uniMap.setSha256(sha256);
            uniMap.setMd5(md5);
            this.insert(qr, singleConnection, "unifiedmap", uniMap);

            int meta = nextMetaId();
            HashEntry hashMap = new HashEntry();
            hashMap.setTag(tagId);
            hashMap.setMeta(meta);
            if(!sha256.equals("")) {
                hashMap.setHash(sha256);
                this.insert(qr, singleConnection, "sha256map", hashMap);
            }
            if(!md5.equals("")) {
                hashMap.setHash(md5);
                this.insert(qr, singleConnection, "md5map", hashMap);
            }

            var data = new MetadataEntry();
            data.setId(meta);
            this.insert(qr, singleConnection, "metadata", data);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void untagChart(int tag, String sha256, String md5) {
        if(sha256 == null) { sha256 = ""; }
        if(md5 == null) { md5 = ""; }
        try {
            if(!sha256.equals("")) {
                int meta = qr.query(singleConnection, "SELECT meta FROM sha256map WHERE tag = ? AND hash = ?", intHandler, tag, sha256);
                qr.update(singleConnection, "DELETE FROM sha256map WHERE tag = ? AND hash = ?", tag, sha256);
                qr.update(singleConnection, "DELETE FROM metadata WHERE id = ?", meta);
            }
            if(!md5.equals("")) {
                int meta = qr.query(singleConnection, "SELECT meta FROM md5map WHERE tag = ? AND hash = ?", intHandler, tag, md5);
                qr.update(singleConnection, "DELETE FROM md5map WHERE tag = ? AND hash = ?", tag, md5);
                qr.update(singleConnection, "DELETE FROM metadata WHERE id = ?", meta);
            }

            if(!sha256.equals("")) {
                qr.update(singleConnection, "DELETE FROM unifiedmap WHERE tag = ? AND sha256 = ?", tag, sha256);
            }
            if(!md5.equals("")) {
                qr.update(singleConnection, "DELETE FROM unifiedmap WHERE tag = ? AND md5 = ?", tag, md5);
            }
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void setChartTagMeta(int tag, String sha256, String md5, String key, String value) {
        if(sha256 == null) { sha256 = ""; }
        if(md5 == null) { md5 = ""; }
        try {
            // insert missing pair hash
            Integer meta = qr.query(singleConnection, "SELECT meta FROM md5map WHERE tag = ? AND hash = ?", intHandler, tag, md5);
            if (meta == null) {
                meta = qr.query(singleConnection, "SELECT meta FROM sha256map WHERE tag = ? AND hash = ?", intHandler, tag, sha256);
            }
            if (meta != null) {
                qr.update(singleConnection, "UPDATE metadata SET " + key + " = ? WHERE id = ?", value, meta);
            }

            qr.update(singleConnection,
                     "UPDATE unifiedmap SET " + key + " = ? "
                     + "WHERE tag = ? AND "
                     + "((sha256 = ? AND sha256 <> '' ) "
                     + " OR (md5 = ? AND md5 <> '' ))",
                     value, tag, sha256, md5);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void setChartTagMeta(int tag, String sha256, String md5, TagMetadata metadata) {
        if(sha256 == null) { sha256 = ""; }
        if(md5 == null) { md5 = ""; }
        try {
            // insert missing pair hash
            Integer meta = qr.query(singleConnection, "SELECT meta FROM md5map WHERE tag = ? AND hash = ?", intHandler, tag, md5);
            if (meta == null) {
                meta = qr.query(singleConnection, "SELECT meta FROM sha256map WHERE tag = ? AND hash = ?", intHandler, tag, sha256);
            }
            if (meta != null) {
                var entry = new MetadataEntry();
                entry.setId(meta);
                entry.setTitle(metadata.getTitle());
                entry.setNotes(metadata.getNotes());
                entry.setUrl(metadata.getUrl());
                this.insert(qr, singleConnection, "metadata", entry);
            }

            UnifiedEntry entry =
                qr.query(singleConnection,
                         "SELECT * FROM unifiedmap "
                         + "WHERE tag = ? AND "
                         + "((sha256 = ? AND sha256 <> '' ) "
                         + "OR (md5 = ? AND md5 <> '' ))",
                         singleUnifiedHandler, tag, sha256, md5);
            if(entry != null) {
                if(entry.sha256.isEmpty()) { entry.setSha256(sha256); }
                if(entry.md5.isEmpty()) { entry.setMd5(md5); }
                entry.setTitle(metadata.getTitle());
                entry.setNotes(metadata.getNotes());
                entry.setUrl(metadata.getUrl());
                this.insert(qr, singleConnection, "unifiedmap", entry);
            }
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public TagMetadata getChartTagMeta(int tag, String sha256, String md5) {
        if(sha256 == null) { sha256 = ""; }
        if(md5 == null) { md5 = ""; }
        try {
            TagMetadata result = null;
            Integer meta = qr.query(singleConnection, "SELECT meta FROM md5map WHERE tag = ? AND hash = ?", intHandler, tag, md5);
            if (meta == null) {
                meta = qr.query(singleConnection, "SELECT meta FROM sha256map WHERE tag = ? AND hash = ?", intHandler, tag, sha256);
            }
            if (meta != null) {
                result = qr.query(singleConnection, "SELECT *  FROM metadata WHERE id = ?", metadataHandler, meta);
            }

            TagMetadata altResult =
                qr.query(singleConnection,
                         "SELECT * FROM unifiedmap "
                         + "WHERE tag = ? AND "
                         + "((sha256 = ? AND sha256 <> '' ) "
                         + "OR md5 = ? AND md5 <> '' )",
                         metadataHandler, tag, sha256, md5);

            assert (result != null && altResult != null) || (result == null && altResult == null);
            if(result != null) {
                assert (altResult.getTitle() == null && result.getTitle() == null)
                    || (altResult.getTitle().equals(result.getTitle()));
                assert (altResult.getNotes() == null && result.getNotes() == null)
                    || (altResult.getNotes().equals(result.getNotes()));
            }

            return result;
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void removePath(int id) {
        // make this a transaction
        List<TagPath> children = tagChildren(id);
        for (TagPath child : children) removePath(child.getId());
        try {
            List<HashEntry> sha256Entries =
                qr.query(singleConnection, "SELECT * FROM sha256map WHERE tag = ?", hashHandler, id);
            List<HashEntry> md5Entries =
                qr.query(singleConnection, "SELECT * FROM md5map WHERE tag = ?", hashHandler, id);
            for(var map : sha256Entries)
                { qr.update(singleConnection, "DELETE FROM metadata WHERE id = ?", map.getMeta()); }
            for(var map : md5Entries)
                { qr.update(singleConnection, "DELETE FROM metadata WHERE id = ?", map.getMeta()); }

            qr.update(singleConnection, "DELETE FROM tree WHERE id = ?", id);
            qr.update(singleConnection, "DELETE FROM sha256map WHERE tag = ?", id);
            qr.update(singleConnection, "DELETE FROM md5map WHERE tag = ?", id);
            qr.update(singleConnection, "DELETE FROM unifiedmap WHERE tag = ?", id);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void removeHeader(int id) {
        // make this a transaction
        removePath(id);
        try {
            qr.update(singleConnection, "DELETE FROM header WHERE id = ?", id);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private Pair<TagHeader, List<TagPath>> fullTagPath(TagPath tag) {
		// make this use a single connection
        try {
            List<TagPath> path = new ArrayList<TagPath>();
            TagPath next = tag;
            while (next.hasParent()) {
                path.add(next);
                next = qr.query(singleConnection,
                                "SELECT * FROM tree "
                                    + "WHERE id = '" + next.getParentid() + "' ",
                                singlePathHandler);
            }
            java.util.Collections.reverse(path);
            TagHeader header = qr.query(singleConnection,
                                        "SELECT * FROM header "
                                            + "WHERE id = '" + next.getId() + "'",
                                        singleHeaderHandler);
            return Pair.of(header, path);
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
            return null;
        }
    }

    public List<Pair<TagHeader, List<TagPath>>> chartTags(String sha256, String md5) {
        try {
            List<TagPath> results =
                qr.query(singleConnection,
                         "SELECT tree.* FROM tree "
                         + "INNER JOIN unifiedmap ON tree.id = unifiedmap.tag "
                         + "WHERE ((unifiedmap.sha256 = ? AND unifiedmap.sha256 <> '' ) "
                         + "OR (unifiedmap.md5 = ? AND unifiedmap.md5 <> ''))",
                         pathHandler, sha256, md5);

            var tagset = new HashSet<Integer>();
            List<HashEntry> sha256Entries =
                qr.query(singleConnection, "SELECT * FROM sha256map WHERE hash = ?", hashHandler, sha256);
            List<HashEntry> md5Entries =
                qr.query(singleConnection, "SELECT * FROM md5map WHERE hash = ?", hashHandler, md5);
            for(var entry : sha256Entries) { tagset.add(entry.getTag()); }
            for(var entry : md5Entries) { tagset.add(entry.getTag()); }
            List<TagPath> altResults = new ArrayList<>();
            for(var id : tagset) {
                altResults.add(qr.query(singleConnection, "SELECT * FROM tree WHERE id = ?", singlePathHandler, id));
            }

            assert results.size() == altResults.size();
            return results.stream().map(tag -> fullTagPath(tag)).toList();
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // split these into a separate class for sha256 and md5 if the overlap causes issues
    public static class HashEntry {
        private String hash;
        private int tag;
        private int meta;

        public HashEntry() {}

        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }

        public int getTag() { return tag; }
        public void setTag(int tag) { this.tag = tag; }

        public int getMeta() { return meta; }
        public void setMeta(int meta) { this.meta = meta; }
    }

    public static class MetadataEntry {
        private int id;
        private String title;
        private String artist;
        private String notes;
        private String url;
        private String appendurl;

        public MetadataEntry() {}

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getAppendurl() { return appendurl; }
        public void setAppendurl(String appendurl) { this.appendurl = appendurl; }
    }

    public static class UnifiedEntry {
        private int tag;
        private String sha256;
        private String md5;
        private String title;
        private String artist;
        private String notes;
        private String url;
        private String appendurl;

        public UnifiedEntry() {}

        public int getTag() { return tag; }
        public void setTag(int tag) { this.tag = tag; }

        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }

        public String getMd5() { return md5; }
        public void setMd5(String md5) { this.md5 = md5; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getAppendurl() { return appendurl; }
        public void setAppendurl(String appendurl) { this.appendurl = appendurl; }
    }
}
