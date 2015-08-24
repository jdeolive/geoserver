# GRASS Python Server

This directory contains an HTTP server that provides a simple RESTful api
on top of the GRASS Python bindings.

## Requirements

- Python 2.7
- [Grass 7.0 Python Bindings]()

  The Python bindings come install with GRASS. However to use them outside of a 
  GRASS session you must configure the `PYTHONPATH` environment variable.

       % export PYTHONPATH="$GISBASE/etc/python"

  Where `GISBASE` is the top level directory of the GRASS install. To test out
  the bindings start a Python shell and attempt to import the `grass` module:

      % python
      Python 2.7.8 (default, Dec 16 2014, 14:18:44) 
      [GCC 4.2.1 Compatible Apple LLVM 6.0 (clang-600.0.56)] on darwin
      Type "help", "copyright", "credits" or "license" for more information.
      >>> import grass
      >>>

  If no error occur then the GRASS bindings are good to go.

- [CherryPy](http://www.cherrypy.org/)

  CherryPy is a lightweight framework that is great for building simple RESTful
  apis in Python. It can be installed via `easy_install` or `pip`.

      % pip install cherrypy

## Running

Once the above requirements have been met run the server by executing `main.py`:

    % python main.py

The server runs by default on port `8000`.

## Server API

The GRASS server provides the following api calls:

### GET /

Returns a simple response to test that the server is up. Example:

    % curl -G http://localhost:8000
    {
      "status": "ok"
    }

### GET /list

Lists all available grass modules. Example:

    % curl -G http://localhost:8000/list
    {
        "count": 238,
        "modules": [
            {
                "type": "vector",
                "name": "v.buffer"
            },
            {
                "type": "vector",
                "name": "v.build"
            },
            ...
        ]
    }

### GET /info/{module}

Gets info about a specific ``module``. Example:

    % curl -G http://localhost:8000/info/v.buffer
    {
        "name": "v.buffer", 
        "description": "Creates a buffer around vector features of given type.", 
        "type": "vector", 
        "inputs": [
            {
              "name": "input", 
              "description": "Or data source for direct OGR access", 
              "type": "vector", 
              "required": true, 
              "default": null
            }, 
            {
              "name": "layer", 
              "description": "A single vector map can be connected to multiple database tables. This number determines which table to use. When used with direct OGR access this is the layer name.", 
              "type": "layer", 
              "required": false, 
              "default": "-1"
            }, 
            ...
        ], 
        "outputs": [
            {
              "name": "output", 
              "description": "Name for output vector map", 
              "type": "vector", 
              "required": true, 
              "default": null
            }
         ]
    }

### POST /run/{module}

Executes a ``module`` sending the inputs as the body of the request. Example:

    % curl -XPOST \
           -H 'Content-Type: application/json' \
           -d '{"input": "dem.tiff", "coordinates": [599909.34,4923108.96]}' \
           http://localhost:8000/run/v.viewshed
    {
      "output": "/var/folders/r1/h8tb3rjn42bd0dnkhdq5fdf00000gn/T/tmpXTd764/result.tif"
    }


## Server Configuration

The server uses a JSON configuration file named ``config.json`` for configuration.
Example:

    {
        "grass": {
            "dbase": "/Users/jdeolive/Projects/geoserver/work/grass/data",
            "exe": "/Applications/GRASS-7.0.app/Contents/MacOS/grass70",
            "modules": "/Applications/GRASS-7.0.app/Contents/MacOS/bin"
          }
    }

All GRASS configuration lives under the top level property ``grass``.

| Option | Description |
| -------|-------------|
| exe    | Path to GRASS executable. |
| dbase  | GRASS data directory (GISDBASE). This directory is used to create new GRASS locations for processing |
| modules| Path to directory containing GRASS modules (like v.viewshed) |
