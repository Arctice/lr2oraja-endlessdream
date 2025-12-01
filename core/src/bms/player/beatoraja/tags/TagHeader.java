package bms.player.beatoraja.tags;

public class TagHeader {
    private String id;
    private String name;
    private String levelsymbol = "";
    private boolean editable = true;
    private boolean songtags = false;
    private String backingurl = "";

    public TagHeader() {}

    public TagHeader(int id, String name) {
        this.name = name;
        this.id = String.valueOf(id);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLevelsymbol() { return levelsymbol; }
    public void setLevelsymbol(String levelsymbol) { this.levelsymbol = levelsymbol; }

    public boolean getEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }

    public boolean getSongtags() { return songtags; }
    public void setSongtags(boolean songtags) { this.songtags = songtags; }

    public String getBackingurl() { return backingurl; }
    public void setBackingurl(String backingurl) { this.backingurl = backingurl; }
}
