package bms.player.beatoraja.tags;

public class TagHierarchy {
    private int tagid;
    private int parentid;
    private int level;
    private String name = "";

    public TagHierarchy() {}

    public TagHierarchy(int id, int parentid, int level) {
        this.tagid = id;
        this.parentid = parentid;
        this.level = level;
        this.name = name;
    }

    public int getTagid() { return tagid; }
    public void setTagid(int id) { this.tagid = id; }

    public int getParentid() { return parentid; }
    public void setParentid(int parent) { this.parentid = parent; }

    public int getLevel() { return level; }
    public void setLevel(int lvl) { this.level = lvl; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
