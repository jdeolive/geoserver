CREATE TABLE IF NOT EXISTS tile_table_metadata (
  t_table_name TEXT NOT NULL PRIMARY KEY,
  is_times_two_zoom INTEGER NOT NULL DEFAULT 1
)
