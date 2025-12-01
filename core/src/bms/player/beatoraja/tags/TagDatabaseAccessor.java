package bms.player.beatoraja.tags;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import java.sql.*;
import java.sql.Connection;
import java.sql.SQLException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.SQLiteDatabaseAccessor;

// import java.util.*;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import org.apache.commons.dbutils.QueryRunner;
// import org.apache.commons.dbutils.ResultSetHandler;
// import org.apache.commons.dbutils.handlers.BeanListHandler;

// import bms.player.beatoraja.song.SongData;

// import org.sqlite.SQLiteConfig.SynchronousMode;

public class TagDatabaseAccessor extends SQLiteDatabaseAccessor {
    private static final Logger logger = LoggerFactory.getLogger(TagDatabaseAccessor.class);

    private final String player;

    private final String playerpath;

    private SQLiteDataSource ds;

    private final QueryRunner qr;

    public TagDatabaseAccessor(Config config) {
        super(new Table("header",
                        new Column("id", "TEXT", 1, 1),
                        new Column("name", "TEXT", 1, 0),
                        new Column("levelsymbol", "TEXT"),
                        new Column("editable", "BOOL"),
                        new Column("songtags", "BOOL"),
                        new Column("backingurl", "TEXT")
                        ),
              new Table("hierarchy",
                        new Column("tagid", "TEXT", 1, 1),
                        new Column("parentid", "TEXT", 1, 1),
                        new Column("name", "TEXT"),
                        new Column("level", "INTEGER")
                        ),
              new Table("chartmap",
                        new Column("sha256", "TEXT", 1, 1),
                        new Column("tagid", "TEXT", 1, 1),
                        new Column("notes", "TEXT")
                        ),
              new Table("songmap",
                        new Column("tagid", "TEXT", 1, 1),
						new Column("path", "TEXT", 1, 0),
                        new Column("notes", "TEXT")
                        )
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
    private final ResultSetHandler<List<ChartEntry>> chartEntryHandler =
        new BeanListHandler<ChartEntry>(ChartEntry.class);

    private int tagCount = 0;
    private int nextTagId() { return ++tagCount; }

    protected void newTag(String name) {
		try (Connection conn = qr.getDataSource().getConnection()) {
			conn.setAutoCommit(false);
            TagHeader header = new TagHeader(nextTagId(), name);
            this.insert(qr, conn, "header", header);
            conn.commit();
		} catch (SQLException e) {
			logger.error("tag db error: {}", e.getMessage());
			e.printStackTrace();
		}
    }

    public void renameTag(String id, String newName) {
        try (Connection conn = qr.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            qr.update(conn, "UPDATE header SET name = '" + newName + "' WHERE id = '" + id + "'");
            conn.commit();
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public List<TagHeader> allTagHeaders() {
		try {
            return qr.query("SELECT * FROM header", headerHandler);
        } catch (Exception e) {
			logger.error("tag db error: {}", e.getMessage());
		}
		return new ArrayList<>();
	}

    public List<String> taggedCharts(String id) {
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

    public void tagChart(String tagId, String sha256) {
        // verify tagid exists?
        // notes
        try (Connection conn = qr.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            ChartEntry entry = new ChartEntry();
            entry.setSha256(sha256);
            entry.setTagid(tagId);
            this.insert(qr, conn, "chartmap", entry);
            conn.commit();
        }
        catch (SQLException e) {
            logger.error("tag db error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public List<TagHeader> chartTags(String sha256) {
        try {
            return qr.query(
                "SELECT * FROM header INNER JOIN chartmap ON header.id = chartmap.tagid "
                    + "WHERE chartmap.sha256 = '" + sha256 + "'",
                headerHandler);
        }
        catch (Exception e) {
            logger.error("tag db error: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    public static class ChartEntry {
        private String sha256;
        private String tagid;
        private String notes = "";

        public ChartEntry() {}

        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }

        public String getTagid() { return tagid; }
        public void setTagid(String tagid) { this.tagid = tagid; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}
