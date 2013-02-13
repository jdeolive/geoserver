# MBTiles Module

*TODO*: Provide some examples

This module adds support for generating tiles in [MBTiles](http://mapbox.com/developers/mbtiles/).

It operates as a WMS GetMap output format with the name "mbtiles". To create a
MBTiles package simply invoke a GetMap request with the appropriate `format`
paramter: 

    /geoserver/wms?service=WMS&request=GetMap&version=1.1.0
                  &layers=states&styles=
                  &width=780&height=330&srs=EPSG:4326
                  &bbox=-124.731,24.956,-66.97,49.372s
                  &format=mbtiles

The result of which is a MBTiles SQLite database inside of zip archive. 

## Tile Generation

The output format works by taking the original WMS request and cutting it up 
into tiles at specific zoom levels. The tiles conform to an internally 
configured grid set known to GeoServers internal GeoWebCache tiling subsystem. 
By default the output format will pick a grid set and set of zoom levels based 
on the request but this is configurable through format options. 

The `gridset` option specifies a named grid set as configured by GeoWebCache. 
If not specified the gridset is chown from the projection/srs of the request.
The first gridset found maching the projection is chosen.

The `min_zoom` option specifies the starting zoom level at which to generate
tiles. If not specified a default is chosen based on the bounding box and 
height/width of the request. The zoom level that best matches the resolution 
of the bbox, width, and height is chosen.

The `max_zoom` option specifies the ending zoom level at which to generate
tiles. If not specified a default is chosen by walking down from the starting
zoom level, counting the number of tiles to be generated, and stopping once a 
threshold value is reached. 

An alternative to specifying a max zoom is to supply the `num_zooms` parameter
that indicates the number of zoom levels relative to the starting zoom. The 
resulting calculation of the ending zoom level is a simple one:

    max_zoom = min_zoom + num_zooms

## Tile Caching

The output format utilizes GeoServers internal tile caching capabilities as 
best it can so that when a suitable cached tiled exists it is used directly. 
In order for this to occur GeoWebCache must be properly configured. More
specificlly:

- Direct WMS integration must be enabled
- Caching for all relevant layers must be enabled
- Appropriate grid sets must be configured

When a tile does not exist one is generated on the fly as is the case with
a regular tiled WMS request. 

## Tile Image Format

The MBTiles spec allows for tiles to stored in either PNG or JPEG format. 
The ``format`` option is used to specify explicitly the format to use. By
default it is chosen based on the layers of the request.  If the request 
consists of a single raster layer then JPEG is chosen. Otherwise PNG is 
used. 

