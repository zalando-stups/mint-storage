ALTER TABLE zm_data.application
 ADD COLUMN a_message TEXT DEFAULT '';

UPDATE zm_data.application
   SET a_message = ''
 WHERE a_has_problems = false;

UPDATE zm_data.application
   SET a_message = 'Unknown error, will be updated.'
 WHERE a_has_problems = true;

 ALTER TABLE zm_data.application
ALTER COLUMN a_message SET NOT NULL;