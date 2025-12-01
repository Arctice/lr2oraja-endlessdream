package bms.player.beatoraja.tags;

import java.util.List;
import java.util.ArrayList;
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

import bms.player.beatoraja.Config;
import bms.player.beatoraja.SQLiteDatabaseAccessor;

// import javafx.util.Pair;

public class TagDatabaseAccessor extends SQLiteDatabaseAccessor {
    private static final Logger logger = LoggerFactory.getLogger(TagDatabaseAccessor.class);

    private final String player;

    private final String playerpath;

    private SQLiteDataSource ds;

    private final QueryRunner qr;

    public TagDatabaseAccessor(Config config) {
        super(new Table("header",
                        new Column("id", "INTEGER", 1, 1),
                        new Column("name", "TEXT", 1, 0),
                        new Column("levelsymbol", "TEXT"),
						new Column("nested", "INTEGER"),
                        new Column("editable", "BOOL"),
                        new Column("backingurl", "TEXT")
                        // new Column("songtags", "BOOL"),
                        ),
              new Table("hierarchy",
                        new Column("tagid", "INTEGER", 1, 1),
                        new Column("parentid", "INTEGER", 1, 0),
                        new Column("level", "INTEGER"),
                        new Column("name", "TEXT")
                        ),
              new Table("chartmap",
                        new Column("sha256", "TEXT", 1, 1),
                        new Column("tagid", "INTEGER", 1, 1),
                        new Column("notes", "TEXT")
                        )
              // new Table("songmap",
              //           new Column("tagid", "INTEGER", 1, 1),
			  // 			new Column("path", "TEXT", 1, 0),
              //           new Column("notes", "TEXT")
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
    }

    private final ResultSetHandler<List<TagHeader>> headerHandler =
        new BeanListHandler<TagHeader>(TagHeader.class);
    private final ResultSetHandler<List<TagHierarchy>> hierarchyHandler =
        new BeanListHandler<TagHierarchy>(TagHierarchy.class);
    private final ResultSetHandler<List<ChartEntry>> chartEntryHandler =
        new BeanListHandler<ChartEntry>(ChartEntry.class);

    private int tagCount = 0;
    private int nextTagId() { return ++tagCount; }

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
        try (Connection conn = qr.getDataSource().getConnection()) {
            this.insert(qr, conn, "header", header);
            // inserting a hierarchy helper to simplify loading the tag tree
            // not sure how to join chartmap/hierarchy with missing rows
            TagHierarchy hierarchy = new TagHierarchy(header.getId(), -1, 0);
            this.insert(qr, conn, "hierarchy", hierarchy);
            return header.getId();
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public int addChild(int parent, int level) {
        TagHierarchy child = new TagHierarchy(nextTagId(), parent, level);
        try (Connection conn = qr.getDataSource().getConnection()) {
            this.insert(qr, conn, "hierarchy", child);
            return child.getTagid();
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public void renameTag(int id, String newName) {
        try (Connection conn = qr.getDataSource().getConnection()) {
            qr.update(conn, "UPDATE header SET name = '" + newName + "' WHERE id = '" + id + "'");
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public List<TagHeader> allTagHeaders() {
		try {
            return qr.query("SELECT * FROM header ORDER BY id", headerHandler);
        } catch (Exception e) {
			logger.error("tag db error: {}", e.getMessage());
		}
		return new ArrayList<>();
	}

    public Optional<TagHeader> tagHeader(int id) {
        try {
            List<TagHeader> found =
                qr.query("SELECT * FROM header WHERE id = '" + id + "'", headerHandler);
            if (!found.isEmpty()) return Optional.of(found.get(0));
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public List<TagHierarchy> tagChildren(int id) {
        try {
            return qr.query("SELECT * FROM hierarchy WHERE parentid = '" + id + "' "
                                + "ORDER BY tagid",
                            hierarchyHandler);
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> taggedCharts(int id) {
        try {
            List<ChartEntry> entries =
                qr.query("SELECT * FROM chartmap WHERE tagid = '" + id + "'", chartEntryHandler);
            List<String> hashes = new ArrayList<>();
            for (ChartEntry entry : entries) { hashes.add(entry.getSha256()); }
            return hashes;
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    public void tagChart(int tagId, String sha256) {
        // verify tagid exists?
        // notes
        try (Connection conn = qr.getDataSource().getConnection()) {
            ChartEntry entry = new ChartEntry();
            entry.setSha256(sha256);
            entry.setTagid(tagId);
            this.insert(qr, conn, "chartmap", entry);
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public List<TagHeader> chartTags(String sha256) {
        try {
            // return qr.query(
            //     "SELECT * FROM header INNER JOIN chartmap ON header.id = chartmap.tagid "
            //         + "WHERE chartmap.sha256 = '" + sha256 + "'",
            //     headerHandler);
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    public static class ChartEntry {
        private String sha256;
        private int tagid;
        private String notes = "";

        public ChartEntry() {}

        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }

        public int getTagid() { return tagid; }
        public void setTagid(int tagid) { this.tagid = tagid; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}
