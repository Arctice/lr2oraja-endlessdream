package bms.player.beatoraja.tags;

public class TagHeader {
    private int id;
    private String name;
    private String levelsymbol = "";
    private int nested = 0;
    private boolean editable = true;
    private String sourceurl = "";
    // private boolean songtags = false;

    public TagHeader() {}

    public TagHeader(int id, String name) {
        this.name = name;
        this.id = id;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLevelsymbol() { return levelsymbol; }
    public void setLevelsymbol(String levelsymbol) { this.levelsymbol = levelsymbol; }

    public int getNested() { return nested; }
    public void setNested(int nesting) { this.nested = nesting; }

    public boolean getEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }

    public String getSourceurl() { return sourceurl; }
    public void setSourceurl(String sourceurl) { this.sourceurl = sourceurl; }

    // public boolean getSongtags() { return songtags; }
    // public void setSongtags(boolean songtags) { this.songtags = songtags; }
}
