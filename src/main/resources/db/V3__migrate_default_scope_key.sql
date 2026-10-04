INSERT INTO memory_settings (setting_key, setting_value, updated_at)
SELECT 'active_scope::default', setting_value, updated_at
FROM memory_settings
WHERE setting_key = 'active_scope'
ON CONFLICT (setting_key) DO NOTHING;

