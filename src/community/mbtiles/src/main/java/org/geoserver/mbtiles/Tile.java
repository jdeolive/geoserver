package org.geoserver.mbtiles;

public class Tile {

    long zoom, column, row;
    byte[] data;

    public Tile(long zoom, long column, long row, byte[] data) {
        this.zoom = zoom;
        this.column = column;
        this.row = row;
        this.data = data;
    }

    public long getZoom() {
        return zoom;
    }

    public long getColumn() {
        return column;
    }
    
    public long getRow() {
        return row;
    }

    public byte[] getData() {
        return data;
    }
}
