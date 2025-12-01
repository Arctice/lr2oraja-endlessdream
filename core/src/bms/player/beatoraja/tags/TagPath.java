package bms.player.beatoraja.tags;

public class TagPath {
    private int id;
    private int parentid;
    private int level;
    private String name = "";

    public TagPath() {}

    public TagPath(int id, int parentid, int level) {
        this.id = id;
        this.parentid = parentid;
        this.level = level;
        this.name = name;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getParentid() { return parentid; }
    public void setParentid(int parent) { this.parentid = parent; }
    public boolean hasParent() { return parentid != -1; }

    public int getLevel() { return level; }
    public void setLevel(int lvl) { this.level = lvl; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
