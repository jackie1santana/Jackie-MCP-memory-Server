INSERT INTO memory_category (name, description)
VALUES
    ('personal', 'Personal identity and preferences'),
    ('family', 'Family related memory'),
    ('custody', 'Custody and parenting related context'),
    ('relationships', 'Relationship preferences and dynamics'),
    ('fidelity', 'Fidelity work context, systems, and operational notes'),
    ('software_engineering', 'Software engineering context and preferences'),
    ('travel', 'Travel plans, routines, and constraints'),
    ('vehicles', 'Vehicle preferences, ownership, maintenance'),
    ('finance', 'Financial preferences and reminders'),
    ('health', 'Health routines and goals'),
    ('apple', 'Apple ecosystem usage and preferences'),
    ('creative', 'Creative projects and ideas')
ON CONFLICT (name) DO NOTHING;

INSERT INTO memory_settings (setting_key, setting_value)
VALUES ('active_scope::default', 'off')
ON CONFLICT (setting_key) DO NOTHING;

