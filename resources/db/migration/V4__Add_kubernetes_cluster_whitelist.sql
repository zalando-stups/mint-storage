
SET search_path TO zm_data;

ALTER TABLE application ADD COLUMN a_kubernetes_clusters TEXT;

COMMENT ON COLUMN application.a_kubernetes_clusters IS 'comma-separated list';
