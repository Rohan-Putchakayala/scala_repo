# --- !Ups

INSERT INTO rooms (name, capacity, location, amenities, is_active)
VALUES
  ('Orion', 8, 'Floor 5 - West', 'TV,Whiteboard,Conference Phone', 1),
  ('Nova', 12, 'Floor 7 - East', 'Projector,HDMI,Whiteboard', 1),
  ('Atlas', 6, 'Floor 3 - North', 'TV,HDMI', 1);

# --- !Downs

DELETE FROM rooms WHERE name IN ('Orion','Nova','Atlas');