package bms.player.beatoraja.tags;

public class TagMetadata {
    private String title;
    private String artist;
    private String notes;
    private String url;
    private String appendurl;

    public TagMetadata() {}

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
