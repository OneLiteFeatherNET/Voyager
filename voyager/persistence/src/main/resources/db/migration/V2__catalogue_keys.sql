-- Catalogue keys (ADR-0040). A map or cup is named by its key, elytrarace:<value>, in the statistics. The rows written
-- before the key existed hold the flat id; each one is rewritten to its key. The layout version is hashed from the
-- geometry and does not change. A value that is not in the mapping is a map or cup no longer in the catalogue and is
-- left as it is. Flyway runs this file in one transaction.

UPDATE voyager.cup_session
   SET cup_key = CASE cup_key
                     WHEN 'alpha_cup' THEN 'elytrarace:alpha_cup'
                     WHEN 'legacy_cup' THEN 'elytrarace:legacy_cup'
                     ELSE cup_key
                 END
 WHERE cup_key IN ('alpha_cup', 'legacy_cup');

UPDATE voyager.race_session
   SET map_key = CASE map_key
                     WHEN 'elytraraceblueandred' THEN 'elytrarace:blue_and_red'
                     WHEN 'palmbeach' THEN 'elytrarace:palm_beach'
                     WHEN 'dragonlobby' THEN 'elytrarace:dragon_lobby'
                     WHEN 'memoria' THEN 'elytrarace:memoria'
                     ELSE map_key
                 END
 WHERE map_key IN ('elytraraceblueandred', 'palmbeach', 'dragonlobby', 'memoria');

UPDATE voyager.map_personal_best
   SET map_key = CASE map_key
                     WHEN 'elytraraceblueandred' THEN 'elytrarace:blue_and_red'
                     WHEN 'palmbeach' THEN 'elytrarace:palm_beach'
                     WHEN 'dragonlobby' THEN 'elytrarace:dragon_lobby'
                     WHEN 'memoria' THEN 'elytrarace:memoria'
                     ELSE map_key
                 END
 WHERE map_key IN ('elytraraceblueandred', 'palmbeach', 'dragonlobby', 'memoria');
