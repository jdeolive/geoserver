import json, glob, os, sys, itertools, cherrypy, string, random, tempfile
from collections import OrderedDict as odict
from grass import script as grass
from grass.script import setup
from grass.pygrass.modules import Module

def map_param(p):
  d = odict()
  d['name'] = p.name
  d['description'] = p.description
  d['type'] = p.typedesc
  d['required'] = p.required
  d['default'] = p.default
  return d

def filter_param(p):
  return p.type.__name__ != 'do_nothing'

def run_cmd(cmd):
  import subprocess as sub
  p = sub.Popen(cmd, shell=True, stdout=sub.PIPE, stderr=sub.PIPE)
  out, err = p.communicate()

  if p.returncode != 0:
    raise cherrypt.HTTPError(
      message='Failed to run command {0}: {1}'.format(cmd, err))

class Grass(object):

  def __init__(self):
    # read the configuration
    cfg = json.loads(open('config.json').read())
    grass_cfg = cfg['grass'] if cfg.has_key('grass') else {}

    # grass exe
    if grass_cfg.has_key('exe'):
      self.grass_exe = grass_cfg['exe']
    else:
      self.grass_exe = 'grass70.bat' if sys.platform.startswith('win') else 'grass70'

    # grass dbase
    if grass_cfg.has_key('dbase'):
      self.gisdb = grass_cfg['dbase']
    elif os.environ.has_key('GISDBASE'):
      self.gisdb = os.environ['GISDBASE']
    else:
      raise Exception("""No GRASS data directory, specify grass.dbase in 
        config.json or set the GISDBASE environment variable.""")

    self.cfg = cfg
    self.grass_cfg = grass_cfg

  def list_modules(self):
    mod_root = self.cfg['grass']['modules']

    # gather up vector and raster modules
    mods = itertools.chain(
      *[glob.glob('{0}/{1}.*'.format(mod_root,t)) for t in ['v','r']])

    # strip off base path
    mods = list(map(lambda p: os.path.basename(p), mods))

    r = odict()
    r['count'] = len(mods)
    r['modules'] = map(lambda m: {
      'name': m,
      'type': 'raster' if m[0] == 'r' else 'vector'
    }, mods)
    return r

  def describe_module(self, name):
    #import pdb; pdb.set_trace()
    m = Module(name)
    info = odict()
    info['name'] = name
    info['description'] = m.description
    info['type'] = 'raster' if name[0] == 'r' else 'vector'

    # round up its inputs/outputs
    info['inputs'] = map(map_param, filter(filter_param, m.inputs.values()))
    info['outputs'] = map(map_param, filter(filter_param, m.outputs.values()))
    return info

  def run(self, name, inputs):
    mod = self.describe_module(name)
    rsp = {}
    if mod['type'] == 'raster':
      rsp['output'] = self.run_raster(name, inputs)
    else:
      raise Exception()

    return rsp

  def run_raster(self, name, inputs):
    raster_file = inputs.pop('input')
    if not os.path.exists(raster_file):
      raise cherrypy.HTTPError(400,
        'Raster file {0} does not exist'.format(raster_file))

    # set up a new location from the raster file
    loc = ''.join(
      random.choice(string.ascii_uppercase + string.letters) for _ in range(8))
    loc_path = os.path.join(self.gisdb, loc)

    run_cmd('{0} -c {1} -e {2}'.format(self.grass_exe, raster_file, loc_path))

    # initialize the grass session
    setup.init(self.gisdb, location=loc)

    # link in the raster file as a virtual grass layer
    grass.read_command('r.external', input=raster_file, output='in_raster')

    # run the process
    grass.read_command(name, input='in_raster', output='out_raster', **inputs)

    # write out the result
    result_file = os.path.join(tempfile.mkdtemp(), 'result.tif')
    grass.read_command('r.out.gdal', input='out_raster', output=result_file, format='GTiff')

    return result_file

