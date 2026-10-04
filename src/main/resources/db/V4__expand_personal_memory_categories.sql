-- Topics share the existing memory table. scope selects the memory area;
-- category describes the kind of information. No personal facts are seeded.
-- Existing category descriptions and saved memories are preserved.
INSERT INTO memory_category (name, description)
VALUES
    ('personal', 'Personal life, identity, preferences, routines, and goals'),
    ('family', 'Children, relatives, family events, and schedules'),
    ('custody', 'Parenting schedules, exchanges, custody arrangements, and events'),
    ('child_support', 'Child support orders, payment records, amounts, and dates'),
    ('ashley', 'User-provided context, conversations, and events concerning Ashley'),
    ('relationships', 'Relationship context, conversations, and events'),
    ('legal', 'Court documents, hearings, deadlines, and user-provided legal context'),
    ('work', 'Career, employment, responsibilities, and professional goals'),
    ('fidelity', 'Fidelity work context and high-level project notes'),
    ('software_engineering', 'Programming, systems, architecture, and technical learning'),
    ('education', 'Courses, books, study notes, and learning goals'),
    ('home', 'Housing, household tasks, maintenance, and everyday administration'),
    ('travel', 'Trips, destinations, reservations, and travel plans'),
    ('vehicles', 'Vehicle ownership, repairs, and maintenance'),
    ('finance', 'Personal budgets, bills, financial records, and reminders'),
    ('health', 'User-provided health context, appointments, and routines'),
    ('apple', 'Apple devices, software, purchases, and settings'),
    ('creative', 'Writing, films, creative projects, and ideas'),
    ('general', 'Other durable context that does not fit a more specific category')
ON CONFLICT (name) DO NOTHING;
