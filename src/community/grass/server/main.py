import cherrypy
from backend import Grass

class GrassServer(object):

  def __init__(self):
    self.g = Grass()

  @cherrypy.expose
  @cherrypy.tools.json_out()
  def index(self):
    return {'status':'ok'}

  @cherrypy.expose
  @cherrypy.tools.json_out()
  def config(self):
    return self.g.cfg

  @cherrypy.expose
  @cherrypy.tools.json_out()
  def list(self):
    return self.g.list_modules()

  @cherrypy.expose
  @cherrypy.tools.json_out()
  def info(self, name):
    return self.g.describe_module(name)

  @cherrypy.expose
  @cherrypy.tools.json_in()
  @cherrypy.tools.json_out()
  def run(self, name):
    return self.g.run(name, cherrypy.request.json)

cherrypy.config.update({
  'server.socket_port': 8000
});
cherrypy.quickstart(GrassServer())