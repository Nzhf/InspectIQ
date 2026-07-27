-- V2__seed_defect_types.sql
-- Seed data for standard manufacturing defect categories

INSERT INTO defect_types (code, description) VALUES
('SOLDER_BRIDGE', 'Excess solder creating an unintended electrical connection between two pins'),
('MISALIGNMENT', 'Component is not properly aligned with the solder pads'),
('MISSING_COMPONENT', 'A required component is missing from the board'),
('TOMBSTONE', 'A surface mount component that has partially lifted off the pad during soldering'),
('SCRATCH', 'Physical scratch on the board surface or traces'),
('WRONG_POLARITY', 'Component is installed backwards or with incorrect polarity');
