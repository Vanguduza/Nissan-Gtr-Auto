-- PartSouq EPC diagram assets are GIF. Initial bucket seed omitted image/gif and used
-- ON CONFLICT DO NOTHING, so existing local/prod buckets need an explicit UPDATE.

UPDATE storage.buckets
SET allowed_mime_types = ARRAY['image/png', 'image/jpeg', 'image/webp', 'image/gif']
WHERE id = 'catalog-diagrams';
